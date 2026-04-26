package com.atguigu.java.ai.langchain4j.service;

public interface ExamJourneyService {

    String buildPreCheckReminder(String examType, String appointmentTime);
}

