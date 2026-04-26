package com.atguigu.java.ai.langchain4j.skill;

import java.util.Map;

public interface SlotChecker {

    boolean supports(SkillDefinition skillDefinition);

    Map<String, String> extractSlots(String userMessage);

    SlotCheckResult check(SkillDefinition skillDefinition, Map<String, String> collectedSlots);
}
