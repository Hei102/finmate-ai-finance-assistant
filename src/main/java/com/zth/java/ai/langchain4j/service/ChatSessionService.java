package com.zth.java.ai.langchain4j.service;

import com.zth.java.ai.langchain4j.dto.ChatMessageResponse;
import com.zth.java.ai.langchain4j.dto.ChatSessionResponse;
import com.zth.java.ai.langchain4j.entity.FinanceChatSession;
import com.zth.java.ai.langchain4j.repository.FinanceChatSessionRepository;
import com.zth.java.ai.langchain4j.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 管理 MySQL 会话目录、用户归属和标题；消息内容从 MongoDB 读取。 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final String DEFAULT_TITLE = "新的聊天";
    private static final int TITLE_LIMIT = 20;

    private final FinanceChatSessionRepository chatSessionRepository;
    private final MongoChatMemoryStore mongoChatMemoryStore;

    /** 按最近更新时间返回用户的会话列表。 */
    public List<ChatSessionResponse> list(Long userId) {
        return chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 创建会话目录并设置默认标题和时间。 */
    public FinanceChatSession create(Long userId, String title) {
        LocalDateTime now = LocalDateTime.now();
        FinanceChatSession chatSession = new FinanceChatSession();
        chatSession.setUserId(userId);
        chatSession.setTitle(normalizeTitle(title));
        chatSession.setCreatedAt(now);
        chatSession.setUpdatedAt(now);
        return chatSessionRepository.save(chatSession);
    }

    /** 取得已有会话或新建会话，并用首条消息补充默认标题。 */
    public FinanceChatSession resolveForMessage(Long userId, Long chatSessionId, String message) {
        FinanceChatSession chatSession = chatSessionId == null
                ? create(userId, normalizeTitle(message))
                : requireOwned(userId, chatSessionId);
        if (DEFAULT_TITLE.equals(chatSession.getTitle())) {
            chatSession.setTitle(normalizeTitle(message));
        }
        chatSession.setUpdatedAt(LocalDateTime.now());
        return chatSessionRepository.save(chatSession);
    }

    /** 确认会话属于当前用户，防止通过 ID 读取他人会话。 */
    public FinanceChatSession requireOwned(Long userId, Long chatSessionId) {
        return chatSessionRepository.findByIdAndUserId(chatSessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("聊天会话不存在或不属于当前账号"));
    }

    /** 读取消息窗口，仅返回可展示的用户消息和 AI 文本。 */
    public List<ChatMessageResponse> listMessages(Long userId, Long chatSessionId) {
        FinanceChatSession chatSession = requireOwned(userId, chatSessionId);
        return mongoChatMemoryStore.getMessages(chatSession.getId()).stream()
                .map(this::toMessageResponse)
                .flatMap(Optional::stream)
                .toList();
    }

    /** 更新会话时间，使最近使用的会话排在前面。 */
    public void touch(FinanceChatSession chatSession) {
        chatSession.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(chatSession);
    }

    /** 先删除 MongoDB 消息，再删除 MySQL 会话目录。 */
    public void delete(Long userId, Long chatSessionId) {
        FinanceChatSession chatSession = requireOwned(userId, chatSessionId);
        mongoChatMemoryStore.deleteMessages(chatSession.getId());
        chatSessionRepository.delete(chatSession);
    }

    /** 统计当前用户的会话数。 */
    public long count(Long userId) {
        return chatSessionRepository.countByUserId(userId);
    }

    /** 把数据库实体转换为前端会话摘要。 */
    public ChatSessionResponse toResponse(FinanceChatSession chatSession) {
        return new ChatSessionResponse(
                chatSession.getId(),
                chatSession.getTitle(),
                chatSession.getCreatedAt(),
                chatSession.getUpdatedAt()
        );
    }

    /** 过滤系统消息和工具消息，只保留界面可展示的内容。 */
    private Optional<ChatMessageResponse> toMessageResponse(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            String content = userMessage.hasSingleText() ? userMessage.singleText() : userMessage.toString();
            return Optional.of(new ChatMessageResponse("user", content));
        }
        if (message instanceof AiMessage aiMessage && aiMessage.text() != null && !aiMessage.text().isBlank()) {
            return Optional.of(new ChatMessageResponse("agent", aiMessage.text()));
        }
        return Optional.empty();
    }

    /** 统一处理手动标题和首条消息：去多余空白、补默认值、限制长度。 */
    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        return trimToLimit(title.trim().replaceAll("\\s+", " "));
    }

    private String trimToLimit(String value) {
        if (value.length() <= TITLE_LIMIT) {
            return value;
        }
        return value.substring(0, TITLE_LIMIT) + "...";
    }
}
