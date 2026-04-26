package com.atguigu.java.ai.langchain4j.service;

public interface TriageService {

    String assess(String symptoms, Integer age, String chronicDiseases);
}

