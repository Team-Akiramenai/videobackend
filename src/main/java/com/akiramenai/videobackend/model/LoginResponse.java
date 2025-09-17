package com.akiramenai.videobackend.model;

import lombok.Builder;

@Builder
public record LoginResponse(
    String accessToken,
    String accountType
) {
}
