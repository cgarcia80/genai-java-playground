package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryNewsUseCase {
    private final NewsVectorStorePort vectorStorePort;
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        You are a helpful, expert AI assistant. Your task is to answer the user's question using ONLY the provided HackerNews stories context.
        
        Rules:
        1. Base your answer solely on the context provided. Do not use external knowledge or make assumptions.
        2. For any fact you state, you MUST explicitly cite its source by mentioning the title and the exact URL of the story. Format citations naturally (e.g., "[Title](URL)").
        3. If the context is empty, or does not contain enough information to answer the question, you must respond politely stating: "I'm sorry, but I couldn't find any relevant local context from HackerNews stories to answer your question."
        """;

    public QueryNewsUseCase(NewsVectorStorePort vectorStorePort, ChatClient.Builder chatClientBuilder) {
        this.vectorStorePort = vectorStorePort;
        this.chatClient = chatClientBuilder.build();
    }

    public QueryResult execute(String question) {
        // Retrieve top 5 matching items
        List<NewsStory> contextStories = vectorStorePort.searchSimilar(question, 5);
        
        String contextText = contextStories.stream()
            .map(s -> String.format("Title: %s\nURL: %s\nAuthor: %s\nScore: %d\nContent: %s\n---", 
                s.title(), s.url(), s.author(), s.score(), s.text()))
            .collect(Collectors.joining("\n"));

        String answer = chatClient.prompt()
            .system(sp -> sp.text(SYSTEM_PROMPT))
            .user(u -> u.text("Context:\n" + contextText + "\n\nQuestion: " + question))
            .call()
            .content();

        List<SourceDto> sources = contextStories.stream()
            .map(s -> new SourceDto(s.title(), s.url()))
            .toList();

        return new QueryResult(answer, sources);
    }

    public record QueryResult(String answer, List<SourceDto> sources) {}
    public record SourceDto(String title, String url) {}
}
