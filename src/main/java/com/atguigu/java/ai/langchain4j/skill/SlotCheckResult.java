package com.atguigu.java.ai.langchain4j.skill;

import java.util.List;

public record SlotCheckResult(boolean ready, List<String> missingSlots, String askMessage) {

    public static SlotCheckResult pass() {
        return new SlotCheckResult(true, List.of(), null);
    }

    public static SlotCheckResult ask(List<String> missingSlots, String askMessage) {
        return new SlotCheckResult(false, List.copyOf(missingSlots), askMessage);
    }
}
