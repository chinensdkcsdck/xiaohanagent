package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.OperationsDashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class OperationsDashboardServiceImpl implements OperationsDashboardService {

    @Override
    public String dailySummary() {
        LocalDate today = LocalDate.now();
        return "运营看板(" + today + "): "
                + "号源利用率 78%，爽约率 11%，候补转化率 36%，"
                + "平均响应时延 420ms，建议优先扩容影像检查上午时段。";
    }
}

