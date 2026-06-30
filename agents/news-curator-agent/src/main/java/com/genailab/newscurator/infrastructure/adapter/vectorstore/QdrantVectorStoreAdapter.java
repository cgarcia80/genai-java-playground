package com.genailab.newscurator.infrastructure.adapter.vectorstore;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class QdrantVectorStoreAdapter implements NewsVectorStorePort {
    private final VectorStore vectorStore;

    public QdrantVectorStoreAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void saveStories(List<NewsStory> stories) {
        List<Document> documents = stories.stream()
                .map(s -> new Document(
                        s.text(),
                        Map.of(
                                "id", s.id(),
                                "title", s.title(),
                                "url", s.url(),
                                "author", s.author(),
                                "score", s.score()
                        )
                ))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public List<NewsStory> searchSimilar(String query, int limit) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(limit).build()
        );

        return results.stream()
                .map(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    return new NewsStory(
                            (String) metadata.getOrDefault("id", doc.getId()),
                            (String) metadata.getOrDefault("title", "No Title"),
                            (String) metadata.getOrDefault("url", ""),
                            (String) metadata.getOrDefault("author", "anonymous"),
                            metadata.get("score") != null ? ((Number) metadata.get("score")).intValue() : 0,
                            doc.getText()
                    );
                })
                .toList();
    }
}
