package com.atguigu.java.ai.langchain4j.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public class ChatForm {

    private Long memoryId;

    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message must be <= 2000 characters")
    private String message;

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

