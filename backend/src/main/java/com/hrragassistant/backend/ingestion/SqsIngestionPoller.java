package com.hrragassistant.backend.ingestion;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Long-polls the ingestion queue and hands each message to
 * DocumentIngestionService.processQueuedDocument(). No retry/DLQ in this
 * phase -- a failed document is marked FAILED (visible via
 * GET /api/reports/{id}) and the message is still deleted rather than
 * redelivered. A real deployment would add a redrive policy / DLQ here.
 */
@Component
public class SqsIngestionPoller {

    private static final Logger log = LoggerFactory.getLogger(SqsIngestionPoller.class);

    private final SqsClient sqsClient;
    private final SqsQueueInitializer sqsQueueInitializer;
    private final DocumentIngestionService documentIngestionService;

    public SqsIngestionPoller(
            SqsClient sqsClient,
            SqsQueueInitializer sqsQueueInitializer,
            DocumentIngestionService documentIngestionService) {
        this.sqsClient = sqsClient;
        this.sqsQueueInitializer = sqsQueueInitializer;
        this.documentIngestionService = documentIngestionService;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        String queueUrl = sqsQueueInitializer.getQueueUrl();
        if (queueUrl == null) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(10)
                        .build())
                .messages();

        for (Message message : messages) {
            try {
                documentIngestionService.processQueuedDocument(UUID.fromString(message.body()));
            } catch (Exception e) {
                log.error("Failed to process queued document message {}: {}", message.messageId(), e.getMessage(), e);
            } finally {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        }
    }
}
