package com.zth.java.ai.langchain4j.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialReportPdfServiceTest {
    private final FinancialReportPdfService service = new FinancialReportPdfService();

    @Test
    void acceptsOfficialPdfButRejectsOtherHostsSchemesAndRedirectParameters() {
        assertThat(service.validateUrl("https://static.cninfo.com.cn/finalpage/2026-04-01/123.PDF").getHost())
                .isEqualTo("static.cninfo.com.cn");
        assertThat(service.validateUrl("https://static.cninfo.com.cn/finalpage/2026-04-01/123.pdf")).isNotNull();
        for (String url : List.of("http://127.0.0.1/a.pdf", "file:///etc/passwd",
                "https://static.cninfo.com.cn.evil.com/finalpage/2026-04-01/123.PDF",
                "https://user@static.cninfo.com.cn/finalpage/2026-04-01/123.PDF",
                "https://static.cninfo.com.cn/finalpage/2026-04-01/123.PDF?url=http://localhost",
                "https://static.cninfo.com.cn:8443/finalpage/2026-04-01/123.PDF", "")) {
            assertThatThrownBy(() -> service.validateUrl(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void extractsRealPdfTextWithPhysicalPageNumbers() throws Exception {
        var evidence = service.extract(pdf(false));
        assertThat(evidence.totalPages()).isEqualTo(2);
        assertThat(evidence.includedPages()).containsExactly(1, 2);
        assertThat(evidence.context()).contains("【PDF第1页】", "Revenue", "【PDF第2页】", "Risk");
        assertThat(evidence.truncated()).isFalse();
    }

    @Test
    void rejectsHtmlAndImageOnlyDocuments() throws Exception {
        assertThatThrownBy(() -> service.extract("<html>failure</html>".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("不是有效的 PDF");
        byte[] empty = pdf(true);
        assertThatThrownBy(() -> service.extract(empty)).hasMessageContaining("扫描件");
    }

    @Test
    void prefersFinancialEvidenceOverContentsAndDisclosesOmissions() {
        var evidence = service.selectEvidence(List.of(
                new FinancialReportPdfService.PageText(1, "目录 主要财务指标 营业收入 风险因素".repeat(20)),
                new FinancialReportPdfService.PageText(2, "普通介绍".repeat(100)),
                new FinancialReportPdfService.PageText(90, "主要财务指标 营业收入 100万元".repeat(20))), 90, 150);
        assertThat(evidence.includedPages()).containsExactly(90);
        assertThat(evidence.context().length()).isLessThanOrEqualTo(150);
        assertThat(evidence.truncated()).isTrue();
        assertThat(evidence.coverage()).contains("不代表全文审阅", "未提取到文字");
    }

    private byte[] pdf(boolean blank) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : List.of("Revenue 2025: CNY 100 million. Operating cash flow: CNY 20 million.",
                    "Risk: Raw material prices increased during the reporting period.")) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!blank) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                        stream.newLineAtOffset(40, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
