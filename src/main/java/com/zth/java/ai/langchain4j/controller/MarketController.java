package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.dto.MarketChatRequest;
import com.zth.java.ai.langchain4j.dto.MarketChatResponse;
import com.zth.java.ai.langchain4j.dto.MarketTodayResponse;
import com.zth.java.ai.langchain4j.service.AuthService;
import com.zth.java.ai.langchain4j.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 今日市场接口：提供市场快照和基于页面上下文的追问。 */
@Tag(name = "今日市场")
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final AuthService authService;

    @Operation(summary = "获取今日市场概览")
    @GetMapping("/today")
    public ApiResponse<MarketTodayResponse> today(@RequestParam(name = "symbol", defaultValue = "000001") String symbol,
                                                  HttpSession session) {
        authService.requireUserId(session);
        return ApiResponse.success(marketService.getTodayMarket(symbol));
    }

    @Operation(summary = "基于当前市场页面上下文追问")
    @PostMapping("/chat")
    public ApiResponse<MarketChatResponse> chat(@Valid @RequestBody MarketChatRequest request, HttpSession session) {
        authService.requireUserId(session);
        return ApiResponse.success(marketService.answerMarketQuestion(request));
    }
}
