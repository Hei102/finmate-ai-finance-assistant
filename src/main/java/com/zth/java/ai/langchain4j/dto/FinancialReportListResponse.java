package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 财报列表响应：包含股票信息、数据状态和公告列表。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportListResponse {

    private String symbol;
    private String companyName;
    private String status;
    private List<FinancialReportResponse> reports;
}
