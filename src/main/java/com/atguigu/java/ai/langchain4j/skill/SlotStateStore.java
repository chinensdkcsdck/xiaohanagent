package com.atguigu.java.ai.langchain4j.skill;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlotStateStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Map<Long, Map<String, SlotState>> state = new ConcurrentHashMap<>();

    public Map<String, String> mergeAndGet(Long memoryId, String skillId, Map<String, String> extractedSlots) {
        SlotState slotState = getSlotState(memoryId, skillId);
        if (extractedSlots != null && !extractedSlots.isEmpty()) {
            slotState.slots.putAll(extractedSlots);
        }
        slotState.updatedAt = Instant.now();
        return Map.copyOf(slotState.slots);
    }

    public int incrementAskAttempts(Long memoryId, String skillId) {
        SlotState slotState = getSlotState(memoryId, skillId);
        slotState.askAttempts++;
        slotState.updatedAt = Instant.now();
        return slotState.askAttempts;
    }

    public void resetAskAttempts(Long memoryId, String skillId) {
        SlotState slotState = getSlotState(memoryId, skillId);
        slotState.askAttempts = 0;
        slotState.updatedAt = Instant.now();
    }

    public void clearSkill(Long memoryId, String skillId) {
        if (memoryId == null || skillId == null) {
            return;
        }
        Map<String, SlotState> skillMap = state.get(memoryId);
        if (skillMap == null) {
            return;
        }
        skillMap.remove(skillId);
        if (skillMap.isEmpty()) {
            state.remove(memoryId);
        }
    }

    private SlotState getSlotState(Long memoryId, String skillId) {
        Long key = memoryId == null ? -1L : memoryId;
        String normalizedSkill = (skillId == null || skillId.isBlank()) ? "default" : skillId;
        Map<String, SlotState> bySkill = state.computeIfAbsent(key, unused -> new ConcurrentHashMap<>());
        SlotState slotState = bySkill.computeIfAbsent(normalizedSkill, unused -> new SlotState());
        if (isExpired(slotState)) {
            bySkill.put(normalizedSkill, new SlotState());
            slotState = bySkill.get(normalizedSkill);
        }
        return slotState;
    }

    private boolean isExpired(SlotState slotState) {
        return slotState.updatedAt.plus(DEFAULT_TTL).isBefore(Instant.now());
    }

    private static class SlotState {
        private final Map<String, String> slots = new HashMap<>();
        private int askAttempts = 0;
        private Instant updatedAt = Instant.now();
    }
}
