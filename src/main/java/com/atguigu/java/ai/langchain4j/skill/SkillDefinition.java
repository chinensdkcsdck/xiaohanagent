package com.atguigu.java.ai.langchain4j.skill;

import java.util.List;

public record SkillDefinition(
        String skillId,
        String description,
        int priority,
        String routeAgent,
        int maxAskTurns,
        String askFallbackReply,
        List<String> intentExamples,
        List<String> keywords,
        List<String> negativeKeywords,
        List<String> requiredSlots,
        List<String> toolWhitelist,
        String riskPolicy,
        String fallbackReply
) {
}
