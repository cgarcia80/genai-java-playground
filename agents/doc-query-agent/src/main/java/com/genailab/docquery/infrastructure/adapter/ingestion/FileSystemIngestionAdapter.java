package com.genailab.docquery.infrastructure.adapter.ingestion;

import com.genailab.docquery.domain.port.DocumentIngestionPort;
import com.genailab.docquery.infrastructure.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Component
public class FileSystemIngestionAdapter implements DocumentIngestionPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemIngestionAdapter.class);
  private static final String AGENT_METADATA_KEY = "doc_query_agent";
  private static final String AGENT_METADATA_VALUE = "true";
  private static final String FILE_NAME_METADATA_KEY = "file_name";

  private final VectorStore vectorStore;
  private final TokenTextSplitter tokenTextSplitter;
  private final AppProperties properties;

  public FileSystemIngestionAdapter(
      VectorStore vectorStore,
      TokenTextSplitter tokenTextSplitter,
      AppProperties properties) {
    this.vectorStore = vectorStore;
    this.tokenTextSplitter = tokenTextSplitter;
    this.properties = properties;
  }

  @Override
  public IngestResult ingestAll() {
    List<Path> files = listDocumentFiles();
    if (files.isEmpty()) {
      return new IngestResult(0, 0);
    }
    List<Document> documents = files.stream()
        .flatMap(file -> readSupportedFile(file).stream())
        .toList();
    List<Document> chunks = tagDocuments(tokenTextSplitter.apply(documents));
    refreshVectorStore(chunks);
    return new IngestResult(files.size(), chunks.size());
  }

  private List<Path> listDocumentFiles() {
    Path docsPath = Path.of(properties.docsPath());
    if (!Files.isDirectory(docsPath)) {
      return List.of();
    }
    try (var paths = Files.list(docsPath)) {
      return paths
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparing(Path::toString))
          .filter(this::isSupportedOrWarn)
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not list docs directory " + docsPath, exception);
    }
  }

  private boolean isSupportedOrWarn(Path file) {
    if (isPdf(file) || isMarkdown(file)) {
      return true;
    }
    LOGGER.warn("Ignoring unsupported document file: {}", file.getFileName());
    return false;
  }

  private List<Document> readSupportedFile(Path file) {
    LOGGER.info("Processing document file: {}", file.getFileName());
    if (isPdf(file)) {
      return tagDocuments(new PagePdfDocumentReader(new FileSystemResource(file)).get(), file);
    }
    return tagDocuments(new TextReader(new FileSystemResource(file)).get(), file);
  }

  private List<Document> tagDocuments(List<Document> documents) {
    return documents.stream()
        .map(document -> tagDocument(document, null))
        .toList();
  }

  private List<Document> tagDocuments(List<Document> documents, Path sourceFile) {
    return documents.stream()
        .map(document -> tagDocument(document, sourceFile))
        .toList();
  }

  private Document tagDocument(Document document, Path sourceFile) {
    Document.Builder builder = document.mutate()
        .metadata(AGENT_METADATA_KEY, AGENT_METADATA_VALUE);
    if (sourceFile != null) {
      builder.metadata(FILE_NAME_METADATA_KEY, sourceFile.getFileName().toString());
    }
    return builder.build();
  }

  private void refreshVectorStore(List<Document> chunks) {
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    vectorStore.delete(builder.eq(AGENT_METADATA_KEY, AGENT_METADATA_VALUE).build());
    if (!chunks.isEmpty()) {
      vectorStore.add(chunks);
    }
  }

  private boolean isPdf(Path file) {
    return extension(file).equals(".pdf");
  }

  private boolean isMarkdown(Path file) {
    String extension = extension(file);
    return extension.equals(".md") || extension.equals(".markdown");
  }

  private String extension(Path file) {
    String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
    int dotIndex = fileName.lastIndexOf('.');
    return dotIndex == -1 ? "" : fileName.substring(dotIndex);
  }
}
