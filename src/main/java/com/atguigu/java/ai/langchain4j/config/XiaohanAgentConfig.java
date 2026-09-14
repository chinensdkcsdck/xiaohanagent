package com.atguigu.java.ai.langchain4j.config;

import com.atguigu.java.ai.langchain4j.store.MongoChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import com.atguigu.java.ai.langchain4j.rag.HybridContentRetriever;
import com.atguigu.java.ai.langchain4j.rag.KnowledgeIndex;
import com.atguigu.java.ai.langchain4j.rag.ChromaVectorStoreClient;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class XiaohanAgentConfig {

    @Autowired
    private MongoChatMemoryStore chatMemoryStore;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AiMetricsState aiMetricsState;

    @Autowired
    private KnowledgeIndex knowledgeIndex;

    @Autowired
    private ChromaVectorStoreClient chromaVectorStoreClient;

    @Value("${app.rag.observed.enabled:true}")
    private boolean ragObservedEnabled;

    @Bean
    @Primary
    public ChatMemoryProvider chatMemoryProviderXiaohan() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean(name = "contentRetrieverXiaohanPinecone")
    public ContentRetriever contentRetrieverXiaohanPinecone() {
        ContentRetriever delegate = new HybridContentRetriever(embeddingModel, knowledgeIndex, chromaVectorStoreClient);
        if (!ragObservedEnabled) {
            return delegate;
        }
        return new ObservedContentRetriever(delegate, meterRegistry, aiMetricsState);
    }
}

