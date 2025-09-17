package com.akiramenai.videobackend.model;

public record ModifyCodingTestRequest(
    String courseId,
    String itemId,
    String question,
    String description,
    String expectedStdout
) {
}
