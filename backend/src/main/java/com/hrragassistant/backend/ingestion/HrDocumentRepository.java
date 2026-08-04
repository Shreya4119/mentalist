package com.hrragassistant.backend.ingestion;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HrDocumentRepository extends JpaRepository<HrDocument, UUID> {
}
