package com.akiramenai.videobackend.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.Course;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepo extends JpaRepository<Course, UUID> {
  Optional<Course> findCourseById(UUID id);

  Page<Course> findAllByInstructorId(UUID instructorId, Pageable pageable);

  Page<Course> findCourseById(UUID instructorId, Pageable pageable);

  Optional<Course> findCourseByTitle(String title);

  @Modifying
  @Transactional
  @Query("UPDATE Course c SET c.isHidden = true WHERE c.instructorId = :userId")
  void hideCoursesByUserId(@Param("userId") UUID userId);

  List<CourseIdInterface> findAllByInstructorIdAndIsHidden(UUID instructorId, Boolean isHidden);
}
