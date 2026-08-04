package com.hrragassistant.backend.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class ChatService {

    private static final int MEMORY_WINDOW_MESSAGES = 10;
    private static final Duration ANSWER_CACHE_TTL = Duration.ofHours(6);
    private static final String ANSWER_CACHE_PREFIX = "chat-answer:";

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;
    private final FeedbackRepository feedbackRepository;
    private final RedisTemplate<String, CachedAnswer> answerCacheTemplate;
    private final MeterRegistry meterRegistry;
    private final double costPer1kTokensUsd;

    public ChatService(
            ChatClient chatClient,
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository,
            FeedbackRepository feedbackRepository,
            RedisTemplate<String, CachedAnswer> answerCacheTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.observability.cost-per-1k-tokens-usd}") double costPer1kTokensUsd) {
        this.chatClient = chatClient;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
        this.feedbackRepository = feedbackRepository;
        this.answerCacheTemplate = answerCacheTemplate;
        this.meterRegistry = meterRegistry;
        this.costPer1kTokensUsd = costPer1kTokensUsd;
    }

    /**
     * Stateless (no conversationId) questions are cache-eligible and don't need
     * prior turns. Continuing an existing conversation always bypasses the cache
     * and loads prior messages as context -- an identical question can
     * legitimately have a different answer depending on what was asked before it.
     */
    public AskResult ask(UUID conversationId, String question) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean cacheHit = false;

        try {
            boolean isNewConversation = conversationId == null;
            UUID resolvedConversationId = resolveConversation(conversationId);

            List<Message> priorMessages = isNewConversation ? List.of() : loadPriorMessages(resolvedConversationId);

            chatMessageRepository.save(new ChatMessage(resolvedConversationId, MessageRole.USER, question));

            CachedAnswer cachedAnswer = isNewConversation ? answerCacheTemplate.opsForValue().get(answerCacheKey(question)) : null;

            String answer;
            List<ChatCitation> citations;
            ChatMessage assistantMessage;

            if (cachedAnswer != null) {
                cacheHit = true;
                answer = cachedAnswer.answer();
                assistantMessage = new ChatMessage(resolvedConversationId, MessageRole.ASSISTANT, answer);
                chatMessageRepository.save(assistantMessage);

                citations = cachedAnswer.citations().stream()
                        .map(c -> new ChatCitation(assistantMessage.getId(), c.documentId(), c.filename(), c.chunkIndex(), c.chunkText()))
                        .toList();
            } else {
                ChatResponse chatResponse = chatClient.prompt()
                        .messages(priorMessages)
                        .user(question)
                        .call()
                        .chatResponse();

                answer = chatResponse.getResult().getOutput().getText();
                assistantMessage = new ChatMessage(resolvedConversationId, MessageRole.ASSISTANT, answer);
                chatMessageRepository.save(assistantMessage);

                citations = extractCitations(chatResponse, assistantMessage.getId());
                recordTokenUsage(chatResponse);

                if (isNewConversation) {
                    cacheAnswer(question, answer, citations);
                }
            }

            if (!citations.isEmpty()) {
                chatCitationRepository.saveAll(citations);
            }

            return new AskResult(resolvedConversationId, assistantMessage.getId(), answer, citations);
        } finally {
            sample.stop(Timer.builder("chat.request.latency")
                    .tag("cache", cacheHit ? "hit" : "miss")
                    .register(meterRegistry));
        }
    }

    /**
     * Ollama is free, so the cost figure here is illustrative -- the point is
     * proving the instrumentation exists for when a paid provider (e.g.
     * Bedrock) is swapped back in. Cache hits cost zero real tokens, so
     * nothing is recorded for that path.
     */
    private void recordTokenUsage(ChatResponse chatResponse) {
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }

        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

        meterRegistry.counter("chat.tokens", "type", "prompt").increment(promptTokens);
        meterRegistry.counter("chat.tokens", "type", "completion").increment(completionTokens);

        double costUsd = (promptTokens + completionTokens) / 1000.0 * costPer1kTokensUsd;
        meterRegistry.counter("chat.cost.usd").increment(costUsd);
    }

    public ConversationHistory getHistory(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + conversationId);
        }

        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        List<UUID> assistantMessageIds = messages.stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                .map(ChatMessage::getId)
                .toList();

        Map<UUID, List<ChatCitation>> citationsByMessageId = new HashMap<>();
        for (ChatCitation citation : chatCitationRepository.findByMessageIdIn(assistantMessageIds)) {
            citationsByMessageId
                    .computeIfAbsent(citation.getMessageId(), id -> new ArrayList<>())
                    .add(citation);
        }

        List<TranscriptMessage> transcript = messages.stream()
                .map(m -> new TranscriptMessage(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt(),
                        citationsByMessageId.getOrDefault(m.getId(), List.of())))
                .toList();

        return new ConversationHistory(conversationId, transcript);
    }

    public void submitFeedback(UUID messageId, FeedbackRating rating) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found: " + messageId));

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback can only be left on assistant messages");
        }

        feedbackRepository.save(new Feedback(messageId, rating));
    }

    private UUID resolveConversation(UUID conversationId) {
        if (conversationId == null) {
            return conversationRepository.save(new Conversation()).getId();
        }

        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + conversationId);
        }
        return conversationId;
    }

    private List<Message> loadPriorMessages(UUID conversationId) {
        List<ChatMessage> history = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        int fromIndex = Math.max(0, history.size() - MEMORY_WINDOW_MESSAGES);
        return history.subList(fromIndex, history.size()).stream()
                .<Message>map(m -> m.getRole() == MessageRole.USER
                        ? new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .toList();
    }

    private void cacheAnswer(String question, String answer, List<ChatCitation> citations) {
        List<CachedCitation> cachedCitations = citations.stream()
                .map(c -> new CachedCitation(c.getDocumentId(), c.getFilename(), c.getChunkIndex(), c.getChunkText()))
                .toList();

        answerCacheTemplate.opsForValue().set(
                answerCacheKey(question), new CachedAnswer(answer, cachedCitations), ANSWER_CACHE_TTL);
    }

    private static String answerCacheKey(String question) {
        return ANSWER_CACHE_PREFIX + question.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<ChatCitation> extractCitations(ChatResponse chatResponse, UUID assistantMessageId) {
        List<Document> retrieved = chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (retrieved == null) {
            return List.of();
        }

        List<ChatCitation> citations = new ArrayList<>(retrieved.size());
        for (Document doc : retrieved) {
            Map<String, Object> metadata = doc.getMetadata();
            citations.add(new ChatCitation(
                    assistantMessageId,
                    UUID.fromString((String) metadata.get("document_id")),
                    (String) metadata.get("filename"),
                    (Integer) metadata.get("chunk_index"),
                    doc.getText()));
        }
        return citations;
    }

    public record AskResult(UUID conversationId, UUID messageId, String answer, List<ChatCitation> citations) {
    }

    public record TranscriptMessage(
            UUID id,
            MessageRole role,
            String content,
            java.time.Instant createdAt,
            List<ChatCitation> citations) {
    }

    public record ConversationHistory(UUID conversationId, List<TranscriptMessage> messages) {
    }

    public record CachedAnswer(String answer, List<CachedCitation> citations) {
    }

    public record CachedCitation(UUID documentId, String filename, Integer chunkIndex, String chunkText) {
    }
}
