package com.hrragassistant.backend.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;
    private final FeedbackRepository feedbackRepository;

    public ChatService(
            ChatClient chatClient,
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository,
            FeedbackRepository feedbackRepository) {
        this.chatClient = chatClient;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public AskResult ask(UUID conversationId, String question) {
        UUID resolvedConversationId = resolveConversation(conversationId);

        chatMessageRepository.save(new ChatMessage(resolvedConversationId, MessageRole.USER, question));

        ChatResponse chatResponse = chatClient.prompt().user(question).call().chatResponse();
        String answer = chatResponse.getResult().getOutput().getText();

        ChatMessage assistantMessage = new ChatMessage(resolvedConversationId, MessageRole.ASSISTANT, answer);
        chatMessageRepository.save(assistantMessage);

        List<ChatCitation> citations = extractCitations(chatResponse, assistantMessage.getId());
        if (!citations.isEmpty()) {
            chatCitationRepository.saveAll(citations);
        }

        return new AskResult(resolvedConversationId, assistantMessage.getId(), answer, citations);
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
}
