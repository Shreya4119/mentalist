package com.hrragassistant.backend.config;

import com.hrragassistant.backend.chat.ChatService.CachedAnswer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AnswerCacheConfig {

    // Typed serializer, not GenericJackson2JsonRedisSerializer -- see the
    // comment on EmbeddingCacheConfig for why the generic one is unsafe here.
    @Bean
    RedisTemplate<String, CachedAnswer> answerCacheTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, CachedAnswer> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(CachedAnswer.class));
        template.afterPropertiesSet();
        return template;
    }
}
