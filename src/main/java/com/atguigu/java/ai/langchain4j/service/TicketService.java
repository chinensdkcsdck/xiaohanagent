package com.atguigu.java.ai.langchain4j.service;

public interface TicketService {

    String create(String username, String category, String content, String priority);
}

