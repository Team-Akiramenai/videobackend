package com.akiramenai.videobackend.model;

public record RateCourseRequest(
    String courseId,
    int rating
) {
}
