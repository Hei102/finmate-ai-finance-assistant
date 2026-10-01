package com.zth.java.ai.langchain4j.repository;

import com.zth.java.ai.langchain4j.entity.FinancialReportAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 财报分析数据访问：查询当前用户的历史分析报告。 */
public interface FinancialReportAnalysisRepository extends JpaRepository<FinancialReportAnalysis, Long> {

    List<FinancialReportAnalysis> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
