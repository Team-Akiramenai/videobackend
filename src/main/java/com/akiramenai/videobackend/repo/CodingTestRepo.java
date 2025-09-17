package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.akiramenai.videobackend.model.CodingTest;

import java.util.Optional;
import java.util.UUID;

public interface CodingTestRepo extends JpaRepository<CodingTest, UUID> {
  Optional<CodingTest> findCodingTestById(UUID id);
}
