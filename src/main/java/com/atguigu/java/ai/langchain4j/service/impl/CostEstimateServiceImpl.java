package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.CostEstimateService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CostEstimateServiceImpl implements CostEstimateService {

    @Override
    public String estimate(String department, String examType, String insuranceType) {
        int base = 80;
        if (StringUtils.hasText(examType)) {
            String t = examType.toLowerCase();
            if (t.contains("mri") || t.contains("磁共振")) {
                base += 700;
            } else if (t.contains("ct")) {
                base += 400;
            } else if (t.contains("超声")) {
                base += 180;
            } else {
                base += 120;
            }
        }
        double ratio = 0.0;
        if (StringUtils.hasText(insuranceType)) {
            String i = insuranceType.toLowerCase();
            if (i.contains("职工")) {
                ratio = 0.65;
            } else if (i.contains("居民")) {
                ratio = 0.50;
            }
        }
        int selfPay = (int) Math.round(base * (1 - ratio));
        return "费用预估：总费用约 " + base + " 元，医保类型[" +
                (StringUtils.hasText(insuranceType) ? insuranceType : "未提供") +
                "]，预计自付约 " + selfPay + " 元。实际金额以医院结算为准。";
    }
}

