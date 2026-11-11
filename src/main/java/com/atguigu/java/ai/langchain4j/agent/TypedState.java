package com.atguigu.java.ai.langchain4j.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit workflow state kept separate from the natural-language chat memory. */
public record TypedState(
        String currentTask,
        String skillId,
        List<String> pendingTasks,
        Map<String, String> slots,
        Map<String, String> toolResults,
        String taskStatus) {

    public TypedState {
        pendingTasks = pendingTasks == null ? List.of() : List.copyOf(pendingTasks);
        slots = slots == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(slots));
        toolResults = toolResults == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(toolResults));
        taskStatus = taskStatus == null ? "pending" : taskStatus;
    }

    public static TypedState initial() {
        return new TypedState("", "", List.of(), Map.of(), Map.of(), "pending");
    }
}
