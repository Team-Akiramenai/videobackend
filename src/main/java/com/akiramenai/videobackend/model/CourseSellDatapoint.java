package com.akiramenai.videobackend.model;

import lombok.Builder;

@Builder
public record CourseSellDatapoint(
    String date,
    long coursesSold,
    double revenueGenerated
) {
}
