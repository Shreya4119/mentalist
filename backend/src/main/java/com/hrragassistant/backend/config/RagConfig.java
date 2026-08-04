package com.hrragassistant.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    private static final String GROUNDING_SYSTEM_PROMPT = """
            You are an HR/payroll knowledge assistant. Answer the user's question using ONLY the
            document context provided to you. If the context does not contain the answer, say
            "I don't know based on the available documents" instead of guessing or using outside
            knowledge.
            """;

    @Bean
    ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore) {
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(5).build())
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(GROUNDING_SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor)
                .build();
    }
}
