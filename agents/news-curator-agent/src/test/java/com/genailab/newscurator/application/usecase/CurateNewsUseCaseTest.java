package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsFetcherPort;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurateNewsUseCaseTest {

    @Mock
    private NewsFetcherPort fetcherPort;

    @Mock
    private NewsVectorStorePort vectorStorePort;

    @InjectMocks
    private CurateNewsUseCase curateNewsUseCase;

    @Test
    void whenStoriesFetched_thenSaveStoriesAndReturnCount() {
        // Arrange
        String topic = "Java 21";
        int limit = 5;
        List<NewsStory> stories = List.of(
                new NewsStory("1", "Title 1", "http://url1", "author1", 100, "Text 1"),
                new NewsStory("2", "Title 2", "http://url2", "author2", 150, "Text 2")
        );
        when(fetcherPort.fetchStories(topic, limit)).thenReturn(stories);

        // Act
        int result = curateNewsUseCase.curate(topic, limit);

        // Assert
        assertEquals(2, result);
        verify(vectorStorePort, times(1)).saveStories(stories);
    }

    @Test
    void whenNoStoriesFetched_thenDoNotSaveStoriesAndReturnZero() {
        // Arrange
        String topic = "Nonexistent";
        int limit = 5;
        when(fetcherPort.fetchStories(topic, limit)).thenReturn(Collections.emptyList());

        // Act
        int result = curateNewsUseCase.curate(topic, limit);

        // Assert
        assertEquals(0, result);
        verify(vectorStorePort, never()).saveStories(any());
    }
}
