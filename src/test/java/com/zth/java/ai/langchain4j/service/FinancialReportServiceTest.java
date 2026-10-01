package com.zth.java.ai.langchain4j.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zth.java.ai.langchain4j.dto.FinancialReportAnalysisRequest;
import com.zth.java.ai.langchain4j.entity.FavoriteAsset;
import com.zth.java.ai.langchain4j.entity.FinancialReportAnalysis;
import com.zth.java.ai.langchain4j.repository.FavoriteAssetRepository;
import com.zth.java.ai.langchain4j.repository.FinancialReportAnalysisRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinancialReportServiceTest {
    private final FavoriteAssetRepository favorites = mock(FavoriteAssetRepository.class);
    private final FinancialReportAnalysisRepository analyses = mock(FinancialReportAnalysisRepository.class);
    private final ChatModel model = mock(ChatModel.class);
    private final FinancialReportPdfService pdf = mock(FinancialReportPdfService.class);
    private final FinancialReportService service = new FinancialReportService(favorites, analyses, new ObjectMapper(), model, pdf);
    private final FinancialReportAnalysisRequest request = new FinancialReportAnalysisRequest();

    @BeforeEach
    void setup() {
        FavoriteAsset favorite = new FavoriteAsset();
        favorite.setSymbol("600519");
        favorite.setName("测试公司");
        favorite.setCategory("股票");
        when(favorites.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favorite));
        request.setSymbol("600519");
        request.setReportId("report-1");
        request.setPdfUrl("https://static.cninfo.com.cn/finalpage/2026-04-01/123.PDF");
        when(pdf.read(request.getPdfUrl())).thenReturn(new FinancialReportPdfService.PdfEvidence(
                10, 10, List.of(3), true, "【PDF第3页】营业收入100万元"));
        when(analyses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsActualEvidenceToModelAndSavesCoverageWithAnswer() {
        when(model.chat(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("营业收入100万元【PDF第3页】")).build());
        var response = service.analyze(1L, request);
        ArgumentCaptor<UserMessage> prompt = ArgumentCaptor.forClass(UserMessage.class);
        verify(model).chat(any(ChatMessage.class), prompt.capture());
        assertThat(prompt.getValue().singleText()).contains("【PDF第3页】营业收入100万元");
        assertThat(response.getAnalysisSource()).isEqualTo("AI·PDF选段");
        assertThat(response.getContent()).contains("不代表全文审阅", "营业收入100万元");
        ArgumentCaptor<FinancialReportAnalysis> saved = ArgumentCaptor.forClass(FinancialReportAnalysis.class);
        verify(analyses).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void doesNotCallModelOrSaveWhenPdfCannotBeRead() {
        when(pdf.read(anyString())).thenThrow(new IllegalArgumentException("扫描件"));
        assertThatThrownBy(() -> service.analyze(1L, request)).hasMessageContaining("扫描件");
        verifyNoInteractions(model, analyses);
    }

    @Test
    void doesNotSaveFakeFallbackWhenModelFails() {
        when(model.chat(any(ChatMessage.class), any(ChatMessage.class))).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.analyze(1L, request)).hasMessageContaining("未保存");
        verifyNoInteractions(analyses);
    }

    @Test
    void deniesAccessBeforeDownloadingWhenStockIsNotOwned() {
        assertThatThrownBy(() -> service.analyze(2L, request)).hasMessageContaining("收藏列表");
        verifyNoInteractions(pdf, model, analyses);
    }

    @Test
    void derivesCninfoOrgIdForShanghaiMainBoardWithoutUsingRemovedDictionary() {
        assertThat(FinancialReportService.deriveCninfoOrgId("600036")).isEqualTo("gssh0600036");
        assertThat(FinancialReportService.deriveCninfoOrgId("600519")).isEqualTo("gssh0600519");
        assertThat(FinancialReportService.deriveCninfoOrgId("601398")).isEqualTo("gssh0601398");
        assertThat(FinancialReportService.deriveCninfoOrgId("603259")).isEqualTo("gssh0603259");
        assertThat(FinancialReportService.deriveCninfoOrgId("605499")).isEqualTo("gssh0605499");
        assertThat(FinancialReportService.deriveCninfoOrgId("688981")).isEmpty();
        assertThat(FinancialReportService.deriveCninfoOrgId("000001")).isEmpty();
    }
}
