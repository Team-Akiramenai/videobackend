package com.akiramenai.videobackend.model;

import lombok.Builder;

@Builder
public record CleanedComment(
    String commentId,
    String authorName,
    String authorProfilePicture,
    String content,
    String createdAt,
    String lastModifiedAt
) {
}
