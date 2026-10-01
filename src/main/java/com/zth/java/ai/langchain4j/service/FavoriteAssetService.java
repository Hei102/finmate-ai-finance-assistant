package com.zth.java.ai.langchain4j.service;

import com.zth.java.ai.langchain4j.dto.FavoriteAssetRequest;
import com.zth.java.ai.langchain4j.dto.FavoriteAssetResponse;
import com.zth.java.ai.langchain4j.entity.FavoriteAsset;
import com.zth.java.ai.langchain4j.repository.FavoriteAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 管理当前用户的收藏，负责重复检查、查询、删除和统计。 */
@Service
@RequiredArgsConstructor
public class FavoriteAssetService {

    private final FavoriteAssetRepository favoriteAssetRepository;

    /** 检查重复收藏，整理输入后保存标的。 */
    public FavoriteAssetResponse create(Long userId, FavoriteAssetRequest request) {
        String symbol = request.getSymbol().trim().toUpperCase(Locale.ROOT);
        String category = request.getCategory().trim();
        if (favoriteAssetRepository.existsByUserIdAndSymbolIgnoreCaseAndCategoryIgnoreCase(userId, symbol, category)) {
            throw new IllegalArgumentException("该标的已经收藏过了");
        }

        FavoriteAsset asset = new FavoriteAsset();
        asset.setUserId(userId);
        asset.setName(request.getName().trim());
        asset.setSymbol(symbol);
        asset.setCategory(category);
        asset.setNote(request.getNote());
        asset.setCreatedAt(LocalDateTime.now());

        return toResponse(favoriteAssetRepository.save(asset));
    }

    /** 读取当前用户的全部收藏。 */
    public List<FavoriteAssetResponse> listAll(Long userId) {
        return favoriteAssetRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /** 读取仪表盘所需的最近八条收藏。 */
    public List<FavoriteAssetResponse> listRecent(Long userId) {
        return favoriteAssetRepository.findTop8ByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /** 验证收藏归属后删除。 */
    public void delete(Long userId, Long id) {
        FavoriteAsset asset = favoriteAssetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("收藏不存在或不属于当前账号"));
        favoriteAssetRepository.delete(asset);
    }

    /** 统计当前用户收藏数量。 */
    public long count(Long userId) {
        return favoriteAssetRepository.countByUserId(userId);
    }

    /** 将收藏实体转换为接口响应。 */
    private FavoriteAssetResponse toResponse(FavoriteAsset asset) {
        return new FavoriteAssetResponse(
                asset.getId(),
                asset.getName(),
                asset.getSymbol(),
                asset.getCategory(),
                asset.getNote(),
                asset.getCreatedAt()
        );
    }
}
