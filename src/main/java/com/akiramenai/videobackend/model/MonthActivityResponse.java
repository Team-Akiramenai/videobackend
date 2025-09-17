package com.akiramenai.videobackend.model;

import java.util.List;

public record MonthActivityResponse(
    List<Integer> activityInMonth
) {
}
