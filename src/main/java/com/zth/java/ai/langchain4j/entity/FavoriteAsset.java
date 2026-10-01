package com.zth.java.ai.langchain4j.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/** MySQL 收藏实体：记录用户关注的标的及备注。 */
@Data
@Entity
@Table(
        name = "favorite_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorite_assets_user_symbol_category",
                columnNames = {"user_id", "symbol", "category"}
        )
)
public class FavoriteAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String name;

    private String symbol;

    private String category;

    private String note;

    private LocalDateTime createdAt;
}
