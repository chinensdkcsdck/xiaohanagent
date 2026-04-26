package com.atguigu.java.ai.langchain4j.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class SkillRegistry {

    private List<SkillDefinition> skills = List.of();

    @PostConstruct
    public void init() {
        List<SkillDefinition> loaded = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Yaml yaml = new Yaml();
        try {
            Resource[] resources = resolver.getResources("classpath*:skills/*.yaml");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    Object parsed = yaml.load(in);
                    if (parsed instanceof Map<?, ?> root) {
                        SkillDefinition definition = toDefinition(root);
                        if (definition != null) {
                            loaded.add(definition);
                        }
                    }
                }
            }
            loaded.sort(Comparator.comparingInt(SkillDefinition::priority).reversed());
            this.skills = Collections.unmodifiableList(loaded);
        } catch (Exception ignored) {
            this.skills = List.of();
        }
    }

    public List<SkillDefinition> all() {
        return skills;
    }

    @SuppressWarnings("unchecked")
    private SkillDefinition toDefinition(Map<?, ?> root) {
        String skillId = asString(root.get("skillId"));
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return new SkillDefinition(
                skillId,
                asString(root.get("description")),
                asInt(root.get("priority"), 0),
                asString(root.get("routeAgent")),
                asInt(root.get("maxAskTurns"), 2),
                asString(root.get("askFallbackReply")),
                asStringList(root.get("intentExamples")),
                asStringList(root.get("keywords")),
                asStringList(root.get("negativeKeywords")),
                asStringList(root.get("requiredSlots")),
                asStringList(root.get("toolWhitelist")),
                asString(root.get("riskPolicy")),
                asString(root.get("fallbackReply"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return List.copyOf(out);
    }
}
