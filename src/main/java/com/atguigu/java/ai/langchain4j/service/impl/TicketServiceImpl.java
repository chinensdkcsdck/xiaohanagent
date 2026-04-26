package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TicketServiceImpl implements TicketService {

    private static final AtomicLong COUNTER = new AtomicLong(1000);

    @Override
    public String create(String username, String category, String content, String priority) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(category) || !StringUtils.hasText(content)) {
            return "建单失败：username/category/content 不能为空。";
        }
        long id = COUNTER.incrementAndGet();
        String p = StringUtils.hasText(priority) ? priority.toLowerCase(Locale.ROOT) : "normal";
        return "工单创建成功：TICKET-" + id
                + "，类别=" + category
                + "，优先级=" + p
                + "，请在工单中心继续跟踪。";
    }
}

