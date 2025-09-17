package com.akiramenai.videobackend.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.Users;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<Users, UUID> {
  Optional<Users> findUsersByUsername(String username);

  Optional<Users> findUsersById(UUID id);

  Optional<Users> findUsersByEmail(String email);

  Optional<Users> getUsersByEmail(String email);
}
