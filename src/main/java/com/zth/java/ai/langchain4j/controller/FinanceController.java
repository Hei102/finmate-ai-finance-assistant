package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.assistant.FinanceAgent;
import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.bean.ChatForm;
import com.zth.java.ai.langchain4j.dto.ChatMessageResponse;
import com.zth.java.ai.langchain4j.dto.ChatResponse;
import com.zth.java.ai.langchain4j.dto.ChatSessionRequest;
import com.zth.java.ai.langchain4j.dto.ChatSessionResponse;
import com.zth.java.ai.langchain4j.entity.FinanceChatSession;
import com.zth.java.ai.langchain4j.service.AuthService;
import com.zth.java.ai.langchain4j.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 聊天接口：校验登录用户，管理会话并调用财经 Agent。 */
@Tag(name = "财经分析对话")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceAgent financeAgent;
    private final AuthService authService;
    private final ChatSessionService chatSessionService;

    @Operation(summary = "获取当前用户的聊天会话")
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(chatSessionService.list(userId));
    }

    @Operation(summary = "创建聊天会话")
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@RequestBody(required = false) ChatSessionRequest request,
                                                          HttpSession session) {
        Long userId = authService.requireUserId(session);
        String title = request == null ? null : request.getTitle();
        return ApiResponse.success("会话已创建", chatSessionService.toResponse(chatSessionService.create(userId, title)));
    }

    @Operation(summary = "获取聊天会话消息")
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable("id") Long id, HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(chatSessionService.listMessages(userId, id));
    }

    @Operation(summary = "删除聊天会话")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable("id") Long id, HttpSession session) {
        Long userId = authService.requireUserId(session);
        chatSessionService.delete(userId, id);
        return ApiResponse.success("会话已删除", null);
    }

    @Operation(summary = "发起财经分析对话")
    @PostMapping
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatForm chatForm, HttpSession session) {
        Long userId = authService.requireUserId(session);
        FinanceChatSession chatSession = chatSessionService.resolveForMessage(
                userId,
                chatForm.getChatSessionId(),
                chatForm.getMessage()
        );
        String answer = financeAgent.chat(chatSession.getId(), chatForm.getMessage());
        chatSessionService.touch(chatSession);
        ChatResponse response = new ChatResponse(
                chatSession.getId(),
                chatSession.getId(),
                chatSession.getTitle(),
                answer,
                LocalDateTime.now()
        );
        return ApiResponse.success("对话成功", response);
    }
}
