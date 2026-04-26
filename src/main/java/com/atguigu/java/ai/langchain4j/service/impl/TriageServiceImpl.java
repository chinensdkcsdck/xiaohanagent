package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.TriageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TriageServiceImpl implements TriageService {

    @Override
    public String assess(String symptoms, Integer age, String chronicDiseases) {
        String symptomText = safe(symptoms).toLowerCase();
        if (symptomText.contains("胸痛") || symptomText.contains("呼吸困难") || symptomText.contains("昏迷")) {
            return "分诊结果：高风险。建议立即前往急诊或拨打120，不建议线上等待。";
        }
        if (symptomText.contains("发热") || symptomText.contains("咳嗽") || symptomText.contains("咽痛")) {
            return "分诊建议：优先呼吸内科/感染科；如体温持续>=39℃超过24小时，建议线下就医。";
        }
        if (symptomText.contains("头痛") || symptomText.contains("眩晕")) {
            return "分诊建议：神经内科；若出现肢体无力、言语不清，请立刻急诊。";
        }
        String chronic = StringUtils.hasText(chronicDiseases) ? (" 慢病史：" + chronicDiseases + "。") : "";
        String ageTip = age != null && age >= 65 ? " 老年患者建议优先线下评估。" : "";
        return "分诊建议：可先预约全科门诊进行初诊，再做专科分流。" + chronic + ageTip;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}

