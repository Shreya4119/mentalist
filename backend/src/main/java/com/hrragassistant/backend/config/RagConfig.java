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


    @Bean
    ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore) {
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(5).build())
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(com.hrragassistant.backend.chat.RagPrompts.REPORT_SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor)
                .build();
    }
}
