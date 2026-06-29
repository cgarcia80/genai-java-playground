package com.genailab.docquery.infrastructure.adapter.ingestion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.util.Assert;

public class OverlappingTokenTextSplitter extends TokenTextSplitter {

  private static final int DEFAULT_MIN_CHUNK_SIZE_CHARS = 350;
  private static final int DEFAULT_MIN_CHUNK_LENGTH_TO_EMBED = 5;
  private static final int DEFAULT_MAX_NUM_CHUNKS = 10000;
  private static final boolean DEFAULT_KEEP_SEPARATOR = true;

  private final Encoding encoding;
  private final int chunkSize;
  private final int chunkOverlap;
  private final int minChunkLengthToEmbed;
  private final int maxNumChunks;

  public OverlappingTokenTextSplitter(int chunkSize, int chunkOverlap) {
    this(
        chunkSize,
        chunkOverlap,
        DEFAULT_MIN_CHUNK_SIZE_CHARS,
        DEFAULT_MIN_CHUNK_LENGTH_TO_EMBED,
        DEFAULT_MAX_NUM_CHUNKS,
        DEFAULT_KEEP_SEPARATOR);
  }

  OverlappingTokenTextSplitter(
      int chunkSize,
      int chunkOverlap,
      int minChunkSizeChars,
      int minChunkLengthToEmbed,
      int maxNumChunks,
      boolean keepSeparator) {
    super(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
    Assert.isTrue(chunkSize > 0, "chunkSize must be greater than zero");
    Assert.isTrue(chunkOverlap >= 0, "chunkOverlap must not be negative");
    Assert.isTrue(chunkOverlap < chunkSize, "chunkOverlap must be smaller than chunkSize");
    this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    this.chunkSize = chunkSize;
    this.chunkOverlap = chunkOverlap;
    this.minChunkLengthToEmbed = minChunkLengthToEmbed;
    this.maxNumChunks = maxNumChunks;
  }

  @Override
  protected List<String> splitText(String text) {
    if (text == null || text.trim().isEmpty()) {
      return List.of();
    }
    List<Integer> tokens = encoding.encode(text).boxed();
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < tokens.size() && chunks.size() < maxNumChunks) {
      int end = Math.min(start + chunkSize, tokens.size());
      addChunk(tokens.subList(start, end), chunks);
      if (end == tokens.size()) {
        break;
      }
      start = end - chunkOverlap;
    }
    return chunks;
  }

  private void addChunk(List<Integer> tokens, List<String> chunks) {
    String chunk = decode(tokens).trim();
    if (chunk.length() > minChunkLengthToEmbed) {
      chunks.add(chunk);
    }
  }

  private String decode(List<Integer> tokens) {
    IntArrayList encodedTokens = new IntArrayList(tokens.size());
    tokens.forEach(encodedTokens::add);
    return encoding.decode(encodedTokens);
  }
}
