package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 聊天消息响应：提供角色和内容，用于恢复聊天界面。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private String role;
    private String content;
}
