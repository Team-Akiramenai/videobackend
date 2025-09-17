package com.akiramenai.videobackend.model;

public record StorageInfoResponse(long totalStorageBytes, long usedStorageBytes, double usagePercentage) {
}
