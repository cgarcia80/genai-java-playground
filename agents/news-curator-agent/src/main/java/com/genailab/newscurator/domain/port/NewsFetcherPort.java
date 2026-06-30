package com.genailab.newscurator.domain.port;

import com.genailab.newscurator.domain.model.NewsStory;
import java.util.List;

public interface NewsFetcherPort {
    List<NewsStory> fetchStories(String topic, int limit);
}
