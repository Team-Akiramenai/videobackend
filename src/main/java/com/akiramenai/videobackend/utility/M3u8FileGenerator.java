package com.akiramenai.videobackend.utility;

import java.util.List;

public class M3u8FileGenerator {
  private static final String MASTER_M3U8_BEGINNING = """
      #EXTM3U
      #EXT-X-VERSION:3
      
      #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,FORCED=NO,URI="sub.m3u8"
      
      """;

  private static final String streamOf1080p = """
      #EXT-X-STREAM-INF:BANDWIDTH=5350000,RESOLUTION=1920x1080,SUBTITLES="subs"
      v1080/prog.m3u8
      """;

  private static final String streamOf720p = """
      #EXT-X-STREAM-INF:BANDWIDTH=2628000,RESOLUTION=1280x720,SUBTITLES="subs"
      v720/prog.m3u8
      """;

  private static final String streamOf480p = """
      #EXT-X-STREAM-INF:BANDWIDTH=856000,RESOLUTION=854x480,SUBTITLES="subs"
      v480/prog.m3u8
      """;

  private static final String streamOf360p = """
      #EXT-X-STREAM-INF:BANDWIDTH=528000,RESOLUTION=640x360,SUBTITLES="subs"
      v360/prog.m3u8
      """;

  private static final String streamOf144p = """
      #EXT-X-STREAM-INF:BANDWIDTH=180000,RESOLUTION=256x144,SUBTITLES="subs"
      v144/prog.m3u8
      """;

  public static String getMasterM3u8FileContent(List<String> videoQualities) {
    StringBuilder content = new StringBuilder(MASTER_M3U8_BEGINNING);

    for (String videoQuality : videoQualities) {
      switch (videoQuality) {
        case "1080" -> {
          content.append(streamOf1080p);
        }
        case "720" -> {
          content.append(streamOf720p);
        }
        case "480" -> {
          content.append(streamOf480p);
        }
        case "360" -> {
          content.append(streamOf360p);
        }
        case "144" -> {
          content.append(streamOf144p);
        }
      }
    }

    return content.toString();
  }
}
