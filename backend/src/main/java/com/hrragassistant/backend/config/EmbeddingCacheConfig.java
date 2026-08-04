package com.hrragassistant.backend.config;

import java.time.Duration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class EmbeddingCacheConfig {

    /**
     * A typed Jackson2JsonRedisSerializer (bound to float[].class), not
     * GenericJackson2JsonRedisSerializer -- the generic one relies on Jackson's
     * polymorphic default-typing to know what to deserialize into, but arrays
     * don't get that type wrapper on write (they're unambiguous to the
     * writer), while a generic Object-typed read demands it. That
     * write/read asymmetry corrupts round-trips for raw array values, so a
     * serializer with a fixed known target type on both sides is required.
     */
    @Bean
    RedisTemplate<String, float[]> embeddingRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, float[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(float[].class));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Wraps the auto-configured (Ollama-backed) EmbeddingModel with a Redis cache.
     * @Primary makes every other consumer -- the pgvector store, the ingestion
     * pipeline -- pick this up transparently with no changes of their own.
     */
    @Bean
    @Primary
    EmbeddingModel cachingEmbeddingModel(EmbeddingModel delegate, RedisTemplate<String, float[]> embeddingRedisTemplate) {
        return new CachingEmbeddingModel(delegate, embeddingRedisTemplate, Duration.ofHours(24));
    }
}
