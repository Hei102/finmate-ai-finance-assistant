package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 聊天结果响应：提供会话信息、模型回复和回复时间。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private Long chatSessionId;
    private Long memoryId;
    private String chatSessionTitle;
    private String answer;
    private LocalDateTime createdAt;
}
