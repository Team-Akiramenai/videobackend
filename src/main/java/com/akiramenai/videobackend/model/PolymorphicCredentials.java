package com.akiramenai.videobackend.model;

public record PolymorphicCredentials(String userEmail, String password, String jwtToken) {
}
