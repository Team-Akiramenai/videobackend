package com.akiramenai.videobackend.model;

public record ModifyCommentRequest(
    String commentId,
    String content
) {
}
