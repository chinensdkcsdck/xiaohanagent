package com.atguigu.java.ai.langchain4j.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService(
        chatModel = "qwenChatModel",
        chatMemoryProvider = "chatMemoryProviderXiaohan",
        tools = {"AppointmentTools", "AdvancedMedicalTools"},
        contentRetriever = "contentRetrieverXiaohanPinecone"
)
public interface XiaohanAgent {

    @SystemMessage(fromResource = "xiaohan-prompt.txt")
    String chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
