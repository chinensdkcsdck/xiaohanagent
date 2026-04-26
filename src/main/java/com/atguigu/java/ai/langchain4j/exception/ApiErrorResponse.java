package com.atguigu.java.ai.langchain4j.exception;

import java.time.LocalDateTime;
import java.util.Map;

public class ApiErrorResponse {
    private int code;
    private String message;
    private String traceId;
    private LocalDateTime timestamp;
    private Map<String, String> errors;

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setCode(errorCode.code());
        response.setMessage(message == null ? errorCode.message() : message);
        response.setTraceId(traceId);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}
