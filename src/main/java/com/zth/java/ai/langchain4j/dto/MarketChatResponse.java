package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 市场追问响应：返回回答、上下文时间和回答来源。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketChatResponse {

    private String answer;
    private String contextSymbol;
    private LocalDateTime contextGeneratedAt;
    private String source;
}
