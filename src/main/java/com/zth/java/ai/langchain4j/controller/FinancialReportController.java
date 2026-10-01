package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.dto.FavoriteAssetResponse;
import com.zth.java.ai.langchain4j.dto.FinancialReportAnalysisRequest;
import com.zth.java.ai.langchain4j.dto.FinancialReportAnalysisResponse;
import com.zth.java.ai.langchain4j.dto.FinancialReportListResponse;
import com.zth.java.ai.langchain4j.service.AuthService;
import com.zth.java.ai.langchain4j.service.FinancialReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 财报接口：查询收藏股票、公告列表，生成及读取分析报告。 */
@Tag(name = "财报分析")
@RestController
@RequestMapping("/api/financial-reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportService financialReportService;
    private final AuthService authService;

    @Operation(summary = "获取当前用户收藏的股票")
    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteAssetResponse>> favoriteStocks(HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(financialReportService.listFavoriteStocks(userId));
    }

    @Operation(summary = "获取某只收藏股票的财报公告")
    @GetMapping("/stocks/{symbol}/reports")
    public ApiResponse<FinancialReportListResponse> reports(@PathVariable("symbol") String symbol,
                                                            @RequestParam(name = "name", required = false) String name,
                                                            HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(financialReportService.listReports(userId, symbol, name));
    }

    @Operation(summary = "生成并保存财报分析")
    @PostMapping("/analyze")
    public ApiResponse<FinancialReportAnalysisResponse> analyze(@Valid @RequestBody FinancialReportAnalysisRequest request,
                                                                HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success("财报分析报告已生成", financialReportService.analyze(userId, request));
    }

    @Operation(summary = "获取当前用户财报分析历史")
    @GetMapping("/analyses")
    public ApiResponse<List<FinancialReportAnalysisResponse>> analyses(HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(financialReportService.listAnalyses(userId));
    }
}
