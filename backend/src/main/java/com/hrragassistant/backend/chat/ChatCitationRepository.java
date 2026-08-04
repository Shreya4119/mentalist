package com.hrragassistant.backend.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCitationRepository extends JpaRepository<ChatCitation, UUID> {

    List<ChatCitation> findByMessageId(UUID messageId);

    List<ChatCitation> findByMessageIdIn(Collection<UUID> messageIds);
}
