package com.akiramenai.videobackend.model;

public record AddCodingTestRequest(
    String courseId,
    String question,
    String description,
    String expectedStdout
) {
}
