package com.atguigu.java.ai.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hybrid retrieval index. The vector branch is backed by the configured store
 * (Chroma/Pinecone/InMemory), while the lexical branch is a small BM25 index.
 * Keeping the lexical index beside the vector store makes local development
 * deterministic and allows RRF to work even when a vector provider is down.
 */
@Component
public class KnowledgeIndex {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    public void index(TextSegment segment, Embedding embedding) {
        if (segment == null || segment.text() == null || segment.text().isBlank()) {
            return;
        }
        String key = documentKey(segment);
        entries.removeIf(entry -> entry.key.equals(key));
        entries.add(new Entry(key, segment, embedding, tokenize(segment.text())));
    }

    public List<RankedContent> search(String query, Embedding queryEmbedding, int topK,
                                      Map<String, String> metadataFilters) {
        if (query == null || query.isBlank() || entries.isEmpty()) {
            return List.of();
        }
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        Map<String, Integer> documentFrequency = documentFrequency(queryTerms, metadataFilters);
        double averageLength = entries.stream()
                .filter(entry -> matches(entry.segment, metadataFilters))
                .mapToInt(entry -> entry.terms.size()).average().orElse(1D);

        Map<String, Integer> lexicalRank = new HashMap<>();
        entries.stream().filter(entry -> matches(entry.segment, metadataFilters))
                .sorted(Comparator.comparingDouble((Entry entry) -> bm25(entry, queryTerms, documentFrequency, averageLength)).reversed())
                .limit(Math.max(topK * 3, 10))
                .forEachOrdered(entry -> lexicalRank.put(entry.key, lexicalRank.size() + 1));

        Map<String, Integer> vectorRank = new HashMap<>();
        if (queryEmbedding != null) {
            try {
                List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(Math.max(topK * 3, 10)).minScore(0D).build()).matches();
                int rank = 1;
                for (EmbeddingMatch<TextSegment> match : matches) {
                    Entry entry = entries.stream().filter(candidate -> candidate.segment.equals(match.embedded())).findFirst().orElse(null);
                    if (entry != null && matches(entry.segment, metadataFilters)) {
                        vectorRank.putIfAbsent(entry.key, rank++);
                    }
                }
            } catch (RuntimeException ignored) {
                // BM25 remains available when the remote vector service is unavailable.
            }
        }

        List<RankedContent> result = new ArrayList<>();
        entries.stream().filter(entry -> lexicalRank.containsKey(entry.key) || vectorRank.containsKey(entry.key))
                .filter(entry -> matches(entry.segment, metadataFilters))
                .forEach(entry -> {
                    int lexical = lexicalRank.getOrDefault(entry.key, Integer.MAX_VALUE);
                    int vector = vectorRank.getOrDefault(entry.key, Integer.MAX_VALUE);
                    double rrf = reciprocalRank(lexical) + reciprocalRank(vector);
                    result.add(new RankedContent(Content.from(entry.segment), rrf, lexical, vector));
                });
        result.sort(Comparator.comparingDouble(RankedContent::score).reversed());
        return result.stream().limit(topK).toList();
    }

    public int size() {
        return entries.size();
    }

    private double reciprocalRank(int rank) {
        return rank == Integer.MAX_VALUE ? 0D : 1D / (60D + rank);
    }

    private Map<String, Integer> documentFrequency(Set<String> queryTerms, Map<String, String> filters) {
        Map<String, Integer> result = new HashMap<>();
        for (String term : queryTerms) {
            int count = (int) entries.stream().filter(entry -> matches(entry.segment, filters) && entry.terms.contains(term)).count();
            result.put(term, Math.max(count, 1));
        }
        return result;
    }

    private double bm25(Entry entry, Set<String> queryTerms, Map<String, Integer> df, double averageLength) {
        double score = 0D;
        int documentCount = Math.max(entries.size(), 1);
        for (String term : queryTerms) {
            int frequency = (int) entry.terms.stream().filter(term::equals).count();
            if (frequency == 0) continue;
            double idf = Math.log(1D + (documentCount - df.getOrDefault(term, 1) + 0.5D) / (df.getOrDefault(term, 1) + 0.5D));
            double denominator = frequency + BM25_K1 * (1D - BM25_B + BM25_B * entry.terms.size() / averageLength);
            score += idf * frequency * (BM25_K1 + 1D) / denominator;
        }
        return score;
    }

    private boolean matches(TextSegment segment, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return true;
        if (segment.metadata() == null) return false;
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            if (!filter.getValue().equals(segment.metadata().getString(filter.getKey()))) return false;
        }
        return true;
    }

    private String documentKey(TextSegment segment) {
        String parent = segment.metadata() == null ? null : segment.metadata().getString("parentId");
        return (parent == null ? "root" : parent) + ":" + Integer.toHexString(segment.text().hashCode());
    }

    private List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null) return terms;
        for (String token : text.toLowerCase().split("[^\\p{IsHan}\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) terms.add(token);
            // Chinese text has no spaces; index overlapping bi-grams for BM25.
            if (token.length() > 1 && token.codePoints().count() > 1) {
                int[] codePoints = token.codePoints().toArray();
                for (int i = 0; i + 1 < codePoints.length; i++) terms.add(new String(codePoints, i, 2));
            }
        }
        return terms;
    }

    private record Entry(String key, TextSegment segment, Embedding embedding, List<String> terms) {}

    public record RankedContent(Content content, double score, int lexicalRank, int vectorRank) {}
}
