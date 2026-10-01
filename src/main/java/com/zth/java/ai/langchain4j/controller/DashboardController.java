package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.dto.DashboardResponse;
import com.zth.java.ai.langchain4j.service.AuthService;
import com.zth.java.ai.langchain4j.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仪表盘接口：返回当前账号的数据统计。 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthService authService;

    @Operation(summary = "获取当前用户仪表盘数据")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(dashboardService.getDashboard(userId));
    }
}
