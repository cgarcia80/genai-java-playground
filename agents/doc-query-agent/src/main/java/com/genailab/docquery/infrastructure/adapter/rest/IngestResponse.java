package com.genailab.docquery.infrastructure.adapter.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IngestResponse(
    @JsonProperty("files_processed") int filesProcessed,
    @JsonProperty("chunks_loaded") int chunksLoaded) {
}
