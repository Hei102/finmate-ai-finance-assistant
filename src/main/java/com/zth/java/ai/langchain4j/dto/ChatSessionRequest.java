package com.zth.java.ai.langchain4j.dto;

import lombok.Data;

/** 新建会话请求：允许指定会话标题。 */
@Data
public class ChatSessionRequest {

    private String title;
}
