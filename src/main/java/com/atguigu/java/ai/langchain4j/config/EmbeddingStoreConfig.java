package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class EmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);

    @Autowired
    private EmbeddingModel embeddingModel;

    @Value("${app.vector.pinecone.api-key:}")
    private String pineconeApiKey;

    @Value("${app.vector.pinecone.index:xiaohan-index}")
    private String pineconeIndex;

    @Value("${app.vector.pinecone.namespace:xiaohan-namespace}")
    private String pineconeNamespace;

    @Value("${app.vector.pinecone.cloud:AWS}")
    private String pineconeCloud;

    @Value("${app.vector.pinecone.region:us-east-1}")
    private String pineconeRegion;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        if (!StringUtils.hasText(pineconeApiKey)) {
            // Allow local/dev startup without Pinecone credentials.
            log.warn("Vector store mode: InMemoryEmbeddingStore (PINECONE_API_KEY is empty)");
            return new InMemoryEmbeddingStore<>();
        }

        log.info("Vector store mode: PineconeEmbeddingStore (index={}, namespace={}, cloud={}, region={})",
                pineconeIndex, pineconeNamespace, pineconeCloud, pineconeRegion);
        return PineconeEmbeddingStore.builder()
                .apiKey(pineconeApiKey)
                .index(pineconeIndex)
                .nameSpace(pineconeNamespace)
                .createIndex(PineconeServerlessIndexConfig.builder()
                        .cloud(pineconeCloud)
                        .region(pineconeRegion)
                        .dimension(embeddingModel.dimension())
                        .build())
                .build();
    }
}

