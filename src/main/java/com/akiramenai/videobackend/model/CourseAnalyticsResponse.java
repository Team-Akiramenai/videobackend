package com.akiramenai.videobackend.model;

import lombok.Builder;

import java.util.List;

@Builder
public record CourseAnalyticsResponse(
    long daysCovered,
    String startDate,
    String endDate,
    List<CourseSellDatapoint> datapoints
) {
}
