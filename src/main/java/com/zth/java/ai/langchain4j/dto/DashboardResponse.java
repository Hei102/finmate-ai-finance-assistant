package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 仪表盘响应：包含统计数量和最近收藏。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long favoriteCount;
    private long chatSessionCount;
    private List<FavoriteAssetResponse> recentFavorites;
}
