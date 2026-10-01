package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 收藏响应：返回标的信息、备注及收藏时间。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteAssetResponse {

    private Long id;
    private String name;
    private String symbol;
    private String category;
    private String note;
    private LocalDateTime createdAt;
}
