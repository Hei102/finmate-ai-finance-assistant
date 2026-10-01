package com.zth.java.ai.langchain4j.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "rag_report_document")
public class ReportDocument {
    @Id
    @Column(length = 64)
    private String id;
    private Long userId;
    private String symbol;
    private String reportId;
    private String title;
    private String reportPeriod;
    @Column(length = 2048)
    private String pdfUrl;
    private String status;
    private String generation;
    private String indexProfile;
    private String contentHash;
    private int totalPages;
    private int readablePages;
    private int chunkCount;
}
