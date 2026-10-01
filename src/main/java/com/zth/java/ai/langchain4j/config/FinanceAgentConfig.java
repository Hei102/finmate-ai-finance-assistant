package com.zth.java.ai.langchain4j.config;

import com.zth.java.ai.langchain4j.store.MongoChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 配置每个会话独立的消息窗口，并将窗口内容持久化到 MongoDB。 */
@Configuration
@RequiredArgsConstructor
public class FinanceAgentConfig {

    private final MongoChatMemoryStore mongoChatMemoryStore;

    @Bean
    ChatMemoryProvider chatMemoryProvider_finance() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                // 这里是“给大模型看的上下文窗口”，完整会话索引在 MySQL，消息内容落在 MongoDB。
                .maxMessages(20)
                .chatMemoryStore(mongoChatMemoryStore)
                .build();
    }
}
