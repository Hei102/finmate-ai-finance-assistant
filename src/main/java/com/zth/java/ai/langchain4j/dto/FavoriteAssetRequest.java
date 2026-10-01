package com.zth.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 收藏请求：接收标的名称、代码、分类和备注。 */
@Data
public class FavoriteAssetRequest {

    @NotBlank(message = "请输入标的名称")
    private String name;

    @NotBlank(message = "请输入代码或简称")
    private String symbol;

    @NotBlank(message = "请选择分类")
    private String category;

    private String note;
}
