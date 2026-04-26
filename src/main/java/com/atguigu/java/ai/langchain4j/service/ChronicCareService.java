package com.atguigu.java.ai.langchain4j.service;

public interface ChronicCareService {

    String generateFollowupPlan(String diseaseType, String latestIndicators, String medicationStatus);
}

