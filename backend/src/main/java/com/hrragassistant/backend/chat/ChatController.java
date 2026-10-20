package com.hrragassistant.backend.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hrragassistant.backend.ingestion.DocumentKind;
import com.hrragassistant.backend.ingestion.DocumentStatus;
import com.hrragassistant.backend.reports.FinancialReportAnalysisRepository;
import com.hrragassistant.backend.reports.ReportAnalysisStatus;
import com.hrragassistant.backend.ingestion.StoredDocument;
import com.hrragassistant.backend.ingestion.StoredDocumentRepository;
import com.hrragassistant.backend.hosting.UsageLimits;
import com.hrragassistant.backend.reports.ReportAccess;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final StoredDocumentRepository documentRepository;
    private final FinancialReportAnalysisRepository analysisRepository;
    private final UsageLimits limits;
    private final ReportAccess access;

    public ChatController(ChatService chatService, StoredDocumentRepository documentRepository,
                          FinancialReportAnalysisRepository analysisRepository, UsageLimits limits, ReportAccess access) {
        this.chatService = chatService;
        this.documentRepository = documentRepository;
        this.analysisRepository = analysisRepository;
        this.limits = limits;
        this.access = access;
    }

    @PostMapping("/api/chat")
    public ChatAnswerResponse ask(@Valid @RequestBody ChatRequest request, Authentication auth) {
        // Every question is about exactly one financial report.
        StoredDocument document = documentRepository.findById(request.documentId())
                .filter(d -> d.getKind() == DocumentKind.FINANCIAL_REPORT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + request.documentId()));
        boolean admin = isAdmin(auth);
        access.requireView(document.getId(), auth.getName(), admin);
        DocumentStatus status = document.getStatus();
        // A report is chat-ready as soon as its AI analysis is done: the analysis digest is
        // indexed straight away, while the full text of a long annual report may still be
        // embedding in the background.
        boolean reportAnalysed = analysisRepository.findByDocumentId(document.getId())
                .map(a -> a.getStatus() == ReportAnalysisStatus.COMPLETED)
                .orElse(false);
        if (status != DocumentStatus.INDEXED && !reportAnalysed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is not ready for questions yet (status " + status + ")");
        }

        limits.acquireQuestion(auth.getName(), admin);
        ChatService.AskResult result = chatService.ask(request.conversationId(), request.question(), request.documentId());

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

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** documentId (a report id) is required: retrieval is always limited to that one report. */
    public record ChatRequest(UUID conversationId, @NotBlank String question, @NotNull UUID documentId) {
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
