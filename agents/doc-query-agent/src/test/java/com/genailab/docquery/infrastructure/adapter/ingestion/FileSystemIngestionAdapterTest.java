package com.genailab.docquery.infrastructure.adapter.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.genailab.docquery.domain.port.DocumentIngestionPort;
import com.genailab.docquery.infrastructure.config.AppProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

class FileSystemIngestionAdapterTest {

  @TempDir
  Path docsPath;

  @Test
  void shouldReturnZeroCountsWhenDirectoryIsEmpty() {
    VectorStore vectorStore = mock(VectorStore.class);
    FileSystemIngestionAdapter adapter = new FileSystemIngestionAdapter(
        vectorStore,
        new TokenTextSplitter(),
        new AppProperties(docsPath.toString(), new AppProperties.Rag(5, 512, 50, 0.70)));

    DocumentIngestionPort.IngestResult result = adapter.ingestAll();

    assertThat(result.filesProcessed()).isZero();
    assertThat(result.chunksLoaded()).isZero();
    verifyNoInteractions(vectorStore);
  }
}
