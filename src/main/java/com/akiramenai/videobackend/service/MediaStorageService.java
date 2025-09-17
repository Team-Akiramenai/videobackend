package com.akiramenai.videobackend.service;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.akiramenai.videobackend.model.FileUploadErrorTypes;
import com.akiramenai.videobackend.model.ResultOrError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class MediaStorageService {
  public enum FileType {
    PNG,
    MP4,
    VTT
  }

  @Value("${application.default-values.media.picture-directory}")
  public String pictureDirectoryString;

  @Value("${application.default-values.media.video-directory}")
  public String videoDirectoryString;

  @Value("${application.default-values.media.subtitles-directory}")
  public String transcriptionDirectoryString;

  public MediaStorageService() {
    log.info("Picture Dir: {}", pictureDirectoryString);
  }

  public ResultOrError<String, FileUploadErrorTypes> saveUploadedImage(MultipartFile file) {
    var resp = ResultOrError.<String, FileUploadErrorTypes>builder();
    if (file.isEmpty()) {
      return resp
          .errorMessage("Uploaded file is empty.")
          .errorType(FileUploadErrorTypes.FileIsEmpty)
          .result(null)
          .build();
    }

    try {
      Path uploadPath = Paths.get(this.pictureDirectoryString);
      if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
      }

      String fileName = getGeneratedFileName(file.getOriginalFilename());
      Path filePath = uploadPath.resolve(fileName);
      Files.copy(file.getInputStream(), filePath);

      return ResultOrError
          .<String, FileUploadErrorTypes>builder()
          .result(filePath.toString())
          .errorMessage(null)
          .errorType(null)
          .build();
    } catch (InvalidPathException e) {
      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.InvalidUploadDir)
          .result(null)
          .build();
    } catch (UnsupportedOperationException e) {
      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.FailedToCreateUploadDir)
          .result(null)
          .build();
    } catch (IOException e) {
      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.FailedToSaveFile)
          .result(null)
          .build();
    }
  }

  public ResultOrError<String, FileUploadErrorTypes> saveUploadedVideo(MultipartFile file) {
    var resp = ResultOrError.<String, FileUploadErrorTypes>builder();
    if (file.isEmpty()) {
      return resp
          .errorMessage("Uploaded file is empty.")
          .errorType(FileUploadErrorTypes.FileIsEmpty)
          .result(null)
          .build();
    }

    try {
      Path uploadPath = Paths.get(this.videoDirectoryString);
      if (!Files.exists(uploadPath)) {
        log.warn("Could not find video directory: {}", uploadPath);
        Files.createDirectories(uploadPath);
      }

      String fileName = getGeneratedFileName(file.getOriginalFilename());
      Path filePath = uploadPath.resolve(fileName);
      Files.copy(file.getInputStream(), filePath);

      return ResultOrError
          .<String, FileUploadErrorTypes>builder()
          .result(filePath.toString())
          .errorMessage(null)
          .errorType(null)
          .build();
    } catch (InvalidPathException e) {
      log.error("Invalid Path Exception: {}", e.getMessage());

      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.InvalidUploadDir)
          .result(null)
          .build();
    } catch (UnsupportedOperationException e) {
      log.error("Unsupported Op Exception: {}", e.getMessage());

      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.FailedToCreateUploadDir)
          .result(null)
          .build();
    } catch (IOException e) {
      log.error("IO Exception: {}", e.getMessage());

      return resp
          .errorMessage(e.getMessage())
          .errorType(FileUploadErrorTypes.FailedToSaveFile)
          .result(null)
          .build();
    }
  }

  private String getFileExtension(@NotNull String filename) {
    int dotIndex = filename.lastIndexOf(".");
    if (dotIndex >= 0) {
      return filename.substring(dotIndex + 1);
    }

    return "";
  }

  private String getGeneratedFileName(String filename) {
    return UUID.randomUUID().toString() + "." + getFileExtension(filename);
  }

  public Optional<File> getNewFile(String filename, FileType fileType) {
    try {
      Path uploadPath;
      String fileExtension;
      switch (fileType) {
        case PNG -> {
          uploadPath = Paths.get(this.pictureDirectoryString);
          fileExtension = ".png";
        }
        case MP4 -> {
          uploadPath = Paths.get(this.videoDirectoryString);
          fileExtension = ".mp4";
        }
        case VTT -> {
          // store the subtitles alongside the videos
          uploadPath = Paths.get(this.transcriptionDirectoryString);
          fileExtension = ".vtt";
        }
        case null, default -> {
          log.error("Invalid file type provided.");

          return Optional.empty();
        }
      }
      if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
      }

      Path filePath = uploadPath.resolve(filename + fileExtension);

      return Optional.of(filePath.toFile());
    } catch (Exception e) {
      log.error("Failed to save file. Reason: ", e);

      return Optional.empty();
    }
  }
}
