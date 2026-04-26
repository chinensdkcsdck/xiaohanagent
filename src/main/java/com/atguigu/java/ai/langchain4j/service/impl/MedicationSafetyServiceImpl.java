package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.MedicationSafetyService;
import org.springframework.stereotype.Service;

@Service
public class MedicationSafetyServiceImpl implements MedicationSafetyService {

    @Override
    public String check(String medications, String allergies, String conditions) {
        String med = medications == null ? "" : medications.toLowerCase();
        boolean risk = (med.contains("阿司匹林") && med.contains("华法林"))
                || (med.contains("布洛芬") && med.contains("华法林"));
        StringBuilder sb = new StringBuilder("用药安全检查：");
        if (risk) {
            sb.append("发现潜在出血风险组合，请尽快与医生确认用药方案；");
        } else {
            sb.append("未发现明显高风险组合；");
        }
        if (allergies != null && !allergies.isBlank()) {
            sb.append(" 过敏史：").append(allergies).append("；");
        }
        if (conditions != null && !conditions.isBlank()) {
            sb.append(" 既往病史：").append(conditions).append("。");
        }
        sb.append(" 本结果仅作初筛，不替代医生处方。");
        return sb.toString();
    }
}

