package com.akiramenai.videobackend.model;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CleanedCourse(
    UUID id,
    UUID instructorId,
    String instructorName,
    String title,
    String description,
    String thumbnailImageName,
    List<String> courseItemIds,
    double price,
    double rating,
    String createdAt,
    String lastModifiedAt
) {
  public CleanedCourse(Course course) {
    this(
        course.getId(),
        course.getInstructorId(),
        course.getInstructor().getUsername(),
        course.getTitle(),
        course.getDescription(),
        course.getThumbnailImageName(),
        course.getCourseItemIds(),
        course.getPrice(),
        (course.getUsersWhoRatedCount() > 0L
            ? (double) course.getTotalStars() / (double) course.getUsersWhoRatedCount()
            : 0.0
        ),
        course.getCreatedAt().toString(),
        course.getLastModifiedAt().toString()
    );
  }
}
