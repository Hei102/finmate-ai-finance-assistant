package com.zth.java.ai.langchain4j.rag;

import com.zth.java.ai.langchain4j.dto.FavoriteAssetResponse;
import com.zth.java.ai.langchain4j.dto.FinancialReportListResponse;
import com.zth.java.ai.langchain4j.dto.FinancialReportResponse;
import com.zth.java.ai.langchain4j.service.FinancialReportPdfService;
import com.zth.java.ai.langchain4j.service.FinancialReportService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportRagServiceTest {
    private final RagProperties config = new RagProperties();
    private final ReportDocumentRepository repo = mock(ReportDocumentRepository.class);
    private final FinancialReportService reports = mock(FinancialReportService.class);
    private final FinancialReportPdfService pdf = mock(FinancialReportPdfService.class);
    private final RagVectorClient vectors = mock(RagVectorClient.class);
    private final ChatModel model = mock(ChatModel.class);
    private final Map<String, ReportDocument> saved = new HashMap<>();
    private ReportRagService service;

    @BeforeEach
    void setup() {
        config.setEnabled(true);
        config.setEmbeddingApiKey("test");
        service = new ReportRagService(config, repo, reports, pdf, new ReportChunker(), vectors, model);
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> {
            ReportDocument doc = i.getArgument(0);
            saved.put(doc.getId(), doc);
            return doc;
        });
        when(repo.findByIdAndUserId(anyString(), anyLong())).thenAnswer(i -> {
            ReportDocument doc = saved.get(i.getArgument(0));
            return doc != null && doc.getUserId().equals(i.getArgument(1)) ? Optional.of(doc) : Optional.empty();
        });
        var report = new FinancialReportResponse("r1", "600519", "公司", "年度报告", "年报", "2025", "",
                "", "https://static.cninfo.com.cn/finalpage/2026-04-01/123.PDF", "巨潮资讯");
        when(reports.listReports(1L, "600519", null)).thenReturn(new FinancialReportListResponse("600519", "公司", "ok", List.of(report)));
        when(reports.listFavoriteStocks(1L)).thenReturn(List.of(new FavoriteAssetResponse(1L, "公司", "600519", "股票", "", null)));
        when(pdf.readPages(anyString())).thenReturn(new FinancialReportPdfService.ParsedPdf(10,
                List.of(new FinancialReportPdfService.PageText(7, "营业收入100万元"))));
        when(vectors.embed(anyList())).thenReturn(List.of(List.of(0.2, 0.3)));
    }

    @Test
    void indexesOnceAndReturnsServerSources() {
        var index = service.index(1L, "600519", "r1");
        assertThat(service.index(1L, "600519", "r1").documentId()).isEqualTo(index.documentId());
        verify(pdf, times(1)).readPages(anyString());
        when(vectors.search(any(), anyList())).thenReturn(List.of(new RagVectorClient.Hit(7, "营业收入100万元", 0.8)));
        when(model.chat(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("收入100万元。[1]")).build());
        var answer = service.ask(1L, index.documentId(), "收入是多少？");
        assertThat(answer.sources().get(0).sourceUrl()).endsWith("123.PDF#page=7");
        assertThat(answer.coverage()).contains("10 页", "1 页");
    }

    @Test
    void rejectsOtherUsersBeforeEmbedding() {
        var index = service.index(1L, "600519", "r1");
        clearInvocations(vectors);
        assertThatThrownBy(() -> service.ask(2L, index.documentId(), "收入？")).hasMessageContaining("不属于");
        verifyNoInteractions(vectors, model);
    }

    @Test
    void emptyRetrievalNeverCallsModel() {
        var index = service.index(1L, "600519", "r1");
        when(vectors.search(any(), anyList())).thenReturn(List.of());
        assertThat(service.ask(1L, index.documentId(), "未知问题").sources()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void failedIndexIsNotQueryableAndCanBeRetried() {
        doThrow(new IllegalArgumentException("write failed")).doNothing().when(vectors).upsert(any(), anyList(), anyList());
        assertThatThrownBy(() -> service.index(1L, "600519", "r1")).hasMessageContaining("write failed");
        ReportDocument doc = saved.values().iterator().next();
        assertThat(doc.getStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> service.ask(1L, doc.getId(), "收入？")).hasMessageContaining("先");
        verify(vectors).deleteGeneration(doc);
        assertThat(service.index(1L, "600519", "r1").status()).isEqualTo("READY");
    }

    @Test
    void rejectsUnverifiedReportAndRemovedFavorite() {
        assertThatThrownBy(() -> service.index(1L, "600519", "forged")).hasMessageContaining("核验");
        verifyNoInteractions(pdf, vectors);
        var index = service.index(1L, "600519", "r1");
        when(reports.listFavoriteStocks(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.ask(1L, index.documentId(), "收入？")).hasMessageContaining("收藏");
    }

    @Test
    void embeddingProfileChangeRequiresReindex() {
        var index = service.index(1L, "600519", "r1");
        config.setEmbeddingModel("different-model");
        assertThatThrownBy(() -> service.ask(1L, index.documentId(), "收入？")).hasMessageContaining("先");
    }
}
