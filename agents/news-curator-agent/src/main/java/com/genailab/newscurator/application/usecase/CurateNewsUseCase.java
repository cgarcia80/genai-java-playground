package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsFetcherPort;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CurateNewsUseCase {
    private final NewsFetcherPort fetcherPort;
    private final NewsVectorStorePort vectorStorePort;

    public CurateNewsUseCase(NewsFetcherPort fetcherPort, NewsVectorStorePort vectorStorePort) {
        this.fetcherPort = fetcherPort;
        this.vectorStorePort = vectorStorePort;
    }

    public int curate(String topic, int limit) {
        List<NewsStory> stories = fetcherPort.fetchStories(topic, limit);
        if (!stories.isEmpty()) {
            vectorStorePort.saveStories(stories);
        }
        return stories.size();
    }
}
