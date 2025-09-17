package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.Fingerprint;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FingerprintRepo extends JpaRepository<Fingerprint, UUID> {
  Optional<Fingerprint> findFingerprintByAudioFingerprint(String audioFingerprint);
}
