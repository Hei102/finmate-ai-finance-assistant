package com.zth.java.ai.langchain4j.bean;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 聊天请求参数：携带用户消息和可选的会话 ID。 */
@Data
public class ChatForm {

    private Long chatSessionId;

    @NotBlank(message = "请输入聊天内容")
    private String message;
}
