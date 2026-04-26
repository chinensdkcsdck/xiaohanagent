package com.atguigu.java.ai.langchain4j.exception;

public enum ErrorCode {
    INVALID_PARAM(40001, "请求参数错误"),
    BIZ_ERROR(40002, "业务处理失败"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    TOOL_ERROR(50010, "工具调用失败"),
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}

