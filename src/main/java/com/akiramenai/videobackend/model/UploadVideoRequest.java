package com.akiramenai.videobackend.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Getter
@Setter
@Builder
public class UploadVideoRequest {
  private UUID courseId;
  private String title;
  private String description;
  private MultipartFile videoFile;
}
