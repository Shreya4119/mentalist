package com.hrragassistant.backend.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Plain AWS SDK v2 client, not Spring Cloud AWS -- that starter's Spring Boot
 * 4 compatibility couldn't be confirmed at the time this was written, and the
 * SDK client itself has no such coupling. Points at LocalStack for local/free
 * dev; swapping to real AWS SQS later is just endpoint/credentials config.
 */
@Configuration
public class SqsConfig {

    @Bean
    SqsClient sqsClient(
            @Value("${app.aws.sqs.endpoint}") String endpoint,
            @Value("${app.aws.sqs.region}") String region) {
        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("localstack", "localstack")))
                .build();
    }
}
