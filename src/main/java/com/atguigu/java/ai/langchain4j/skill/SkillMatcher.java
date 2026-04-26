package com.atguigu.java.ai.langchain4j.skill;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class SkillMatcher {

    private static final int KEYWORD_WEIGHT = 4;
    private static final int INTENT_WEIGHT = 2;
    private static final int NEGATIVE_WEIGHT = -5;
    private static final int MAX_KEYWORD_HITS = 3;
    private static final int MAX_INTENT_HITS = 2;
    private static final int MAX_NEGATIVE_HITS = 2;
    private static final int MIN_TOKEN_LENGTH = 2;

    @Autowired
    private SkillRegistry skillRegistry;

    public SkillMatchResult match(String message) {
        List<SkillMatchResult> ranked = rank(message);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    public List<SkillMatchResult> rank(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SkillMatchResult> ranked = new ArrayList<>();
        skillRegistry.all().forEach(skill -> {
            int score = score(skill, normalized);
            if (score > 0) {
                ranked.add(new SkillMatchResult(skill, score));
            }
        });
        ranked.sort(Comparator.comparingInt(SkillMatchResult::score)
                .thenComparing(result -> result.definition().priority())
                .reversed());
        return ranked;
    }

    private int score(SkillDefinition skill, String message) {
        int keywordHits = 0;
        for (String keyword : skill.keywords()) {
            if (contains(message, keyword)) {
                keywordHits++;
            }
        }

        int intentHits = 0;
        for (String sample : skill.intentExamples()) {
            if (contains(message, sample)) {
                intentHits++;
            }
        }

        int negativeHits = 0;
        for (String negative : skill.negativeKeywords()) {
            if (contains(message, negative)) {
                negativeHits++;
            }
        }

        int boundedKeywordHits = Math.min(keywordHits, MAX_KEYWORD_HITS);
        int boundedIntentHits = Math.min(intentHits, MAX_INTENT_HITS);
        int boundedNegativeHits = Math.min(negativeHits, MAX_NEGATIVE_HITS);

        int total = boundedKeywordHits * KEYWORD_WEIGHT
                + boundedIntentHits * INTENT_WEIGHT
                + boundedNegativeHits * NEGATIVE_WEIGHT;
        return Math.max(0, total);
    }

    private boolean contains(String message, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT);
        if (normalizedToken.length() < MIN_TOKEN_LENGTH) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(normalizedToken);
    }
}
