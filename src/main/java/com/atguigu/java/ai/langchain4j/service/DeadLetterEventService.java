package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.MqDeadLetterEvent;

import java.util.List;

public interface DeadLetterEventService {

    List<MqDeadLetterEvent> recent(int limit);
}
