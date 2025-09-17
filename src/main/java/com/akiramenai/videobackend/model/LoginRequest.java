package com.akiramenai.videobackend.model;

public record LoginRequest(
    String email,
    String password
) {
}
