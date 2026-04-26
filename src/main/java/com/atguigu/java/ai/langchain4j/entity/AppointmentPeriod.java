package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AppointmentPeriod {
    AM("上午"),
    PM("下午");

    @EnumValue
    private final String label;

    AppointmentPeriod(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static AppointmentPeriod from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("预约时间不能为空，支持：上午/下午");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("预约时间不能为空，支持：上午/下午");
        }

        if ("上午".equals(normalized)
                || "上".equals(normalized)
                || "am".equalsIgnoreCase(normalized)
                || "AM".equals(normalized)
                || "涓婂崍".equals(normalized)) {
            return AM;
        }

        if ("下午".equals(normalized)
                || "下".equals(normalized)
                || "pm".equalsIgnoreCase(normalized)
                || "PM".equals(normalized)
                || "涓嬪崍".equals(normalized)) {
            return PM;
        }

        throw new IllegalArgumentException("预约时间仅支持：上午/下午（或 AM/PM）");
    }
}