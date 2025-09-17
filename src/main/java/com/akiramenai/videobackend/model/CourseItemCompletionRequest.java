package com.akiramenai.videobackend.model;

public record CourseItemCompletionRequest(
    String courseId,
    String itemId
) {
}
