package com.genailab.newscurator.domain.model;

public record NewsStory(
    String id,
    String title,
    String url,
    String author,
    int score,
    String text
) {}
