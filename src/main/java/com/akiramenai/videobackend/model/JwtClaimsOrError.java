package com.akiramenai.videobackend.model;

import io.jsonwebtoken.Claims;
import lombok.Builder;

@Builder
public record JwtClaimsOrError(
    Claims claims,
    String friendlyErrorMessage
) {
}
