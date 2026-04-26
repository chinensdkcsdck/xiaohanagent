package com.atguigu.java.ai.langchain4j.bean;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("chat_messages")
public class ChatMessages {

    @Id
    private ObjectId messageId;

    private Long memoryId;

    private String content;

    public ChatMessages() {
    }

    public ChatMessages(ObjectId messageId, Long memoryId, String content) {
        this.messageId = messageId;
        this.memoryId = memoryId;
        this.content = content;
    }

    public ObjectId getMessageId() {
        return messageId;
    }

    public void setMessageId(ObjectId messageId) {
        this.messageId = messageId;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

