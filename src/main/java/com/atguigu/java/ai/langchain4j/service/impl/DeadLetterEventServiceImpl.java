package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.MqDeadLetterEvent;
import com.atguigu.java.ai.langchain4j.mapper.MqDeadLetterEventMapper;
import com.atguigu.java.ai.langchain4j.service.DeadLetterEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeadLetterEventServiceImpl implements DeadLetterEventService {

    private final MqDeadLetterEventMapper mqDeadLetterEventMapper;

    public DeadLetterEventServiceImpl(MqDeadLetterEventMapper mqDeadLetterEventMapper) {
        this.mqDeadLetterEventMapper = mqDeadLetterEventMapper;
    }

    @Override
    public List<MqDeadLetterEvent> recent(int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        return mqDeadLetterEventMapper.selectList(
                new LambdaQueryWrapper<MqDeadLetterEvent>()
                        .orderByDesc(MqDeadLetterEvent::getId)
                        .last("limit " + safeLimit)
        );
    }
}
