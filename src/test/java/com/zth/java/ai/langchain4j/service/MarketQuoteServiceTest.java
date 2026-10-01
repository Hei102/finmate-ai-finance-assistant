package com.zth.java.ai.langchain4j.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketQuoteServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final MarketQuoteService service = new MarketQuoteService(new ObjectMapper(), client);

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String body) {
        return mock(HttpResponse.class, invocation -> switch (invocation.getMethod().getName()) {
            case "statusCode" -> 200;
            case "body" -> body;
            default -> null;
        });
    }

    private String quote(String symbol, String name) {
        String[] fields = new String[38];
        Arrays.fill(fields, "0");
        fields[1] = name; fields[2] = symbol.substring(2); fields[3] = "3845.37";
        fields[6] = "1234"; fields[30] = "20260930142242";
        fields[31] = "14.92"; fields[32] = "0.39"; fields[37] = "57532704";
        return "v_" + symbol + "=\"" + String.join("~", fields) + "\";";
    }

    @Test
    void decodesIndicesWithSourceTimestampAndAmountUnits() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response(
                quote("sh000001", "上证指数") + quote("sz399001", "深证成指") + quote("sz399006", "创业板指")));
        var result = service.indices();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("上证指数");
        assertThat(result.get(0).getQuotedAt()).isEqualTo("2026-09-30T14:22:42");
        assertThat(Double.parseDouble(result.get(0).getAmount())).isEqualTo(575327040000d);
        assertThat(result.get(0).getSource()).isEqualTo("腾讯财经");
    }

    @Test
    void readsUnadjustedIndexBarsWhenQfqdayAbsent() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response("""
                {"data":{"sh000001":{"day":[["2026-09-29","3800","3830","3840","3790","123"]],
                "qt":{"sh000001":["1","上证指数"]}}}}
                """));
        var bars = service.bars("000001");
        assertThat(bars.points()).hasSize(1);
        assertThat(bars.points().get(0).getClose()).isEqualTo(3830);
        assertThat(bars.points().get(0).getAmount()).isNull();
        assertThat(bars.name()).isEqualTo("上证指数");
    }

    @Test
    void readsAdjustedStockBarsAndPreservesExplicitExchange() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertThat(request.uri().toString()).contains("sz000001,day");
            return response("""
                    {"data":{"sz000001":{"qfqday":[["2026-09-29","10","11","12","9","123"]],
                    "qt":{"sz000001":["51","平安银行"]}}}}
                    """);
        });
        assertThat(service.bars("sz000001").name()).isEqualTo("平安银行");
    }

    @Test
    void switchesToEastmoneyOnInvalidPrimaryPrices() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response("""
                        {"data":{"sh000001":{"day":[["2026-09-29","NaN","3830","3840","3790","123"]]}}}
                        """))
                .thenReturn(response("""
                        {"data":{"name":"上证指数","klines":["2026-09-29,3800,3830,3840,3790,123,456"]}}
                        """));
        var bars = service.bars("000001");
        assertThat(bars.source()).isEqualTo("东方财富");
        assertThat(bars.points().get(0).getAmount()).isEqualTo(456d);
    }

    @Test
    void emptyPrimaryIndicesTriggerSecondaryRatherThanFalseSuccess() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(""))
                .thenReturn(response("""
                        {"data":{"diff":[
                        {"f12":"000001","f14":"上证指数","f2":3800,"f3":1,"f4":38,"f124":1790749362},
                        {"f12":"399001","f14":"深证成指","f2":12000,"f3":1,"f4":120},
                        {"f12":"399006","f14":"创业板指","f2":3000,"f3":1,"f4":30}]}}
                        """));
        assertThat(service.indices()).hasSize(3).allMatch(index -> "东方财富".equals(index.getSource()));
    }

    @Test
    void allSourcesFailReturnsEmptyNotSyntheticBars() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("offline"));
        assertThat(service.bars("000001").points()).isEmpty();
        assertThat(service.indices()).isEmpty();
    }

    @Test
    void rejectsDuplicateDatesAndInvalidOhlc() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response("""
                        {"data":{"sh000001":{"day":[["2026-09-29","10","11","12","9","123"],
                        ["2026-09-29","10","11","12","9","123"]]}}}
                        """))
                .thenReturn(response("""
                        {"data":{"klines":["2026-09-29,10,11,9,8,123,456"]}}
                        """));
        assertThat(service.bars("000001").points()).isEmpty();
    }

    @Test
    void resolvesExchangeAndRejectsGarbage() {
        assertThat(MarketQuoteService.normalizeSymbol("000001")).isEqualTo("sh000001");
        assertThat(MarketQuoteService.normalizeSymbol("sz000001")).isEqualTo("sz000001");
        assertThat(MarketQuoteService.normalizeSymbol("510300")).isEqualTo("sh510300");
        assertThat(MarketQuoteService.normalizeSymbol("399001")).isEqualTo("sz399001");
        assertThatThrownBy(() -> service.bars("123abc")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test
    @EnabledIfSystemProperty(named = "market.live", matches = "true")
    void liveJavaHttpClientSmoke() {
        var live = new MarketQuoteService(new ObjectMapper());
        var indices = live.indices();
        var bars = live.bars("sh000001");
        assertThat(indices).hasSize(3);
        assertThat(bars.points()).hasSizeGreaterThanOrEqualTo(60);
        assertThat(bars.name()).isEqualTo("上证指数");
        System.out.printf("LIVE source=%s indices=%d bars=%d lastDate=%s close=%s%n", bars.source(),
                indices.size(), bars.points().size(), bars.points().get(bars.points().size()-1).getDate(),
                bars.points().get(bars.points().size()-1).getClose());
    }
}
