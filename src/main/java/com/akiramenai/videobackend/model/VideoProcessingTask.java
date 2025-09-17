package com.akiramenai.videobackend.model;

import java.io.File;
import java.util.UUID;

public record VideoProcessingTask(File videoToProcess, UUID courseId, UUID videoId, UUID uploader) {
}
