package com.atguigu.java.ai.langchain4j.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Splits a combined natural-language request into ordered business tasks. */
@Component
public class Planner {

    public TypedState plan(String message, TypedState previous) {
        String text = message == null ? "" : message;
        List<String> tasks = new ArrayList<>();
        boolean symptom = text.contains("胸闷") || text.contains("心慌") || text.contains("头晕")
                || text.contains("咳嗽") || text.contains("发热");
        boolean appointment = text.contains("预约") || text.contains("挂号") || text.contains("号");
        if (symptom && appointment) {
            tasks.add("triage");
            tasks.add("appointment");
        } else if (appointment) {
            tasks.add("appointment");
        } else {
            tasks.add("knowledge");
        }
        String current = previous != null && !previous.taskStatus().equals("succeeded")
                && tasks.contains(previous.currentTask()) ? previous.currentTask() : tasks.get(0);
        return new TypedState(current, previous == null ? "" : previous.skillId(), tasks,
                previous == null ? java.util.Map.of() : previous.slots(),
                previous == null ? java.util.Map.of() : previous.toolResults(), "running");
    }
}
