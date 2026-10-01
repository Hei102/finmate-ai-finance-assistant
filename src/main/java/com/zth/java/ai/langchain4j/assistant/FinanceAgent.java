package com.zth.java.ai.langchain4j.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

/** LangChain4j 对话入口：组合系统提示词、会话记忆和财经工具，由框架生成实现。 */
@AiService(
        wiringMode = EXPLICIT,
        chatModel = "openAiChatModel",
        chatMemoryProvider = "chatMemoryProvider_finance",
        tools = "financeTools"
)
public interface FinanceAgent {

    @SystemMessage(fromResource = "finance-agent-prompt.txt")
    String chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
