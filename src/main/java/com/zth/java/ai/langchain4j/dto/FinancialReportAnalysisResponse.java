package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 财报分析响应：提供分析正文、来源链接、分析方式和保存时间。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportAnalysisResponse {

    private Long id;
    private String symbol;
    private String companyName;
    private String reportId;
    private String reportTitle;
    private String reportType;
    private String reportPeriod;
    private String publishTime;
    private String sourceUrl;
    private String pdfUrl;
    private String content;
    private String analysisSource;
    private LocalDateTime createdAt;
}
