package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.LearnerInfos;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearnerInfosRepo extends JpaRepository<LearnerInfos, UUID> {
  Optional<LearnerInfos> findLearnerInfosById(UUID id);

}
