package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 会话目录响应：用于前端会话列表展示。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {

    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
