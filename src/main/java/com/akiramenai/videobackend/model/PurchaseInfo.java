package com.akiramenai.videobackend.model;

public record PurchaseInfo(
    String userId,
    PurchaseTypes purchaseTypes,
    String courseId,
    Integer storageToAddInGBs
) {
}
