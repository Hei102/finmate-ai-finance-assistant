package com.zth.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 市场追问请求：携带问题、标的和当前页面上下文。 */
@Data
public class MarketChatRequest {

    @NotBlank(message = "请输入要追问的问题")
    private String question;

    private String symbol;

    private MarketTodayResponse context;
}
