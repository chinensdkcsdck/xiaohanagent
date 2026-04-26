package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.ChronicCareService;
import org.springframework.stereotype.Service;

@Service
public class ChronicCareServiceImpl implements ChronicCareService {

    @Override
    public String generateFollowupPlan(String diseaseType, String latestIndicators, String medicationStatus) {
        String type = diseaseType == null ? "慢病" : diseaseType.trim();
        return "慢病随访计划(" + type + ")："
                + "1) 2周内复测关键指标；"
                + "2) 4周内门诊复诊评估治疗方案；"
                + "3) 每日记录用药与症状变化。"
                + " 当前指标：" + (latestIndicators == null ? "未提供" : latestIndicators)
                + "；用药情况：" + (medicationStatus == null ? "未提供" : medicationStatus) + "。";
    }
}

