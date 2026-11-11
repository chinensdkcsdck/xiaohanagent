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
        Map<String, String> filters = query.metadata() == null ? Map.of() : Map.of();
        return retrieveVariants(query.text(), filters);
    }

    public List<Content> retrieve(String query, Map<String, String> metadataFilters) {
        return retrieveVariants(query, metadataFilters);
    }

    /** Agentic multi-query recall: original wording, medical wording and a HyDE-style hypothesis. */
    private List<Content> retrieveVariants(String query, Map<String, String> filters) {
        java.util.LinkedHashMap<String, Content> candidates = new java.util.LinkedHashMap<>();
        for (String variant : List.of(query, medicalRewrite(query), hyde(query))) {
            Embedding embedding = embeddingModel.embed(variant).content();
            List<Content> local = knowledgeIndex.search(variant, embedding, Math.max(topK * 2, 6), filters).stream()
                    .map(KnowledgeIndex.RankedContent::content).toList();
            merge(local, chromaVectorStoreClient.query(embedding, Math.max(topK * 2, 6), filters), topK * 2)
                    .forEach(content -> candidates.putIfAbsent(content.textSegment().text(), content));
        }
        return candidates.values().stream().limit(topK).toList();
    }

    private String medicalRewrite(String query) {
        return query.replace("胃镜", "上消化道内镜检查")
                .replace("心慌", "心悸").replace("胸闷", "胸部不适");
    }

    private String hyde(String query) {
        return "医疗知识证据：" + medicalRewrite(query) + "的适用条件、流程和注意事项。";
    }

    private List<Content> merge(List<Content> local, List<dev.langchain4j.data.segment.TextSegment> chroma,
                                int topK) {
        java.util.LinkedHashMap<String, Content> merged = new java.util.LinkedHashMap<>();
        local.forEach(content -> merged.put(content.textSegment().text(), content));
        chroma.forEach(segment -> merged.putIfAbsent(segment.text(), Content.from(segment)));
        return merged.values().stream().limit(topK).toList();
    }
}
