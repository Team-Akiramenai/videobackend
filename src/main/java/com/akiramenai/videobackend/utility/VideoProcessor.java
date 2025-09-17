package com.akiramenai.videobackend.utility;

import com.akiramenai.videobackend.config.VideoProcessingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import com.akiramenai.videobackend.model.*;
import com.akiramenai.videobackend.repo.FingerprintRepo;
import com.akiramenai.videobackend.repo.UserRepo;
import com.akiramenai.videobackend.repo.VideoMetadataRepo;
import com.akiramenai.videobackend.service.MediaStorageService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class VideoProcessor {
  public enum ProcessingError {
    FailedToProcess,
    FailedToCreateOutputFile,
  }

  String whisperCppCliPath;
  String whisperCppModelPath;

  private final UserRepo userRepo;
  private final VideoMetadataRepo videoMetadataRepo;
  private final FingerprintRepo fingerprintRepo;
  private final MediaStorageService mediaStorageService;
  private final BlockingQueue<VideoProcessingTask> videoFileQueue;

  private final VideoProcessingConfig videoProcessingConfig;

  public VideoProcessor(
      UserRepo userRepo,
      VideoMetadataRepo videoMetadataRepo,
      FingerprintRepo fingerprintRepo,
      MediaStorageService mediaStorageService,
      BlockingQueue<VideoProcessingTask> videoFileQueue,
      String whisperCppCliPath,
      String whisperCppModelPath,
      VideoProcessingConfig videoProcessingConfig
  ) {
    this.userRepo = userRepo;
    this.videoMetadataRepo = videoMetadataRepo;
    this.fingerprintRepo = fingerprintRepo;
    this.mediaStorageService = mediaStorageService;
    this.videoFileQueue = videoFileQueue;

    this.whisperCppCliPath = whisperCppCliPath;
    this.whisperCppModelPath = whisperCppModelPath;

    this.videoProcessingConfig = videoProcessingConfig;
  }

  private ResultOrError<File, ProcessingError> processVideo(
      File videoToProcess,
      UUID videoId,
      int width,
      int height,
      boolean useGpu
  ) {
    var res = ResultOrError.<File, ProcessingError>builder();

    Optional<File> encodedVideoFile = mediaStorageService.getNewFile(
        String.format("%s-%dp", videoId.toString(), height),
        MediaStorageService.FileType.MP4
    );
    if (encodedVideoFile.isEmpty()) {
      return res
          .errorType(ProcessingError.FailedToCreateOutputFile)
          .errorMessage("Failed to create the output file.")
          .build();
    }

    try {
      ProcessBuilder pb;
      if (useGpu) {
        pb = new ProcessBuilder(
            "ffmpeg",
            "-y", // NOTE: Remove after dev
            "-i", videoToProcess.getAbsolutePath(),
            "-vf", "scale=" + width + ":" + height,
            "-c:a", "copy",
            "-c:v", "h264_nvenc",
            "-preset", "medium",
            encodedVideoFile.get().getAbsolutePath()
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT);
      } else {
        pb = new ProcessBuilder(
            "ffmpeg",
            "-y", // NOTE: Remove after dev
            "-i", videoToProcess.getAbsolutePath(),
            "-vf", "scale=" + width + ":" + height,
            "-c:a", "copy",
            "-c:v", "libx264",
            "-preset", "fast",
            encodedVideoFile.get().getAbsolutePath()
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT);
      }
      int exitCode = pb.start().waitFor();

      if (exitCode != 0) {
        log.error("Failed to process video. FFMPEG exited with exit code: {}", exitCode);

        return res
            .errorType(ProcessingError.FailedToProcess)
            .errorMessage("Failed to process video.")
            .build();
      }
    } catch (Exception e) {
      log.error("Failed to run the command to process the video. Reason: ", e);

      return res
          .errorType(ProcessingError.FailedToProcess)
          .errorMessage("Failed to process video.")
          .build();
    }

    log.info("Video processing completed successfully. Video path: {}", encodedVideoFile.get().getAbsolutePath());
    return res
        .result(encodedVideoFile.get())
        .build();
  }

  private ResultOrError<File, ProcessingError> extractTranscription(
      VideoProcessingTask task,
      boolean shouldTranscribe
  ) {
    var res = ResultOrError.<File, ProcessingError>builder();

    File videoToProcess = task.videoToProcess();
    UUID videoId = task.videoId();

    try {
      String tempAudioFile = videoId.toString() + "_pcm16.wav";
      ProcessBuilder pb1 = new ProcessBuilder(
          "ffmpeg",
          "-i", videoToProcess.getAbsolutePath(),
          "-acodec", "pcm_s16le",
          tempAudioFile
      )
          .directory(new File(mediaStorageService.videoDirectoryString))
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.INHERIT);
      Process process1 = pb1.start();
      int exitCode = process1.waitFor();
      if (exitCode != 0) {
        log.error("Failed to extract audio from video. FFMPEG exited with exit code: {}", exitCode);

        return res
            .errorType(ProcessingError.FailedToProcess)
            .errorMessage("Failed to extract 16-bit WAV from the video.")
            .build();
      }

      Optional<File> vttFile = mediaStorageService.getNewFile(videoId + "_vtt", MediaStorageService.FileType.VTT);
      if (vttFile.isEmpty()) {
        return res
            .errorType(ProcessingError.FailedToProcess)
            .errorMessage("Failed to create the output VTT file.")
            .build();
      }
      if (shouldTranscribe) {
        ProcessBuilder pb2 = new ProcessBuilder(
            whisperCppCliPath,
            "-m",
            whisperCppModelPath,
            "-f", Paths.get(mediaStorageService.videoDirectoryString, tempAudioFile).toString()
        );

        Process process2 = pb2.start();

        // Read and store stdout
        BufferedReader reader = new BufferedReader(new InputStreamReader(process2.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
        reader.close();

        exitCode = process2.waitFor();
        if (exitCode != 0) {
          log.error("Failed to process video. Whisper-CLI exited with exit code: {}", exitCode);

          return res
              .errorType(ProcessingError.FailedToProcess)
              .errorMessage("Failed to extract 16-bit WAV from the video.")
              .build();
        }

        Optional<File> transcriptionFile = mediaStorageService.getNewFile(videoId + "_temp", MediaStorageService.FileType.VTT);
        if (transcriptionFile.isEmpty()) {
          return res
              .errorType(ProcessingError.FailedToProcess)
              .errorMessage("Failed to create temporary transcription output file.")
              .build();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(transcriptionFile.get()));
        writer.write(output.toString());
        writer.close();

        // convert the transcription output into VTT file
        VttHelper.convert(
            transcriptionFile.get().getAbsolutePath(),
            vttFile.get().getAbsolutePath()
        );
        if (!transcriptionFile.get().delete()) {
          log.warn("Failed to delete temporary transcription file: {}", transcriptionFile.get().getAbsolutePath());
        }
      }
      else {
        try {
          Path testVttFilePath = Paths.get(videoProcessingConfig.getTestVttFile());
          Files.copy( testVttFilePath,vttFile.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          log.error("Failed to extract test VTT file from video. Reason: ", e);

          return res
              .errorType(ProcessingError.FailedToProcess)
              .errorMessage("Failed to copy the test VTT file into the output VTT file.")
              .build();
        }
      }

      File tempAudioFileHandle = Paths.get(mediaStorageService.videoDirectoryString, tempAudioFile).toFile();

      ResultOrError<AudioFingerprint, ProcessingError> fingerprintResult = extractFingerprint(tempAudioFileHandle);
      if (fingerprintResult.errorType() != null) {
        log.error("Failed to extract the audio fingerprint. Reason: {} -> {}", fingerprintResult.errorType(), fingerprintResult.errorMessage());

        return res
            .errorMessage("Failed to extract the audio fingerprint.")
            .errorType(ProcessingError.FailedToProcess)
            .build();
      }
      Fingerprint fingerprint = Fingerprint
          .builder()
          .videoMetadataId("VM_" + videoId)
          .authorId(task.uploader())
          .audioDuration(fingerprintResult.result().duration())
          .audioFingerprint(fingerprintResult.result().fingerprint())
          .build();
      this.fingerprintRepo.save(fingerprint);

      // Remove the temporarily created audio file used for transcription
      if (!tempAudioFileHandle.delete()) {
        log.error("Failed to delete temporary WAV file: {}", tempAudioFileHandle.getAbsolutePath());
      }

      return res
          .result(vttFile.get())
          .build();
    } catch (Exception e) {
      log.error("Failed to extract VTT file and fingerprint from the video. Reason: ", e);

      return res
          .errorType(ProcessingError.FailedToProcess)
          .errorMessage("Failed to extract VTT file and fingerprint from the video.")
          .build();
    }
  }

  private ResultOrError<AudioFingerprint, ProcessingError> extractFingerprint(
      File targetAudioFile
  ) {
    var res = ResultOrError.<AudioFingerprint, ProcessingError>builder();

    try {
      ProcessBuilder fpcalcCommand = new ProcessBuilder(
          "fpcalc",
          targetAudioFile.getAbsolutePath()
      );

      Process commandProcess = fpcalcCommand.start();

      // Read and store stdout
      BufferedReader reader = new BufferedReader(new InputStreamReader(commandProcess.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
      reader.close();


      int exitCode = commandProcess.waitFor();
      if (exitCode != 0) {
        log.error(
            "Failed to extract audio fingerprint from the audio. Exit code: {}. Audio file path: {}",
            exitCode,
            targetAudioFile.getAbsolutePath()
        );

        return res
            .errorType(ProcessingError.FailedToProcess)
            .errorMessage("Failed to extract audio fingerprint.")
            .build();
      }

      // Parse the output from fpcalc
      String[] lines = output.toString().lines().toArray(String[]::new);
      String duration = lines[0].split("=")[1];
      String fingerprint = lines[1].substring(12);

      return res
          .result(new AudioFingerprint(duration, fingerprint))
          .build();
    } catch (Exception e) {
      log.error("Failed to run the command to extract the audio fingerprint. Reason: ", e);

      return res
          .errorType(ProcessingError.FailedToProcess)
          .errorMessage("Failed to extract audio fingerprint.")
          .build();
    }
  }

  public void start() {
    new Thread(() -> {
      log.info("Video processing thread has started.");

      while (!Thread.currentThread().isInterrupted()) {
        try {
          VideoProcessingTask task = videoFileQueue.take();

          ArrayList<File> encodedVideoFiles = new ArrayList<>();
          ResultOrError<File, ProcessingError> processedResult = null;
          var videoDimensions = videoProcessingConfig.getVideoDimensions();
          for (int[] dimension : videoDimensions) {
            processedResult = processVideo(
                task.videoToProcess(),
                task.videoId(),
                dimension[0],
                dimension[1],
                videoProcessingConfig.useGpu()
            );
            if (processedResult.errorType() != null) {
              break;
            }
            encodedVideoFiles.add(processedResult.result());
          }
          if (processedResult.errorType() != null) {
            log.error("Failed to process the video. Reason: {} -> {}", processedResult.errorType(), processedResult.errorMessage());
            continue;
          }

          ResultOrError<File, ProcessingError> transcriptionResult = extractTranscription(task, videoProcessingConfig.useGpu());
          if (transcriptionResult.errorType() != null) {
            log.error("Failed to process the video. Reason: {} -> {}", transcriptionResult.errorType(), transcriptionResult.errorMessage());
            continue;
          }

          if (!task.videoToProcess().delete()) {
            log.warn("Failed to delete the temporarily uploaded video file.");
          }

          Optional<VideoMetadata> targetMetadata = videoMetadataRepo.findVideoMetadataByVideoFileId(task.videoId());
          if (targetMetadata.isEmpty()) {
            log.warn("Failed to find video metadata for video file id: {}", task.videoId());
            continue;
          }
          targetMetadata.get().setProcessing(false);
          targetMetadata.get().setSubtitleFileName(transcriptionResult.result().getName());
          videoMetadataRepo.save(targetMetadata.get());

          Optional<Users> targetUser = userRepo.findUsersById(task.uploader());
          if (targetUser.isEmpty()) {
            log.error("Failed to update the uploaded user's profile storage details. User not found.");

            continue;
          }
          long usedStorageBytes = 0;
          for (File encodedFile : encodedVideoFiles) {
            usedStorageBytes += encodedFile.length();
          }
          targetUser.get().setUsedStorageInBytes(usedStorageBytes);
          userRepo.save(targetUser.get());

          log.info("Video processed successfully. VideoId: {}", task.videoId());
        } catch (IllegalArgumentException | OptimisticLockingFailureException e) {
          log.error("Failed to update video processing status. Reason: ", e);
        } catch (Exception e) {
          log.error("Failed to process video. Reason: ", e);
        }
      }
    }).start();
  }
}
