package com.atguigu.java.ai.langchain4j.skill;

import org.springframework.stereotype.Component;

@Component
public class SkillConflictResolver {

    public String resolveActionConflict(SkillDefinition skill, String userMessage) {
        if (skill == null || userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (!"appointment_workflow".equalsIgnoreCase(skill.skillId())) {
            return null;
        }
        String normalized = userMessage.toLowerCase();
        boolean hasCancel = normalized.contains("取消") || normalized.contains("去不了") || normalized.contains("不去了");
        boolean hasReschedule = normalized.contains("改约") || normalized.contains("改到") || normalized.contains("改期")
                || normalized.contains("换时间") || normalized.contains("换到");

        if (hasCancel && hasReschedule) {
            return "你这句话同时像“取消预约”和“改约”。请确认你要哪一种：1) 取消 2) 改约到新时间。";
        }
        return null;
    }
}
