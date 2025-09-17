package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.akiramenai.videobackend.model.VideoMetadata;

import java.util.Optional;
import java.util.UUID;

public interface VideoMetadataRepo extends JpaRepository<VideoMetadata, UUID> {
  Optional<VideoMetadata> findVideoMetadataById(UUID id);

  Optional<VideoMetadata> findVideoMetadataByVideoFileId(UUID videoFileId);
}
