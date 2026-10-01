package com.zth.java.ai.langchain4j.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 市场快照响应：组合指数、K 线、新闻、观察清单和风险提示。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketTodayResponse {

    private LocalDateTime generatedAt;
    private String watchedSymbol;
    private String watchedName;
    private String dataStatus;
    private String aiInsight;
    private String disclaimer;
    private List<MarketIndexSnapshot> indices;
    private List<KLinePoint> kLines;
    private List<MarketNewsItem> news;
    private List<StockIdea> stockIdeas;
    private List<String> riskChecklist;
    private String kLineSource;

    // 保持接口字段名为 kLines；显式访问器避免命名推断影响前端图表。
    @JsonProperty("kLines")
    public List<KLinePoint> getKLines() {
        return kLines;
    }

    @JsonProperty("kLines")
    public void setKLines(List<KLinePoint> kLines) {
        this.kLines = kLines;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketIndexSnapshot {
        private String symbol;
        private String name;
        private String latest;
        private String change;
        private String changePercent;
        private String volume;
        private String amount;
        private String source;
        private String quotedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KLinePoint {
        private String date;
        private double open;
        private double close;
        private double high;
        private double low;
        private double volume;
        private Double amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketNewsItem {
        private String title;
        private String summary;
        private String url;
        private String source;
        private String publishTime;
        private String impactTag;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockIdea {
        private String symbol;
        private String name;
        private String theme;
        private String reason;
        private String risk;
    }
}
