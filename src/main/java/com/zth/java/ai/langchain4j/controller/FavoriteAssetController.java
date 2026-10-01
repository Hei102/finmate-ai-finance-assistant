package com.zth.java.ai.langchain4j.controller;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.dto.FavoriteAssetRequest;
import com.zth.java.ai.langchain4j.dto.FavoriteAssetResponse;
import com.zth.java.ai.langchain4j.service.AuthService;
import com.zth.java.ai.langchain4j.service.FavoriteAssetService;
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

import java.util.List;

/** 收藏接口：查询、新增和删除当前账号的收藏。 */
@Tag(name = "收藏标的管理")
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteAssetController {

    private final FavoriteAssetService favoriteAssetService;
    private final AuthService authService;

    @Operation(summary = "添加收藏标的")
    @PostMapping
    public ApiResponse<FavoriteAssetResponse> create(@Valid @RequestBody FavoriteAssetRequest request, HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success("收藏成功", favoriteAssetService.create(userId, request));
    }

    @Operation(summary = "获取当前用户全部收藏标的")
    @GetMapping
    public ApiResponse<List<FavoriteAssetResponse>> list(HttpSession session) {
        Long userId = authService.requireUserId(session);
        return ApiResponse.success(favoriteAssetService.listAll(userId));
    }

    @Operation(summary = "删除收藏标的")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id, HttpSession session) {
        Long userId = authService.requireUserId(session);
        favoriteAssetService.delete(userId, id);
        return ApiResponse.success("删除成功", null);
    }
}
