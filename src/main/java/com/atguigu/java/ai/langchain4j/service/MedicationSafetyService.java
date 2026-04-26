package com.atguigu.java.ai.langchain4j.service;

public interface MedicationSafetyService {

    String check(String medications, String allergies, String conditions);
}

