package com.atguigu.java.ai.langchain4j.service;

public interface CostEstimateService {

    String estimate(String department, String examType, String insuranceType);
}

