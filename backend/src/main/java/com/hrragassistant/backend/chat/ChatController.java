package com.hrragassistant.backend.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/api/chat")
    public ChatAnswerResponse ask(@Valid @RequestBody ChatRequest request) {
        ChatService.AskResult result = chatService.ask(request.conversationId(), request.question());

        return new ChatAnswerResponse(
                result.conversationId(),
                result.messageId(),
                result.answer(),
                result.citations().stream().map(CitationDto::from).toList());
    }

    @GetMapping("/api/chat/{conversationId}")
    public ConversationHistoryResponse history(@PathVariable UUID conversationId) {
        ChatService.ConversationHistory history = chatService.getHistory(conversationId);

        List<MessageDto> messages = history.messages().stream()
                .map(m -> new MessageDto(
                        m.id(),
                        m.role(),
                        m.content(),
                        m.createdAt(),
                        m.citations().stream().map(CitationDto::from).toList()))
                .toList();

        return new ConversationHistoryResponse(history.conversationId(), messages);
    }

    @PostMapping("/api/chat/{messageId}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable UUID messageId, @Valid @RequestBody FeedbackRequest request) {
        chatService.submitFeedback(messageId, request.rating());
        return ResponseEntity.ok().build();
    }

    public record ChatRequest(UUID conversationId, @NotBlank String question) {
    }

    public record FeedbackRequest(@NotNull FeedbackRating rating) {
    }

    public record CitationDto(UUID documentId, String filename, Integer chunkIndex, String chunkText) {
        static CitationDto from(ChatCitation citation) {
            return new CitationDto(
                    citation.getDocumentId(),
                    citation.getFilename(),
                    citation.getChunkIndex(),
                    citation.getChunkText());
        }
    }

    public record MessageDto(UUID id, MessageRole role, String content, Instant createdAt, List<CitationDto> citations) {
    }

    public record ChatAnswerResponse(UUID conversationId, UUID messageId, String answer, List<CitationDto> citations) {
    }

    public record ConversationHistoryResponse(UUID conversationId, List<MessageDto> messages) {
    }
}
