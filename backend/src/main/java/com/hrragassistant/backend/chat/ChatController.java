package com.hrragassistant.backend.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/api/chat")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
                .user(request.question())
                .call()
                .content();

        return new ChatResponse(answer);
    }

    public record ChatRequest(@NotBlank String question) {
    }

    public record ChatResponse(String answer) {
    }
}
