package com.genailab.newscurator.infrastructure.adapter.vectorstore;

import com.genailab.newscurator.domain.model.NewsStory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QdrantVectorStoreAdapterTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private QdrantVectorStoreAdapter adapter;

    @Test
    @SuppressWarnings("unchecked")
    void whenSaveStories_thenMapToDocumentsAndAddToVectorStore() {
        // Arrange
        List<NewsStory> stories = List.of(
                new NewsStory("1", "Title 1", "http://url1", "author1", 100, "Text 1")
        );

        // Act
        adapter.saveStories(stories);

        // Assert
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());
        
        List<Document> documents = captor.getValue();
        assertNotNull(documents);
        assertEquals(1, documents.size());
        
        Document doc = documents.get(0);
        assertEquals("Text 1", doc.getText());
        
        Map<String, Object> metadata = doc.getMetadata();
        assertEquals("1", metadata.get("id"));
        assertEquals("Title 1", metadata.get("title"));
        assertEquals("http://url1", metadata.get("url"));
        assertEquals("author1", metadata.get("author"));
        assertEquals(100, metadata.get("score"));
    }

    @Test
    void whenSearchSimilar_thenPerformSearchAndMapToNewsStories() {
        // Arrange
        String query = "test query";
        int limit = 5;
        
        Document doc1 = new Document("Text 1", Map.of(
                "id", "1",
                "title", "Title 1",
                "url", "http://url1",
                "author", "author1",
                "score", 100
        ));
        
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1));

        // Act
        List<NewsStory> results = adapter.searchSimilar(query, limit);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        
        NewsStory story = results.get(0);
        assertEquals("1", story.id());
        assertEquals("Title 1", story.title());
        assertEquals("http://url1", story.url());
        assertEquals("author1", story.author());
        assertEquals(100, story.score());
        assertEquals("Text 1", story.text());
        
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(1)).similaritySearch(captor.capture());
        
        SearchRequest request = captor.getValue();
        assertEquals(query, request.getQuery());
        assertEquals(limit, request.getTopK());
    }
}
