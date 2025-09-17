package com.akiramenai.videobackend.controller;

import com.akiramenai.videobackend.config.VideoProcessingConfig;
import com.akiramenai.videobackend.filters.FingerprintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.akiramenai.videobackend.model.*;
import com.akiramenai.videobackend.repo.CourseRepo;
import com.akiramenai.videobackend.repo.FingerprintRepo;
import com.akiramenai.videobackend.repo.UserRepo;
import com.akiramenai.videobackend.repo.VideoMetadataRepo;
import com.akiramenai.videobackend.service.MediaStorageService;
import com.akiramenai.videobackend.service.TranscriptionCommandService;
import com.akiramenai.videobackend.utility.HttpResponseWriter;
import com.akiramenai.videobackend.utility.JsonSerializer;
import com.akiramenai.videobackend.utility.VideoProcessor;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
@RestController
@RequestMapping("/api/private/video")
public class VideoConvertController {
  private final CourseRepo courseRepo;
  JsonSerializer jsonSerializer = new JsonSerializer();
  HttpResponseWriter httpResponseWriter = new HttpResponseWriter();

  VideoProcessingConfig videoProcessingConfig;

  TranscriptionCommandService transcriptionCommandService;

  UserRepo userRepo;
  VideoMetadataRepo videoMetadataRepo;
  MediaStorageService mediaStorageService;
  VideoProcessor videoProcessor;
  ArrayBlockingQueue<VideoProcessingTask> fileQueue;

  public VideoConvertController(
      UserRepo userRepo,
      VideoMetadataRepo videoMetadataRepo,
      MediaStorageService mediaStorageService,
      TranscriptionCommandService transcriptionCommandService,
      CourseRepo courseRepo,
      VideoProcessingConfig videoProcessingConfig,
      FingerprintService fingerprintService
  ) {
    this.userRepo = userRepo;
    this.videoMetadataRepo = videoMetadataRepo;
    this.mediaStorageService = mediaStorageService;

    this.transcriptionCommandService = transcriptionCommandService;

    this.fileQueue = new ArrayBlockingQueue<>(1024);
    this.videoProcessor = new VideoProcessor(
        this.userRepo,
        this.videoMetadataRepo,
        this.mediaStorageService,
        this.fileQueue,
        transcriptionCommandService.whisperCppCliPath,
        transcriptionCommandService.whisperCppModelPath,
        videoProcessingConfig,
        fingerprintService
    );

    this.videoProcessor.start();
    this.courseRepo = courseRepo;
    this.videoProcessingConfig = videoProcessingConfig;
  }

  @PostMapping("/upload")
  public void upload(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestParam("video") MultipartFile uploadedVideo,
      @RequestParam("course-id") String courseId
  ) {
    if (!request.getAttribute("accountType").equals("Instructor")) {
      httpResponseWriter.writeFailedResponse(response, "Only instructors can upload videos.", HttpStatus.BAD_REQUEST);
      return;
    }
    if (courseId == null) {
      httpResponseWriter.writeFailedResponse(response, "Course ID not provided.", HttpStatus.BAD_REQUEST);
      return;
    }
    if (uploadedVideo.isEmpty()) {
      httpResponseWriter.writeFailedResponse(response, "Uploaded video is empty.", HttpStatus.BAD_REQUEST);
      return;
    }

    UUID userId = UUID.fromString(request.getAttribute("userId").toString());
    Optional<Users> targetUser = userRepo.findUsersById(userId);
    if (targetUser.isEmpty()) {
      httpResponseWriter.writeFailedResponse(response, "User not found.", HttpStatus.BAD_REQUEST);
      return;
    }

    Optional<Course> targetCourse = courseRepo.findCourseById(UUID.fromString(courseId));
    if (targetCourse.isEmpty()) {
      httpResponseWriter.writeFailedResponse(response, "Course not found.", HttpStatus.BAD_REQUEST);
      return;
    }

    long videoSize = uploadedVideo.getSize();
    long storageLeft = targetUser.get().getTotalStorageInBytes() - targetUser.get().getUsedStorageInBytes();

    // We'll create 3 video files -> 1080p, 720p, 480p. That's why we multiply it by 3 to get the upperbound size.
    if ((videoSize * 3) > storageLeft) {
      httpResponseWriter.writeFailedResponse(response, "Failed to upload the video. You don't have enough free storage.", HttpStatus.INSUFFICIENT_STORAGE);
      return;
    }

    UUID videoId = UUID.randomUUID();
    Optional<String> result = processUploadedVideo(uploadedVideo, UUID.fromString(courseId), videoId, userId);
    if (result.isPresent()) {
      httpResponseWriter.writeFailedResponse(response, result.get(), HttpStatus.INTERNAL_SERVER_ERROR);
      return;
    }

    LocalDateTime ldtNow = LocalDateTime.now();
    VideoMetadata videoMetadataToAdd = VideoMetadata
        .builder()
        .itemId("VM_" + videoId)
        .courseId(UUID.fromString(courseId))
        .title("Placeholder Title")
        .description("Placeholder Description")
        .videoFileId(videoId)
        .isProcessing(true)
        .uploadDateTime(ldtNow)
        .lastModifiedDateTime(ldtNow)
        .build();

    try {
      videoMetadataRepo.save(videoMetadataToAdd);

      targetCourse.get().getCourseItemIds().add(videoMetadataToAdd.getItemId());
      courseRepo.save(targetCourse.get());
    } catch (Exception e) {
      log.error("Failed to save video metadata to DB. Reason: ", e);

      httpResponseWriter.writeFailedResponse(response, "Failed to save video metadata.", HttpStatus.INTERNAL_SERVER_ERROR);
      return;
    }

    ItemId itemIdResponse = new ItemId(videoMetadataToAdd.getItemId());
    Optional<String> respJson = jsonSerializer.serialize(itemIdResponse);
    if (respJson.isEmpty()) {
      httpResponseWriter.writeFailedResponse(response, "Failed to serialize JSON response.", HttpStatus.INTERNAL_SERVER_ERROR);
      return;
    }

    httpResponseWriter.writeOkResponse(response, respJson.get(), HttpStatus.CREATED);
  }

  private Optional<String> processUploadedVideo(
      MultipartFile uploadedFile,
      UUID courseId,
      UUID videoId,
      UUID uploader
  ) {
    if (uploadedFile.isEmpty()) {
      return Optional.of("Failed to process video. Uploaded file is empty.");
    }

    ResultOrError<String, FileUploadErrorTypes> savedPath = mediaStorageService.saveUploadedVideo(uploadedFile);
    if (savedPath.errorType() != null) {
      log.error("Failed to save uploaded video. Reason: {}", savedPath.errorType());

      return Optional.of("Failed to process video.");
    }

    try {
      File vidoeFile = new File(savedPath.result());
      uploadedFile.transferTo(vidoeFile);

      fileQueue.put(new VideoProcessingTask(vidoeFile, courseId, videoId, uploader));
    } catch (IllegalStateException | IOException e) {
      log.error("Failed to save the uploaded video in storage. Reason: ", e);

      return Optional.of("Failed to save video.");
    } catch (InterruptedException | NullPointerException e) {
      log.error("Failed to put the video in processing queue. Reason: ", e);

      return Optional.of("Failed to process video.");
    }

    return Optional.empty();
  }
}
