package com.genailab.docquery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class QueryResponseTest {

  @Test
  void shouldCreateResponseWhenSourcesAreEmpty() {
    QueryResponse response = new QueryResponse("No relevant information found.", List.of());

    assertThat(response.answer()).isEqualTo("No relevant information found.");
    assertThat(response.sources()).isEmpty();
  }
}
