package com.atguigu.java.ai.langchain4j.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ToolInvocationTracker {

    private static final ThreadLocal<Integer> TOOL_CALLS = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<List<String>> INVOKED_TOOLS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Set<String>> ALLOWED_TOOLS = new ThreadLocal<>();

    private ToolInvocationTracker() {
    }

    public static void reset() {
        TOOL_CALLS.set(0);
        INVOKED_TOOLS.set(new ArrayList<>());
        ALLOWED_TOOLS.remove();
    }

    public static void markInvoked(String toolName) {
        Set<String> allowed = ALLOWED_TOOLS.get();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(toolName)) {
            throw new IllegalStateException("Tool invocation is not allowed for current skill: " + toolName);
        }
        TOOL_CALLS.set(TOOL_CALLS.get() + 1);
        INVOKED_TOOLS.get().add(toolName);
    }

    public static int currentCalls() {
        return TOOL_CALLS.get();
    }

    public static List<String> invokedTools() {
        return Collections.unmodifiableList(new ArrayList<>(INVOKED_TOOLS.get()));
    }

    public static void setAllowedTools(Collection<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            ALLOWED_TOOLS.remove();
            return;
        }
        ALLOWED_TOOLS.set(new HashSet<>(allowedTools));
    }

    public static void clearAllowedTools() {
        ALLOWED_TOOLS.remove();
    }

    public static void clear() {
        TOOL_CALLS.remove();
        INVOKED_TOOLS.remove();
        ALLOWED_TOOLS.remove();
    }
}
