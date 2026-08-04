package com.hrragassistant.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Wraps the real EmbeddingModel with a Redis cache keyed by a hash of the
 * input text -- embeddings are a pure function of their input, so this is
 * always safe to cache (unlike chat answers, which depend on conversation
 * context once memory is involved). Only single-text requests are cached;
 * batched multi-text requests (bulk ingestion) pass through uncached to
 * keep the ordering/merging logic simple.
 */
public class CachingEmbeddingModel implements EmbeddingModel {

    private static final String CACHE_KEY_PREFIX = "embedding:";

    private final EmbeddingModel delegate;
    private final RedisTemplate<String, float[]> redisTemplate;
    private final Duration ttl;

    public CachingEmbeddingModel(EmbeddingModel delegate, RedisTemplate<String, float[]> redisTemplate, Duration ttl) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        if (instructions.size() != 1) {
            return delegate.call(request);
        }

        String text = instructions.get(0);
        String cacheKey = CACHE_KEY_PREFIX + sha256(text);

        float[] cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return new EmbeddingResponse(List.of(new Embedding(cached, 0, EmbeddingResultMetadata.EMPTY)));
        }

        EmbeddingResponse response = delegate.call(request);
        float[] vector = response.getResult().getOutput();
        redisTemplate.opsForValue().set(cacheKey, vector, ttl);
        return response;
    }

    @Override
    public float[] embed(Document document) {
        return embed(delegate.getEmbeddingContent(document));
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
