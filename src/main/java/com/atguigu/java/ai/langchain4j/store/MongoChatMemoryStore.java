package com.atguigu.java.ai.langchain4j.store;

import com.atguigu.java.ai.langchain4j.bean.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Query query = new Query(Criteria.where("memoryId").is(toLong(memoryId)));
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null) {
            return new LinkedList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(chatMessages.getContent());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long id = toLong(memoryId);
        Query query = new Query(Criteria.where("memoryId").is(id));
        Update update = new Update();
        update.set("memoryId", id);
        update.set("content", ChatMessageSerializer.messagesToJson(messages));
        mongoTemplate.upsert(query, update, ChatMessages.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Query query = new Query(Criteria.where("memoryId").is(toLong(memoryId)));
        mongoTemplate.remove(query, ChatMessages.class);
    }

    private Long toLong(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        if (memoryId instanceof Long value) {
            return value;
        }
        return Long.parseLong(String.valueOf(memoryId));
    }
}

