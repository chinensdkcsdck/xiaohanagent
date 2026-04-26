package com.atguigu.java.ai.langchain4j.skill;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AppointmentSlotChecker implements SlotChecker {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b");
    private static final List<String> SLOT_DEPENDENCY_ORDER = List.of("department", "date", "period");

    private static final Map<String, String> DEPARTMENT_SYNONYMS = Map.ofEntries(
            Map.entry("内科", "内科"),
            Map.entry("心内科", "心内科"),
            Map.entry("消化科", "消化科"),
            Map.entry("呼吸科", "呼吸科"),
            Map.entry("神经内科", "神经内科"),
            Map.entry("外科", "外科"),
            Map.entry("骨科", "骨科"),
            Map.entry("儿科", "儿科"),
            Map.entry("妇科", "妇科"),
            Map.entry("眼科", "眼科"),
            Map.entry("口腔科", "口腔科"),
            Map.entry("皮肤科", "皮肤科"),
            Map.entry("耳鼻喉", "耳鼻喉"),
            Map.entry("中医科", "中医科")
    );

    @Override
    public boolean supports(SkillDefinition skillDefinition) {
        return skillDefinition != null && "appointment_workflow".equalsIgnoreCase(skillDefinition.skillId());
    }

    @Override
    public Map<String, String> extractSlots(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of();
        }
        String normalized = userMessage.trim();
        Map<String, String> extracted = new LinkedHashMap<>();

        String department = detectDepartment(normalized);
        if (department != null) {
            extracted.put("department", department);
        }

        String date = detectDate(normalized);
        if (date != null) {
            extracted.put("date", date);
        }

        String period = detectPeriod(normalized);
        if (period != null) {
            extracted.put("period", period);
        }

        return extracted;
    }

    @Override
    public SlotCheckResult check(SkillDefinition skillDefinition, Map<String, String> collectedSlots) {
        if (skillDefinition == null || skillDefinition.requiredSlots() == null || skillDefinition.requiredSlots().isEmpty()) {
            return SlotCheckResult.pass();
        }
        Map<String, String> slots = collectedSlots == null ? Map.of() : collectedSlots;
        List<String> missing = orderedMissing(skillDefinition.requiredSlots(), slots);
        if (missing.isEmpty()) {
            return SlotCheckResult.pass();
        }
        return SlotCheckResult.ask(missing, buildAskMessage(missing));
    }

    private List<String> orderedMissing(List<String> requiredSlots, Map<String, String> slots) {
        List<String> missing = new ArrayList<>();
        for (String orderedSlot : SLOT_DEPENDENCY_ORDER) {
            if (requiredSlots.contains(orderedSlot) && !hasText(slots.get(orderedSlot))) {
                missing.add(orderedSlot);
            }
        }
        for (String slot : requiredSlots) {
            if (!SLOT_DEPENDENCY_ORDER.contains(slot) && !hasText(slots.get(slot))) {
                missing.add(slot);
            }
        }
        return missing;
    }

    private String detectDepartment(String text) {
        for (Map.Entry<String, String> entry : DEPARTMENT_SYNONYMS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String detectDate(String text) {
        Matcher matcher = ISO_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        if (text.contains("明天")) {
            return "明天";
        }
        if (text.contains("后天")) {
            return "后天";
        }
        if (text.contains("今天")) {
            return "今天";
        }
        if (text.contains("下周")) {
            return "下周";
        }
        if (text.contains("本周")) {
            return "本周";
        }
        return null;
    }

    private String detectPeriod(String text) {
        if (text.contains("上午") || text.contains("am") || text.contains("AM") || text.contains("早上")) {
            return "上午";
        }
        if (text.contains("下午") || text.contains("pm") || text.contains("PM") || text.contains("中午")) {
            return "下午";
        }
        return null;
    }

    private String buildAskMessage(List<String> missingSlots) {
        String first = missingSlots.get(0);
        return switch (first) {
            case "department" -> "先确认科室：你想挂哪个科室？例如内科、儿科、心内科。";
            case "date" -> "先确认日期：你想预约哪天？可直接说“明天”或“2026-04-20”。";
            case "period" -> "先确认时段：你想预约上午还是下午？";
            default -> "还需要补充信息：" + String.join("、", missingSlots);
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
