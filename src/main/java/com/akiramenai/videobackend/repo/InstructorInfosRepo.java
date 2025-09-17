package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.InstructorInfos;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorInfosRepo extends JpaRepository<InstructorInfos, UUID> {
  Optional<InstructorInfos> getInstructorInfosById(UUID id);
}
