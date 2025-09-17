package com.akiramenai.videobackend.model;

public record AddCommentRequest(
    String videoMetadataId,
    String content
) {
}
