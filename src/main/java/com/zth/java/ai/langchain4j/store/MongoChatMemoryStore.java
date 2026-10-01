package com.zth.java.ai.langchain4j.store;

import com.zth.java.ai.langchain4j.bean.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

/** 把 LangChain4j 当前会话窗口序列化为 JSON，按会话 ID 保存到 MongoDB。 */
@Component
@RequiredArgsConstructor
public class MongoChatMemoryStore implements ChatMemoryStore {

    private final MongoTemplate mongoTemplate;

    /** 按会话 ID 读取并反序列化消息窗口。 */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // memoryId 使用 MySQL chat_sessions.id，同一个会话的上下文在 MongoDB 中只保存一份 JSON。
        Query query = new Query(Criteria.where("memoryId").is(normalizeMemoryId(memoryId)));
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null) {
            return new LinkedList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(chatMessages.getContent());
    }

    /** 覆盖保存框架传入的消息窗口，不额外累计完整历史。 */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String normalizedMemoryId = normalizeMemoryId(memoryId);
        Query query = new Query(Criteria.where("memoryId").is(normalizedMemoryId));
        // LangChain4j 已经负责把窗口裁剪到 maxMessages，这里直接覆盖当前会话记忆即可。
        Update update = new Update()
                .set("memoryId", normalizedMemoryId)
                .set("content", ChatMessageSerializer.messagesToJson(messages));
        mongoTemplate.upsert(query, update, ChatMessages.class);
    }

    /** 删除指定会话的 MongoDB 消息文档。 */
    @Override
    public void deleteMessages(Object memoryId) {
        Query query = new Query(Criteria.where("memoryId").is(normalizeMemoryId(memoryId)));
        mongoTemplate.remove(query, ChatMessages.class);
    }

    private String normalizeMemoryId(Object memoryId) {
        return String.valueOf(memoryId);
    }
}
