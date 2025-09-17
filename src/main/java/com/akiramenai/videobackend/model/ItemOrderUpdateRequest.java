package com.akiramenai.videobackend.model;

import java.util.List;

public record ItemOrderUpdateRequest(String courseId, List<String> orderOfItemIds) {
}
