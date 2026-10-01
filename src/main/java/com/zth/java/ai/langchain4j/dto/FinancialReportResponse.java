package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 财报公告响应：包含公告标识、报告期、来源和 PDF 链接。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportResponse {

    private String reportId;
    private String symbol;
    private String companyName;
    private String title;
    private String reportType;
    private String reportPeriod;
    private String publishTime;
    private String sourceUrl;
    private String pdfUrl;
    private String source;
}
