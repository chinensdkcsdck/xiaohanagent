package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.ReportInterpretationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReportInterpretationServiceImpl implements ReportInterpretationService {

    @Override
    public String explain(String reportText) {
        if (!StringUtils.hasText(reportText)) {
            return "请提供报告原文（至少包含检查项目和结论）。";
        }
        String text = reportText.toLowerCase();
        String level = "未见明显风险提示";
        if (text.contains("异常") || text.contains("增高") || text.contains("占位")) {
            level = "存在异常信号，建议尽快携带报告至对应科室复诊";
        }
        return "报告解读（患者友好版）："
                + "1) 结果概览：" + level + "；"
                + "2) 建议：结合症状与既往病史让医生综合判断；"
                + "3) 若出现急性症状（胸痛/呼吸困难/意识障碍）请立即急诊。";
    }
}

