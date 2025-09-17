package com.akiramenai.videobackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TranscriptionCommandService {
  @Value("${application.default-values.media.whisper-cpp-cli-path}")
  public String whisperCppCliPath;

  @Value("${application.default-values.media.whisper-cpp-model-path}")
  public String whisperCppModelPath;
}
