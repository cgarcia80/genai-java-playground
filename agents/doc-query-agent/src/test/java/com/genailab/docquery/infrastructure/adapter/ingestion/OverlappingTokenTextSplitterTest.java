package com.genailab.docquery.infrastructure.adapter.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class OverlappingTokenTextSplitterTest {

  @Test
  void shouldCarryConfiguredOverlapBetweenChunks() {
    String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda";
    OverlappingTokenTextSplitter splitter =
        new OverlappingTokenTextSplitter(6, 2, 1, 1, 100, true);
    Document document = Document.builder()
        .text(text)
        .metadata("file_name", "sample.md")
        .build();
    Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    List<Integer> tokens = encoding.encode(text).boxed();

    List<Document> chunks = splitter.apply(List.of(document));

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks.get(0).getText()).isEqualTo(decode(encoding, tokens.subList(0, 6)).trim());
    assertThat(chunks.get(1).getText()).isEqualTo(decode(encoding, tokens.subList(4, 10)).trim());
    assertThat(chunks.get(1).getMetadata()).containsEntry("file_name", "sample.md");
  }

  @Test
  void shouldRejectOverlapGreaterThanOrEqualToChunkSize() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OverlappingTokenTextSplitter(50, 50));
  }

  private String decode(Encoding encoding, List<Integer> tokens) {
    IntArrayList encodedTokens = new IntArrayList(tokens.size());
    tokens.forEach(encodedTokens::add);
    return encoding.decode(encodedTokens);
  }
}
