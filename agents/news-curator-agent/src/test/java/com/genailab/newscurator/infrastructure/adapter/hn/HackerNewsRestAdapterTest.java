package com.genailab.newscurator.infrastructure.adapter.hn;

import com.genailab.newscurator.domain.model.NewsStory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HackerNewsRestAdapterTest {

    @Mock
    private RestClient algoliaClient;

    @Mock
    private RestClient firebaseClient;

    private HackerNewsRestAdapter adapter;

    @BeforeEach
    void setUp() {
        Executor executor = ForkJoinPool.commonPool();
        adapter = new HackerNewsRestAdapter(algoliaClient, firebaseClient, executor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenFetchStories_thenCallAlgoliaAndFirebaseAndReturnStories() {
        // Arrange
        String topic = "Java";
        int limit = 2;

        RestClient.RequestHeadersUriSpec algoliaUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec algoliaResponseSpec = mock(RestClient.ResponseSpec.class);

        when(algoliaClient.get()).thenReturn(algoliaUriSpec);
        when(algoliaUriSpec.uri(any(Function.class))).thenReturn(algoliaUriSpec);
        when(algoliaUriSpec.retrieve()).thenReturn(algoliaResponseSpec);
        when(algoliaResponseSpec.body(AlgoliaSearchResponse.class)).thenReturn(
                new AlgoliaSearchResponse(List.of(new AlgoliaHit("12345"), new AlgoliaHit("67890")))
        );

        RestClient.RequestHeadersUriSpec baseFirebaseUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersUriSpec firebaseUriSpec1 = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersUriSpec firebaseUriSpec2 = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec firebaseResponseSpec1 = mock(RestClient.ResponseSpec.class);
        RestClient.ResponseSpec firebaseResponseSpec2 = mock(RestClient.ResponseSpec.class);

        when(firebaseClient.get()).thenReturn(baseFirebaseUriSpec);
        when(baseFirebaseUriSpec.uri(eq("/item/{id}.json"), eq("12345"))).thenReturn(firebaseUriSpec1);
        when(baseFirebaseUriSpec.uri(eq("/item/{id}.json"), eq("67890"))).thenReturn(firebaseUriSpec2);
        
        when(firebaseUriSpec1.retrieve()).thenReturn(firebaseResponseSpec1);
        when(firebaseUriSpec2.retrieve()).thenReturn(firebaseResponseSpec2);

        when(firebaseResponseSpec1.body(FirebaseStoryItem.class)).thenReturn(
                new FirebaseStoryItem("12345", "story", "author1", 100, "Java 21 Released", "Discussion about Java 21", "http://java21")
        );
        when(firebaseResponseSpec2.body(FirebaseStoryItem.class)).thenReturn(
                new FirebaseStoryItem("67890", "story", "author2", 150, "Virtual Threads", "Discussion about Virtual Threads", "http://threads")
        );

        // Act
        List<NewsStory> stories = adapter.fetchStories(topic, limit);

        // Assert
        assertNotNull(stories);
        assertEquals(2, stories.size());

        NewsStory story1 = stories.get(0);
        assertEquals("12345", story1.id());
        assertEquals("Java 21 Released", story1.title());
        assertEquals("http://java21", story1.url());
        assertEquals("author1", story1.author());
        assertEquals(100, story1.score());
        assertEquals("Discussion about Java 21", story1.text());

        NewsStory story2 = stories.get(1);
        assertEquals("67890", story2.id());
        assertEquals("Virtual Threads", story2.title());
        assertEquals("http://threads", story2.url());
        assertEquals("author2", story2.author());
        assertEquals(150, story2.score());
        assertEquals("Discussion about Virtual Threads", story2.text());
    }
}
