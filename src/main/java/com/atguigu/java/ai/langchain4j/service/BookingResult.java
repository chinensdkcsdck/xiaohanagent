package com.atguigu.java.ai.langchain4j.service;

public record BookingResult(boolean success, String message) {

    public static BookingResult ok(String message) {
        return new BookingResult(true, message);
    }

    public static BookingResult fail(String message) {
        return new BookingResult(false, message);
    }
}
