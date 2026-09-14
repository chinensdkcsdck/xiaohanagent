package com.atguigu.java.ai.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

/** BM25 + vector multi-recall retriever with reciprocal-rank fusion. */
public class HybridContentRetriever implements ContentRetriever {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeIndex knowledgeIndex;
    private final ChromaVectorStoreClient chromaVectorStoreClient;
    public HybridContentRetriever(EmbeddingModel embeddingModel, KnowledgeIndex knowledgeIndex,
                                  ChromaVectorStoreClient chromaVectorStoreClient) {
        this.embeddingModel = embeddingModel;
        this.knowledgeIndex = knowledgeIndex;
        this.chromaVectorStoreClient = chromaVectorStoreClient;
    }
    @Value("${app.rag.top-k:3}") private int topK;

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) return List.of();
        Embedding embedding = embeddingModel.embed(query.text()).content();
        Map<String, String> filters = query.metadata() == null ? Map.of() : Map.of();
        List<Content> local = knowledgeIndex.search(query.text(), embedding, Math.max(1, topK), filters).stream()
                .map(KnowledgeIndex.RankedContent::content).toList();
        return merge(local, chromaVectorStoreClient.query(embedding, Math.max(1, topK), filters), topK);
    }

    public List<Content> retrieve(String query, Map<String, String> metadataFilters) {
        Embedding embedding = embeddingModel.embed(query).content();
        List<Content> local = knowledgeIndex.search(query, embedding, Math.max(1, topK), metadataFilters).stream()
                .map(KnowledgeIndex.RankedContent::content).toList();
        return merge(local, chromaVectorStoreClient.query(embedding, Math.max(1, topK), metadataFilters), topK);
    }

    private List<Content> merge(List<Content> local, List<dev.langchain4j.data.segment.TextSegment> chroma,
                                int topK) {
        java.util.LinkedHashMap<String, Content> merged = new java.util.LinkedHashMap<>();
        local.forEach(content -> merged.put(content.textSegment().text(), content));
        chroma.forEach(segment -> merged.putIfAbsent(segment.text(), Content.from(segment)));
        return merged.values().stream().limit(topK).toList();
    }
}
