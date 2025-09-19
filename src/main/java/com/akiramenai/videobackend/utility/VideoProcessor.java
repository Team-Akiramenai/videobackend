package com.akiramenai.videobackend.utility;

import com.akiramenai.videobackend.config.VideoProcessingConfig;
import com.akiramenai.videobackend.filters.FingerprintService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import com.akiramenai.videobackend.model.*;
import com.akiramenai.videobackend.repo.UserRepo;
import com.akiramenai.videobackend.repo.VideoMetadataRepo;
import com.akiramenai.videobackend.service.MediaStorageService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class VideoProcessor {
  String whisperCppCliPath;
  String whisperCppModelPath;

  private final UserRepo userRepo;
  private final VideoMetadataRepo videoMetadataRepo;
  private final FingerprintService fingerprintService;
  private final MediaStorageService mediaStorageService;
  private final BlockingQueue<VideoProcessingTask> videoFileQueue;

  private final VideoProcessingConfig videoProcessingConfig;

  public VideoProcessor(
      UserRepo userRepo,
      VideoMetadataRepo videoMetadataRepo,
      MediaStorageService mediaStorageService,
      BlockingQueue<VideoProcessingTask> videoFileQueue,
      String whisperCppCliPath,
      String whisperCppModelPath,
      VideoProcessingConfig videoProcessingConfig,
      FingerprintService fingerprintService
  ) {
    this.userRepo = userRepo;
    this.videoMetadataRepo = videoMetadataRepo;
    this.mediaStorageService = mediaStorageService;
    this.videoFileQueue = videoFileQueue;

    this.whisperCppCliPath = whisperCppCliPath;
    this.whisperCppModelPath = whisperCppModelPath;

    this.videoProcessingConfig = videoProcessingConfig;
    this.fingerprintService = fingerprintService;
  }

  private boolean writeToFile(File fileToWriteTo, String content) {
    try (
        FileWriter fileWriter = new FileWriter(fileToWriteTo);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
    ) {
      bufferedWriter.write(content);
    } catch (Exception e) {
      log.error("Failed to write `subs.m3u8` file into the VideoIdDirectory. Reason: ", e);

      return false;
    }

    return true;
  }

  private String[] getVideoQualityDetails(String videoHeight) {
    switch (videoHeight) {
      case "1080" -> {
        return new String[]{"5000k", "5350k", "7500k"};
        //return "-b:v 5000k -maxrate 5350k -bufsize 7500k";
      }
      case "720" -> {
        return new String[]{"2500k", "2675k", "3750k"};
      }
      case "480" -> {
        return new String[]{"800k", "856k", "1200k"};
      }
      case "360" -> {
        return new String[]{"400k", "428k", "600k"};
      }
      default -> {
        return new String[]{"95k", "100k", "150k"};
      }
    }
  }

  private ResultOrError<File, VideoProcessingErrors> processVideo(
      File videoToProcess,
      UUID videoId,
      int width,
      int height,
      boolean useGpu
  ) {
    var res = ResultOrError.<File, VideoProcessingErrors>builder();

    // Create the video directory and the quality directory
    Path qualityDirPath = Paths.get(
        mediaStorageService.videoDirectoryString,
        videoId.toString(),
        "v" + height
    );

    try {
      Files.createDirectories(qualityDirPath);
    } catch (Exception e) {
      log.error("Error creating directory for video processing.", e);
      return res
          .errorType(VideoProcessingErrors.FailedToCreateOutputFile)
          .errorMessage("Failed to create the output file.")
          .build();
    }
    Path videoIdDirectory = Paths.get(
        mediaStorageService.videoDirectoryString,
        videoId.toString()
    );

    try {
      String encoderToUse = null, presetToUse = null;
      if (useGpu) {
        encoderToUse = "h264_nvenc";
        presetToUse = "p6";
      } else {
        encoderToUse = "libx264";
        presetToUse = "medium";
      }

      String[] properties = getVideoQualityDetails(String.valueOf(height));

      String[] command = {
          "ffmpeg",
          "-i", videoToProcess.getAbsolutePath(),
          "-c:v", encoderToUse,
          "-preset", presetToUse,
          "-profile:v", "main",
          "-b:v", properties[0],
          "-maxrate", properties[1],
          "-bufsize", properties[2],
          "-vf", "scale=-2:" + height,
          "-c:a", "aac",
          "-ac", "2",
          "-b:a", "128k",
          "-g", "48",
          "-force_key_frames", "expr:gte(t,n_forced*2)",
          "-fflags", "+genpts",
          "-start_at_zero",
          "-hls_time", "2",
          "-hls_list_size", "0",
          "-hls_segment_filename", MessageFormat.format("v{0}/seg_%03d.ts", height),
          MessageFormat.format("v{0}/prog.m3u8", height)
      };

      ProcessBuilder pb = new ProcessBuilder(
          command
      )
          .directory(videoIdDirectory.toFile())
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.INHERIT);
      int exitCode = pb.start().waitFor();

      if (exitCode != 0) {
        log.error("Failed to process video. FFMPEG exited with exit code: {}", exitCode);

        return res
            .errorType(VideoProcessingErrors.FailedToProcess)
            .errorMessage("Failed to process video.")
            .build();
      }
    } catch (Exception e) {
      log.error("Failed to run the command to process the video. Reason: ", e);

      return res
          .errorType(VideoProcessingErrors.FailedToProcess)
          .errorMessage("Failed to process video.")
          .build();
    }

    log.info("Video processing completed successfully. Video path: {}", qualityDirPath);
    return res
        .result(qualityDirPath.toFile())
        .build();
  }

  private ResultOrError<File, VideoProcessingErrors> extractVideoInfo(
      VideoProcessingTask task,
      boolean shouldTranscribe
  ) {
    var res = ResultOrError.<File, VideoProcessingErrors>builder();

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
            .errorType(VideoProcessingErrors.FailedToProcess)
            .errorMessage("Failed to extract 16-bit WAV from the video.")
            .build();
      }

      File tempAudioFileHandle = Paths.get(mediaStorageService.videoDirectoryString, tempAudioFile).toFile();

      ResultOrError<AudioFingerprint, VideoProcessingErrors> fingerprintResult = extractFingerprint(tempAudioFileHandle);
      if (fingerprintResult.errorType() != null) {
        log.error("Failed to extract the audio fingerprint. Reason: {} -> {}", fingerprintResult.errorType(), fingerprintResult.errorMessage());

        return res
            .errorMessage("Failed to extract the audio fingerprint.")
            .errorType(VideoProcessingErrors.FailedToProcess)
            .build();
      }
      Fingerprint fingerprint = Fingerprint
          .builder()
          .videoMetadataId("VM_" + videoId)
          .authorId(task.uploader())
          .audioDuration(fingerprintResult.result().duration())
          .audioFingerprint(fingerprintResult.result().fingerprint())
          .build();
      FingerprintService.FingerprintMatchStatus fprintStatus = this.fingerprintService.examineAndSaveFingerprint(fingerprint);
      if (fprintStatus.equals(FingerprintService.FingerprintMatchStatus.ACCIDENTAL_REUPLOAD)) {
        log.warn("It seems instructor has accidentally reuploaded an already uploaded video.");
      }

      Optional<File> vttFile = mediaStorageService.getNewFile(videoId + "_vtt", MediaStorageService.FileType.VTT);
      if (vttFile.isEmpty()) {
        return res
            .errorType(VideoProcessingErrors.FailedToProcess)
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
              .errorType(VideoProcessingErrors.FailedToProcess)
              .errorMessage("Failed to extract 16-bit WAV from the video.")
              .build();
        }

        Optional<File> transcriptionFile = mediaStorageService.getNewFile(videoId + "_temp", MediaStorageService.FileType.VTT);
        if (transcriptionFile.isEmpty()) {
          return res
              .errorType(VideoProcessingErrors.FailedToProcess)
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
      } else {
        try {
          Path testVttFilePath = Paths.get(videoProcessingConfig.getTestVttFile());
          Files.copy(testVttFilePath, vttFile.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          log.error("Failed to extract test VTT file from video. Reason: ", e);

          return res
              .errorType(VideoProcessingErrors.FailedToProcess)
              .errorMessage("Failed to copy the test VTT file into the output VTT file.")
              .build();
        }
      }

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
          .errorType(VideoProcessingErrors.FailedToProcess)
          .errorMessage("Failed to extract VTT file and fingerprint from the video.")
          .build();
    }
  }

  private ResultOrError<String, VideoProcessingErrors> generateM3u8Files(
      Path videoIdDir,
      File vttFile
  ) {
    var res = ResultOrError.<String, VideoProcessingErrors>builder();
    // get the VTT file in here
    try {
      Files.copy(vttFile.toPath(), videoIdDir.resolve("subtitle.vtt"));
    } catch (Exception e) {
      log.error("Failed to copy VTT file into the VideoIdDirectory. Reason: ", e);

      return res
          .errorType(VideoProcessingErrors.FailedToProcess)
          .errorMessage("Failed to copy VTT file into the VideoIdDirectory.")
          .build();
    }
    // generate the m3u8 file for the VTT file
    Path m3u8ForVtt = videoIdDir.resolve("sub.m3u8");
    String content =
        """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:9999
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:9999.0,
            subtitle.vtt
            #EXT-X-ENDLIST
            """;
    boolean isWriteSuccessful = writeToFile(m3u8ForVtt.toFile(), content);
    if (!isWriteSuccessful) {
      log.error("Failed to write `sub.m3u8` file into the VideoIdDirectory.");

      return res
          .errorType(VideoProcessingErrors.FailedToProcess)
          .errorMessage("Failed to write `sub.m3u8` file into the VideoIdDirectory.")
          .build();
    }

    // generate the master VTT file according to the qualities we have

    ArrayList<String> videoQualities = new ArrayList<>();
    for (int[] dimensions : this.videoProcessingConfig.getVideoDimensions()) {
      videoQualities.add(String.valueOf(dimensions[1]));
    }
    content = M3u8FileGenerator.getMasterM3u8FileContent(videoQualities);

    Path pathToMasterM3u8File = videoIdDir.resolve("master.m3u8");
    isWriteSuccessful = writeToFile(pathToMasterM3u8File.toFile(), content);
    if (!isWriteSuccessful) {
      log.error("Failed to write `master.m3u8` file into the VideoIdDirectory.");

      return res
          .errorType(VideoProcessingErrors.FailedToProcess)
          .errorMessage("Failed to write `sub.m3u8` file into the VideoIdDirectory.")
          .build();
    }

    return res
        .result("")
        .build();
  }

  private ResultOrError<AudioFingerprint, VideoProcessingErrors> extractFingerprint(
      File targetAudioFile
  ) {
    var res = ResultOrError.<AudioFingerprint, VideoProcessingErrors>builder();

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
            .errorType(VideoProcessingErrors.FailedToProcess)
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
          .errorType(VideoProcessingErrors.FailedToProcess)
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

          ResultOrError<File, VideoProcessingErrors> processedResult = null;
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
          }
          if (processedResult.errorType() != null) {
            log.error("Failed to process the video. Reason: {} -> {}", processedResult.errorType(), processedResult.errorMessage());
            continue;
          }

          ResultOrError<File, VideoProcessingErrors> transcriptionResult = extractVideoInfo(task, videoProcessingConfig.useGpu());
          if (transcriptionResult.errorType() != null) {
            log.error("Failed to process the video. Reason: {} -> {}", transcriptionResult.errorType(), transcriptionResult.errorMessage());
            continue;
          }

          Path videoIdDirectory = Paths.get(
              mediaStorageService.videoDirectoryString,
              task.videoId().toString()
          );
          ResultOrError<String, VideoProcessingErrors> result = generateM3u8Files(
              videoIdDirectory,
              transcriptionResult.result()
          );
          if (result.errorType() != null) {
            log.error("Failed to generate m3u8 files. Reason: {} -> {}", result.errorType(), result.errorMessage());
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

          long bytesUsed = FileUtils.sizeOf(videoIdDirectory.toFile());
          targetUser.get().setUsedStorageInBytes(
              targetUser.get().getUsedStorageInBytes() + bytesUsed
          );
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
