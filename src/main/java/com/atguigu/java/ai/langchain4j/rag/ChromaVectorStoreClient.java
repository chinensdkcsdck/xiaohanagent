package com.atguigu.java.ai.langchain4j.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;

/** Optional Chroma REST adapter. The application remains usable with the local BM25/vector fallback. */
@Component
public class ChromaVectorStoreClient {
    private static final Logger log = LoggerFactory.getLogger(ChromaVectorStoreClient.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${app.vector.chroma.enabled:false}") private boolean enabled;
    @Value("${app.vector.chroma.url:http://localhost:8000}") private String baseUrl;
    @Value("${app.vector.chroma.collection:xiaohan-knowledge}") private String collection;

    public void upsert(String id, TextSegment segment, Embedding embedding) {
        if (!enabled || embedding == null || segment == null) return;
        try {
            Map<String, Object> payload = Map.of(
                    "ids", List.of(id),
                    "embeddings", List.of(embedding.vector()),
                    "documents", List.of(segment.text()),
                    "metadatas", List.of(segment.metadata() == null ? Map.of() : segment.metadata().toMap()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/collections/" + collection + "/upsert"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> { log.debug("Chroma upsert unavailable: {}", error.getMessage()); return null; });
        } catch (Exception exception) {
            log.debug("Chroma upsert skipped: {}", exception.getMessage());
        }
    }

    public List<TextSegment> query(Embedding embedding, int topK, Map<String, String> filters) {
        if (!enabled || embedding == null) return List.of();
        try {
            Map<String, Object> payload = Map.of("query_embeddings", List.of(embedding.vector()), "n_results", topK);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/collections/" + collection + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return List.of();
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode documents = root.path("documents").isArray() && root.path("documents").size() > 0
                    ? root.path("documents").get(0) : root.path("documents");
            JsonNode metadatas = root.path("metadatas").isArray() && root.path("metadatas").size() > 0
                    ? root.path("metadatas").get(0) : root.path("metadatas");
            List<TextSegment> result = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                String text = documents.get(i).asText();
                Map<String, Object> metadata = metadatas.isArray() && i < metadatas.size()
                        ? objectMapper.convertValue(metadatas.get(i), Map.class) : Map.of();
                boolean accepted = filters == null || filters.entrySet().stream()
                        .allMatch(filter -> filter.getValue().equals(String.valueOf(metadata.get(filter.getKey()))));
                if (accepted) result.add(TextSegment.from(text, new dev.langchain4j.data.document.Metadata(metadata)));
            }
            return result;
        } catch (Exception exception) {
            log.debug("Chroma query unavailable: {}", exception.getMessage());
            return List.of();
        }
    }
}
