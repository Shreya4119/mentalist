package com.hrragassistant.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    java.util.List<User> findByRole(Role role);

    boolean existsByUsername(String username);
}
