package com.akiramenai.videobackend.model;

import lombok.Builder;

@Builder
public record StripeRequest(
    String productName,
    String productDescription,
    double cost,

    String buyerId,
    PurchaseTypes itemType,
    String courseId,
    Integer storageAmountInGBs
) {
}
