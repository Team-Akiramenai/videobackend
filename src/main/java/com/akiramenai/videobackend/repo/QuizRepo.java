package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.akiramenai.videobackend.model.Quiz;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepo extends JpaRepository<Quiz, UUID> {
  Optional<Quiz> findQuizById(UUID id);
}
