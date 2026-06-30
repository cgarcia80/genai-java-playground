package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryNewsUseCaseTest {

    @Mock
    private NewsVectorStorePort vectorStorePort;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private QueryNewsUseCase queryNewsUseCase;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        queryNewsUseCase = new QueryNewsUseCase(vectorStorePort, chatClientBuilder);
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuted_thenSearchSimilarAndReturnAnswerWithSources() {
        // Arrange
        String question = "What is Java 21?";
        List<NewsStory> stories = List.of(
                new NewsStory("1", "Java 21 Released", "http://java21", "author1", 200, "Java 21 features")
        );
        when(vectorStorePort.searchSimilar(question, 5)).thenReturn(stories);
        
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Java 21 was released with virtual threads.");

        // Act
        QueryNewsUseCase.QueryResult result = queryNewsUseCase.execute(question);

        // Assert
        assertEquals("Java 21 was released with virtual threads.", result.answer());
        assertEquals(1, result.sources().size());
        assertEquals("Java 21 Released", result.sources().get(0).title());
        assertEquals("http://java21", result.sources().get(0).url());
        
        verify(vectorStorePort, times(1)).searchSimilar(question, 5);
    }
}
