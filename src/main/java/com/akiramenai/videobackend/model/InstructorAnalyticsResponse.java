package com.akiramenai.videobackend.model;

import lombok.Builder;

import java.util.List;

@Builder
public record InstructorAnalyticsResponse(
    int loginStreak,
    List<Integer> loginActivity,
    double accountBalance,
    long totalCoursesSold
) {
}
