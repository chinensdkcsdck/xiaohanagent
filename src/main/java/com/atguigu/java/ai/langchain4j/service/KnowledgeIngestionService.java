package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.VisitPreparation;

public interface KnowledgeIngestionService {

    int ingestWorkflowKnowledge();

    int ingestProjectKnowledge();

    void ingestPreparationGuide(VisitPreparation guide);
}
