package com.akiramenai.videobackend.model;

import lombok.Builder;

@Builder
public record StripeResponse(
    String status,
    String msg,
    String sessionId,
    String sessionUrl
) {
}
