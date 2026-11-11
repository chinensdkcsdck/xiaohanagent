package com.atguigu.java.ai.langchain4j.skill;

import org.springframework.stereotype.Component;

import java.util.List;

/** Named routing boundary between task planning and a concrete Skill agent. */
@Component
public class SkillRouter {
    private final SkillMatcher matcher;

    public SkillRouter(SkillMatcher matcher) {
        this.matcher = matcher;
    }

    public List<SkillMatchResult> rank(String message) {
        return matcher.rank(message);
    }
}
