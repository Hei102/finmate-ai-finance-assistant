package com.zth.java.ai.langchain4j.rag;

import com.zth.java.ai.langchain4j.service.FinancialReportPdfService.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportChunkerTest {
    @Test
    void retainsTailAndPhysicalPagesWithBoundedOverlappingChunks() {
        String first = "营业收入及单位\n" + "财报正文".repeat(300) + "第一页结尾";
        var chunks = new ReportChunker().split(List.of(new PageText(1, first), new PageText(90, "风险因素尾页")));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(800));
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.text()).endsWith("第一页结尾"));
        assertThat(chunks.get(chunks.size() - 1).page()).isEqualTo(90);
        assertThat(chunks.get(0).text().substring(chunks.get(0).text().length() - 100))
                .isEqualTo(chunks.get(1).text().substring(0, 100));
    }

    @Test
    void validatesModelCitationIds() {
        ReportRagService.validateCitations("收入变化见[1]与[2]", 2);
        assertThatThrownBy(() -> ReportRagService.validateCitations("错误引用[3]", 2)).hasMessageContaining("无效来源");
        assertThatThrownBy(() -> ReportRagService.validateCitations("错误引用[9999999999999]", 2)).hasMessageContaining("无效来源");
    }
}
