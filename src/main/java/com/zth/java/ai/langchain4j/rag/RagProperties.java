package com.zth.java.ai.langchain4j.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Setter
@Component
public class RagProperties {
    @Value("${FINMATE_RAG_ENABLED:false}")
    private boolean enabled = false;
    @Value("${FINMATE_RAG_QDRANT_URL:http://localhost:6333}")
    private String qdrantUrl = "http://localhost:6333";
    @Value("${FINMATE_RAG_QDRANT_API_KEY:}")
    private String qdrantApiKey = "";
    @Value("${FINMATE_RAG_COLLECTION:finmate_reports_v1}")
    private String collection = "finmate_reports_v1";
    @Value("${FINMATE_RAG_EMBEDDING_URL:https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings}")
    private String embeddingUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    @Value("${FINMATE_RAG_EMBEDDING_API_KEY:}")
    private String embeddingApiKey = "";
    @Value("${FINMATE_RAG_EMBEDDING_MODEL:text-embedding-v4}")
    private String embeddingModel = "text-embedding-v4";
    @Value("${FINMATE_RAG_DIMENSIONS:1024}")
    private int dimensions = 1024;
    @Value("${FINMATE_RAG_MIN_SCORE:0.45}")
    private double minScore = 0.45;

    public void requireEnabled() {
        if (!enabled || embeddingApiKey.isBlank()) {
            throw new IllegalArgumentException("财报问答尚未配置，请设置 FINMATE_RAG_ENABLED 和 FINMATE_RAG_EMBEDDING_API_KEY");
        }
        if (!collection.matches("[a-zA-Z0-9_-]+") || dimensions < 1
                || !Double.isFinite(minScore) || minScore < -1 || minScore > 1) {
            throw new IllegalArgumentException("RAG 集合名称、向量维度或相关性阈值配置无效");
        }
    }
}
