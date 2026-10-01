package com.zth.java.ai.langchain4j.service;

import com.zth.java.ai.langchain4j.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 汇总收藏和会话数量，提供仪表盘数据。 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FavoriteAssetService favoriteAssetService;
    private final ChatSessionService chatSessionService;

    /** 组合业务服务的统计和最近记录。 */
    public DashboardResponse getDashboard(Long userId) {
        return new DashboardResponse(
                favoriteAssetService.count(userId),
                chatSessionService.count(userId),
                favoriteAssetService.listRecent(userId)
        );
    }
}
