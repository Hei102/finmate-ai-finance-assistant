package com.zth.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 财报分析请求：携带所选公告信息，后端仍需校验用户收藏和 PDF 链接。 */
@Data
public class FinancialReportAnalysisRequest {

    @NotBlank(message = "请选择财报")
    private String reportId;

    @NotBlank(message = "请选择股票")
    private String symbol;

    private String companyName;

    private String title;

    private String reportType;

    private String reportPeriod;

    private String publishTime;

    private String sourceUrl;

    private String pdfUrl;

    private String favoriteNote;
}
