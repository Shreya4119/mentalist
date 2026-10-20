package com.hrragassistant.backend.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdSnapshotRepository extends JpaRepository<HouseholdSnapshot, UUID> {

    List<HouseholdSnapshot> findByUsernameOrderByCreatedAtDesc(String username);

    Optional<HouseholdSnapshot> findByIdAndUsername(UUID id, String username);

    /** Account deletion: removes all of a person's saved checks. */
    long deleteByUsername(String username);
}
