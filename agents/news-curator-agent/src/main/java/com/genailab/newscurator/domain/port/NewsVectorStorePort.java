package com.genailab.newscurator.domain.port;

import com.genailab.newscurator.domain.model.NewsStory;
import java.util.List;

public interface NewsVectorStorePort {
    void saveStories(List<NewsStory> stories);
    List<NewsStory> searchSimilar(String query, int limit);
}
