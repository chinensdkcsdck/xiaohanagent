package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.assistant.AdvancedSkillAgent;
import com.atguigu.java.ai.langchain4j.assistant.AppointmentSkillAgent;
import com.atguigu.java.ai.langchain4j.assistant.GeneralSkillAgent;
import com.atguigu.java.ai.langchain4j.config.ToolInvocationTracker;
import com.atguigu.java.ai.langchain4j.skill.SkillConflictResolver;
import com.atguigu.java.ai.langchain4j.skill.SkillDefinition;
import com.atguigu.java.ai.langchain4j.skill.SkillMatchResult;
import com.atguigu.java.ai.langchain4j.skill.SkillMatcher;
import com.atguigu.java.ai.langchain4j.skill.SlotCheckResult;
import com.atguigu.java.ai.langchain4j.skill.SlotChecker;
import com.atguigu.java.ai.langchain4j.skill.SlotStateStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillOrchestratorService {

    private static final int MATCH_CONFLICT_DELTA = 2;
    private static final String EMERGENCY_REPLY = "当前描述疑似急危重症，请立即线下急诊或拨打120。"
            + "我可以继续帮你整理就医信息（症状起始时间、既往病史、当前用药）供医生快速判断。";

    @Autowired
    private SkillMatcher skillMatcher;
    @Autowired
    private SkillConflictResolver skillConflictResolver;
    @Autowired
    private SlotStateStore slotStateStore;
    @Autowired
    private AppointmentSkillAgent appointmentSkillAgent;
    @Autowired
    private AdvancedSkillAgent advancedSkillAgent;
    @Autowired
    private GeneralSkillAgent generalSkillAgent;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired(required = false)
    private List<SlotChecker> slotCheckers = List.of();

    public String chat(Long memoryId, String userMessage) {
        if (isEmergency(userMessage)) {
            Counter.builder("app.skill.emergency.escalate")
                    .register(meterRegistry)
                    .increment();
            return EMERGENCY_REPLY;
        }

        List<SkillMatchResult> ranked = skillMatcher.rank(userMessage);
        if (ranked.isEmpty()) {
            Counter.builder("app.skill.fallback")
                    .tag("reason", "no_match")
                    .register(meterRegistry)
                    .increment();
            return generalSkillAgent.chat(memoryId, userMessage);
        }

        if (isMatchConflict(ranked)) {
            Counter.builder("app.skill.conflict.clarify")
                    .tag("reason", "close_scores")
                    .register(meterRegistry)
                    .increment();
            return "我需要先确认你的目标：你是想“预约挂号”，还是想“做分诊/报告解读/用药评估”？";
        }

        SkillDefinition skill = ranked.get(0).definition();
        Counter.builder("app.skill.match")
                .tag("skillId", skill.skillId())
                .tag("status", "matched")
                .register(meterRegistry)
                .increment();

        String actionConflict = skillConflictResolver.resolveActionConflict(skill, userMessage);
        if (actionConflict != null) {
            Counter.builder("app.skill.conflict.clarify")
                    .tag("reason", "action_conflict")
                    .register(meterRegistry)
                    .increment();
            return actionConflict;
        }

        SlotCheckResult slotCheckResult = checkSlots(memoryId, skill, userMessage);
        if (!slotCheckResult.ready()) {
            int askAttempts = slotStateStore.incrementAskAttempts(memoryId, skill.skillId());
            for (String slot : slotCheckResult.missingSlots()) {
                Counter.builder("app.skill.slot.missing")
                        .tag("skillId", skill.skillId())
                        .tag("slot", slot)
                        .register(meterRegistry)
                        .increment();
            }
            Counter.builder("app.skill.ask_slot")
                    .tag("skillId", skill.skillId())
                    .register(meterRegistry)
                    .increment();

            int maxAskTurns = skill.maxAskTurns() <= 0 ? 2 : skill.maxAskTurns();
            if (askAttempts > maxAskTurns) {
                Counter.builder("app.skill.ask_slot.exhausted")
                        .tag("skillId", skill.skillId())
                        .register(meterRegistry)
                        .increment();
                slotStateStore.resetAskAttempts(memoryId, skill.skillId());
                String fallback = hasText(skill.askFallbackReply())
                        ? skill.askFallbackReply()
                        : "为了避免反复追问，我先不直接执行。你可以回复“科室 + 日期 + 上午/下午”，例如“心内科 明天 上午”。";
                return fallback;
            }
            return slotCheckResult.askMessage();
        }

        slotStateStore.resetAskAttempts(memoryId, skill.skillId());
        String route = safeUpper(skill.routeAgent());
        String finalMessage = enrichMessage(memoryId, skill, userMessage);
        try {
            ToolInvocationTracker.setAllowedTools(skill.toolWhitelist());
            return switch (route) {
                case "APPOINTMENT" -> appointmentSkillAgent.chat(memoryId, finalMessage);
                case "ADVANCED" -> advancedSkillAgent.chat(memoryId, finalMessage);
                default -> generalSkillAgent.chat(memoryId, userMessage);
            };
        } finally {
            ToolInvocationTracker.clearAllowedTools();
        }
    }

    private SlotCheckResult checkSlots(Long memoryId, SkillDefinition skillDefinition, String userMessage) {
        if (skillDefinition.requiredSlots() == null || skillDefinition.requiredSlots().isEmpty()) {
            return SlotCheckResult.pass();
        }
        SlotChecker checker = resolveChecker(skillDefinition);
        if (checker == null) {
            return SlotCheckResult.pass();
        }
        Map<String, String> extracted = checker.extractSlots(userMessage);
        Map<String, String> collected = slotStateStore.mergeAndGet(memoryId, skillDefinition.skillId(), extracted);
        return checker.check(skillDefinition, collected);
    }

    private String enrichMessage(Long memoryId, SkillDefinition skill, String userMessage) {
        if (!"appointment_workflow".equalsIgnoreCase(skill.skillId())) {
            return userMessage;
        }
        SlotChecker checker = resolveChecker(skill);
        if (checker == null) {
            return userMessage;
        }
        Map<String, String> snapshot = slotStateStore.mergeAndGet(memoryId, skill.skillId(), Map.of());
        if (snapshot.isEmpty()) {
            return userMessage;
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        if (snapshot.containsKey("department")) {
            ordered.put("department", snapshot.get("department"));
        }
        if (snapshot.containsKey("date")) {
            ordered.put("date", snapshot.get("date"));
        }
        if (snapshot.containsKey("period")) {
            ordered.put("period", snapshot.get("period"));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("已确认预约槽位: ").append(ordered).append("\n");
        sb.append("用户本轮输入: ").append(userMessage);
        return sb.toString();
    }

    private SlotChecker resolveChecker(SkillDefinition skillDefinition) {
        for (SlotChecker checker : slotCheckers) {
            if (checker.supports(skillDefinition)) {
                return checker;
            }
        }
        return null;
    }

    private boolean isMatchConflict(List<SkillMatchResult> ranked) {
        if (ranked.size() < 2) {
            return false;
        }
        SkillMatchResult first = ranked.get(0);
        SkillMatchResult second = ranked.get(1);
        if (first.score() - second.score() > MATCH_CONFLICT_DELTA) {
            return false;
        }
        return !first.definition().skillId().equalsIgnoreCase(second.definition().skillId());
    }

    private boolean isEmergency(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = message.toLowerCase();
        return text.contains("胸痛")
                || text.contains("呼吸困难")
                || text.contains("喘不上气")
                || text.contains("昏迷")
                || text.contains("大出血")
                || text.contains("抽搐");
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
