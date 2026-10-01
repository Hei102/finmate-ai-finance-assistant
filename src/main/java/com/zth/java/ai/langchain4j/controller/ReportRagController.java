package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.rag.ReportRagService;
import com.zth.java.ai.langchain4j.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-reports/rag")
@RequiredArgsConstructor
public class ReportRagController {
    private final ReportRagService rag;
    private final AuthService auth;

    public record IndexRequest(@NotBlank @Pattern(regexp = "\\d{6}") String symbol,
                               @NotBlank @Size(max = 128) String reportId) { }
    public record Question(@NotBlank @Size(max = 2000) String question) { }

    @PostMapping("/index")
    public ApiResponse<ReportRagService.IndexResult> index(@Valid @RequestBody IndexRequest request, HttpSession session) {
        return ApiResponse.success(rag.index(auth.requireUserId(session), request.symbol(), request.reportId()));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<ReportRagService.IndexResult> status(@PathVariable("id") String id, HttpSession session) {
        return ApiResponse.success(rag.status(auth.requireUserId(session), id));
    }

    @PostMapping("/documents/{id}/questions")
    public ApiResponse<ReportRagService.Answer> ask(@PathVariable("id") String id,
                                                    @Valid @RequestBody Question request, HttpSession session) {
        return ApiResponse.success(rag.ask(auth.requireUserId(session), id, request.question()));
    }
}
