package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.VisitPreparation;

import java.util.List;

public interface PreparationService {

    String queryPreparationGuide(String department, String examType);

    Long upsertPreparationGuide(String department, String examType, String prepContent, String riskNotice);

    List<VisitPreparation> listEnabledGuides();
}
