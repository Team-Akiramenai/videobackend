package com.akiramenai.videobackend.filters;

import com.akiramenai.videobackend.model.Fingerprint;
import com.akiramenai.videobackend.model.Users;
import com.akiramenai.videobackend.repo.CourseRepo;
import com.akiramenai.videobackend.repo.FingerprintRepo;
import com.akiramenai.videobackend.repo.CourseIdInterface;
import com.akiramenai.videobackend.repo.UserRepo;
import com.akiramenai.videobackend.service.MeiliService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class FingerprintService {
  private final FingerprintRepo fingerprintRepo;
  private final UserRepo userRepo;
  private final CourseRepo courseRepo;
  private final MeiliService meiliService;

  /// `UNIQUE` -> Video whose fingerprint we don't have in the database. Considered original content.
  ///
  /// `ACCIDENTAL_REUPLOAD` -> Author accidentally uploaded his own video again.
  ///
  /// `STOLEN` -> Someone other that the author uploaded an already uploaded video. This is considered content theft.
  public enum FingerprintMatchStatus {
    UNIQUE,
    STOLEN,
    ACCIDENTAL_REUPLOAD
  }

  public FingerprintService(
      FingerprintRepo fingerprintRepo,
      UserRepo userRepo, CourseRepo courseRepo, MeiliService meiliService) {
    this.fingerprintRepo = fingerprintRepo;
    this.userRepo = userRepo;
    this.courseRepo = courseRepo;
    this.meiliService = meiliService;
  }

  private FingerprintMatchStatus findMatchingFingerprint(String providedFingerprint, UUID currentUserId) {
    Optional<Fingerprint> retrievedFingerprint = fingerprintRepo.findFingerprintByAudioFingerprint(providedFingerprint);
    if (retrievedFingerprint.isEmpty()) {
      return FingerprintMatchStatus.UNIQUE;
    }

    if (retrievedFingerprint.get().getAuthorId().equals(currentUserId)) {
      return FingerprintMatchStatus.ACCIDENTAL_REUPLOAD;
    }

    // In the case of content theft, shadow-ban the content thief
    Optional<Users> thief = userRepo.findUsersById(currentUserId);
    if (thief.isEmpty()) {
      log.warn("Failed to retrieve the content thief's account. UserId = {}", currentUserId);
      return FingerprintMatchStatus.STOLEN;
    }

    thief.get().setShadowBanned(true);
    userRepo.save(thief.get());
    courseRepo.hideCoursesByUserId(thief.get().getId());
    List<CourseIdInterface> stolenCourseIds = courseRepo.findAllByInstructorIdAndIsHidden(currentUserId, true);
    meiliService.deleteCourseInDocument(stolenCourseIds);

    return FingerprintMatchStatus.STOLEN;
  }

  public FingerprintMatchStatus examineAndSaveFingerprint(Fingerprint fingerprint) {
    FingerprintMatchStatus fprintStatus = findMatchingFingerprint(
        fingerprint.getAudioFingerprint(),
        fingerprint.getAuthorId()
    );
    if (fprintStatus != FingerprintMatchStatus.UNIQUE) {
      return fprintStatus;
    }

    fingerprintRepo.save(fingerprint);
    return FingerprintMatchStatus.UNIQUE;
  }
}
