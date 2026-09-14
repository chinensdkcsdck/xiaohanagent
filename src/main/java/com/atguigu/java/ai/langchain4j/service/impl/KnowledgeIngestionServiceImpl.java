package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.VisitPreparation;
import com.atguigu.java.ai.langchain4j.mapper.VisitPreparationMapper;
import com.atguigu.java.ai.langchain4j.service.KnowledgeIngestionService;
import com.atguigu.java.ai.langchain4j.rag.ChromaVectorStoreClient;
import com.atguigu.java.ai.langchain4j.rag.KnowledgeIndex;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VisitPreparationMapper visitPreparationMapper;

    @Autowired
    private KnowledgeIndex knowledgeIndex;

    @Autowired
    private ChromaVectorStoreClient chromaVectorStoreClient;

    @Override
    public int ingestWorkflowKnowledge() {
        List<String> docs = new ArrayList<>();
        docs.add("候补与改约流程：当预约号源已满时，引导用户加入候补队列；候补登记需要姓名、身份证、科室、日期、时段、医生（可选）。");
        docs.add("检查检验预约流程：先判断检查时段容量，再创建检查预约记录；支持取消检查预约。");
        docs.add("就诊前准备流程：根据科室或检查项目返回准备事项、饮食要求、到院时间和风险提示。");
        docs.add("复诊预约流程：先创建复诊计划，再按计划确认预约；确认成功后把计划状态更新为已预约。");

        LambdaQueryWrapper<VisitPreparation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitPreparation::getEnabled, 1);
        List<VisitPreparation> guides = visitPreparationMapper.selectList(wrapper);
        for (VisitPreparation guide : guides) {
            docs.add(buildGuideDocument(guide));
        }

        int parent = 0;
        for (String text : docs) {
            addDocument(text, "workflow", "workflow-" + (++parent));
        }
        return docs.size();
    }

    @Override
    public int ingestProjectKnowledge() {
        List<String> docs = new ArrayList<>();
        docs.add("项目定位：本系统是医院门诊场景的 AI 助手，提供预约挂号、改约、退号、检查预约、复诊计划、分诊导诊、费用预估、用药安全和报告解读。");
        docs.add("接口约定：核心业务接口前缀为 /api，智能对话接口为 /xiaohan/chat。生产环境建议通过网关统一鉴权与限流，保留 traceId 便于问题追踪。");
        docs.add("RAG 架构：知识入库时将文本切片向量化并写入 EmbeddingStore；检索时通过 EmbeddingStoreContentRetriever 返回 topK=3 且 minScore=0.75 的内容片段。");
        docs.add("向量存储策略：当配置了 PINECONE_API_KEY 时使用 Pinecone；未配置时退化为 InMemoryEmbeddingStore，仅适合本地调试且重启后丢失。");
        docs.add("可观测性指标：系统暴露 app.chat.requests、app.chat.latency、app.tool.calls、app.rag.requests、app.rag.recall.rate 等指标，可用于评估成功率与召回质量。");
        docs.add("知识更新流程：可调用 POST /api/knowledge/ingest-workflow 同步业务流程知识，调用 POST /api/knowledge/ingest-project-docs 同步项目说明知识。");

        int parent = 0;
        for (String text : docs) {
            addDocument(text, "project", "project-" + (++parent));
        }
        return docs.size();
    }

    @Override
    public void ingestPreparationGuide(VisitPreparation guide) {
        if (guide == null || guide.getEnabled() == null || guide.getEnabled() != 1) {
            return;
        }
        addDocument(buildGuideDocument(guide), "preparation", "preparation-" + guide.getId());
    }

    private String buildGuideDocument(VisitPreparation guide) {
        return "就诊前准备知识：科室=" + safe(guide.getDepartment())
                + "，检查项目=" + safe(guide.getExamType())
                + "，准备内容=" + safe(guide.getPrepContent())
                + "，风险提示=" + safe(guide.getRiskNotice());
    }

    private void addDocument(String text, String source, String parentId) {
        List<String> chunks = semanticChunks(text, 220, 45);
        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = new Metadata().put("source", source).put("parentId", parentId).put("chunkIndex", i);
            TextSegment segment = TextSegment.from(chunks.get(i), metadata);
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            String id = embeddingStore.add(embedding, segment);
            knowledgeIndex.index(segment, embedding);
            chromaVectorStoreClient.upsert(id, segment, embedding);
        }
    }

    /** Split on semantic punctuation first, then apply a small overlap to preserve workflow context. */
    private List<String> semanticChunks(String text, int maxChars, int overlapChars) {
        if (text == null || text.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？；.!?])")) {
            if (current.length() > 0 && current.length() + sentence.length() > maxChars) {
                chunks.add(current.toString().trim());
                String overlap = current.substring(Math.max(0, current.length() - overlapChars));
                current = new StringBuilder(overlap);
            }
            current.append(sentence);
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
