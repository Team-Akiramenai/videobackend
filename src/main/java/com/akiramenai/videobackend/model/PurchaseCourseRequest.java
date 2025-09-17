package com.akiramenai.videobackend.model;

public record PurchaseCourseRequest(
    String courseId,
    String transactionId
) {
}
