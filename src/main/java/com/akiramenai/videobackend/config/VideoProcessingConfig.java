package com.akiramenai.videobackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "application.default-values")
public class VideoProcessingConfig {
  @Setter
  private boolean useGpu;

  @Setter
  @Getter
  private String testVttFile;

  @Setter
  @Getter
  private List<String> videoQualities;

  public boolean useGpu() {
    return useGpu;
  }

  public List<int[]> getVideoDimensions() {
    List<int[]> dimensions = new ArrayList<>();
    for (String videoQuality : videoQualities) {
      String videoHeightStr = videoQuality.substring(0, videoQuality.length() - 1);
      int videoHeight = Integer.parseInt(videoHeightStr);
      int videoWidth = (int) ((videoHeight / 9.0) * 16.0);

      // All videos are assumed to be in 16:9 aspect ratio
      dimensions.add(new int[]{videoWidth,videoHeight});
    }
    return dimensions;
  }
}
