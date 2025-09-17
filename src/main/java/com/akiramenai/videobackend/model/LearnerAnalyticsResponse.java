package com.akiramenai.videobackend.model;

import lombok.Builder;

import java.util.List;

@Builder
public record LearnerAnalyticsResponse(
    int loginStreak,
    List<Integer> activityThisMonth
) {
}
