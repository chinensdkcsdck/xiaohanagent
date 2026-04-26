package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.service.ExamJourneyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExamJourneyServiceImpl implements ExamJourneyService {

    @Override
    public String buildPreCheckReminder(String examType, String appointmentTime) {
        String type = StringUtils.hasText(examType) ? examType.trim() : "常规检查";
        StringBuilder sb = new StringBuilder();
        sb.append("检查前提醒（").append(type).append("）：");
        sb.append("1) 请携带身份证、就诊卡、既往报告；");
        if (type.contains("MRI") || type.contains("磁共振")) {
            sb.append("2) 去除金属物品，体内有金属植入请提前告知；");
        } else if (type.contains("CT")) {
            sb.append("2) 如需增强CT，请确认是否有碘造影剂过敏史；");
        } else if (type.contains("腹部") || type.contains("血")) {
            sb.append("2) 检查前8小时空腹，少量饮水可接受；");
        } else {
            sb.append("2) 遵医嘱准备，提前30分钟到院；");
        }
        if (StringUtils.hasText(appointmentTime)) {
            sb.append("3) 预约时间：").append(appointmentTime.trim()).append("。");
        }
        return sb.toString();
    }
}

