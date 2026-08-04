package com.hrragassistant.backend.config;

import java.time.Duration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class EmbeddingCacheConfig {

    @Bean
    RedisTemplate<String, float[]> embeddingRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, float[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
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
