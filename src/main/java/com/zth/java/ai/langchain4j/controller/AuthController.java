package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.dto.CurrentUserResponse;
import com.zth.java.ai.langchain4j.dto.LoginRequest;
import com.zth.java.ai.langchain4j.dto.RegisterRequest;
import com.zth.java.ai.langchain4j.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录注册接口：接收表单参数，调用认证服务并返回统一响应。 */
@Tag(name = "用户登录注册")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    public ApiResponse<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        return ApiResponse.success("注册成功", authService.register(request, session));
    }

    @Operation(summary = "登录账号")
    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        return ApiResponse.success("登录成功", authService.login(request, session));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(HttpSession session) {
        return ApiResponse.success(authService.currentUser(session));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        authService.logout(session);
        return ApiResponse.success("已退出登录", null);
    }
}
