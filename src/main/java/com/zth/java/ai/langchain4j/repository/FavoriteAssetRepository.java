package com.zth.java.ai.langchain4j.repository;

import com.zth.java.ai.langchain4j.entity.FavoriteAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 收藏数据访问：按用户查询、统计，并检查标的是否重复。 */
public interface FavoriteAssetRepository extends JpaRepository<FavoriteAsset, Long> {

    boolean existsByUserIdAndSymbolIgnoreCaseAndCategoryIgnoreCase(Long userId, String symbol, String category);

    List<FavoriteAsset> findTop8ByUserIdOrderByCreatedAtDesc(Long userId);

    List<FavoriteAsset> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteAsset> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
