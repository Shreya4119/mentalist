package com.hrragassistant.backend.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** On startup, puts documents an earlier run never finished back on the ingestion queue. */
@Component
public class IngestionRecovery {

    private static final Logger log = LoggerFactory.getLogger(IngestionRecovery.class);

    private final DocumentIngestionService ingestionService;

    public IngestionRecovery(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requeueUnfinished() {
        try {
            int n = ingestionService.requeueUnfinished();
            if (n > 0) {
                log.info("Re-queued {} document(s) left unfinished by a previous run", n);
            }
        } catch (Exception e) {
            log.warn("Could not re-queue unfinished documents: {}", e.getMessage());
        }
    }
}
