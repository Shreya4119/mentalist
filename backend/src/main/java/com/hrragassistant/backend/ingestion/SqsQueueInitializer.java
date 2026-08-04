package com.hrragassistant.backend.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

/**
 * Ensures the ingestion queue exists on startup (SQS's CreateQueue is
 * idempotent for identical parameters, so this is safe to call every boot,
 * against LocalStack or real AWS alike) and exposes its URL for the
 * enqueue/poll call sites.
 */
@Component
public class SqsQueueInitializer implements ApplicationRunner {

    private final SqsClient sqsClient;
    private final String queueName;
    private volatile String queueUrl;

    public SqsQueueInitializer(SqsClient sqsClient, @Value("${app.aws.sqs.queue-name}") String queueName) {
        this.sqsClient = sqsClient;
        this.queueName = queueName;
    }

    @Override
    public void run(ApplicationArguments args) {
        sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());
        this.queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
    }

    public String getQueueUrl() {
        return queueUrl;
    }
}
