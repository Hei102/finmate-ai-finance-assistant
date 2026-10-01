package com.zth.java.ai.langchain4j.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** MySQL 财报分析实体：保存公告来源、分析方式和报告正文。 */
@Data
@Entity
@Table(name = "financial_report_analyses")
public class FinancialReportAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String symbol;

    private String companyName;

    private String reportId;

    private String reportTitle;

    private String reportType;

    private String reportPeriod;

    private String publishTime;

    private String sourceUrl;

    private String pdfUrl;

    private String analysisSource;

    @Lob
    private String content;

    private LocalDateTime createdAt;
}
