package com.zth.java.ai.langchain4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 使用官方 REST 协议，避免引入与当前 LangChain4j BOM 不匹配的 SDK。 */
@Component
public class RagVectorClient {
    private final RagProperties config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public RagVectorClient(RagProperties config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    public List<List<Double>> embed(List<String> texts) {
        JsonNode data = call("POST", config.getEmbeddingUrl(), config.getEmbeddingApiKey(), true,
                Map.of("model", config.getEmbeddingModel(), "input", texts,
                        "dimensions", config.getDimensions(), "encoding_format", "float"), false).path("data");
        if (!data.isArray() || data.size() != texts.size()) {
            throw failure("向量模型返回数量不匹配");
        }
        List<List<Double>> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            JsonNode vector = item.path("embedding");
            if (index < 0 || index >= texts.size() || result.get(index) != null
                    || !vector.isArray() || vector.size() != config.getDimensions()) {
                throw failure("向量模型返回索引或维度不匹配");
            }
            List<Double> values = new ArrayList<>();
            for (JsonNode value : vector) {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    throw failure("向量包含无效数值");
                }
                values.add(value.asDouble());
            }
            result.set(index, values);
        }
        return result;
    }

    public void ensureCollection() {
        JsonNode existing = qdrant("GET", "", null, true);
        if (existing == null) {
            qdrant("PUT", "", Map.of("vectors", Map.of("size", config.getDimensions(), "distance", "Cosine")), false);
        } else {
            JsonNode vectors = existing.path("result").path("config").path("params").path("vectors");
            if (vectors.path("size").asInt() != config.getDimensions()
                    || !"Cosine".equals(vectors.path("distance").asText())) {
                throw failure("Qdrant 集合维度或距离配置不匹配，请使用新集合");
            }
        }
        // 云端严格模式要求过滤字段先建立 payload 索引。
        for (String field : List.of("documentId", "generation", "userId")) {
            qdrant("PUT", "/index?wait=true", Map.of("field_name", field,
                    "field_schema", "userId".equals(field) ? "integer" : "keyword"), false);
        }
    }

    public void upsert(ReportDocument doc, List<ReportChunker.Chunk> chunks, List<List<Double>> vectors) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("generation", doc.getGeneration());
            payload.put("userId", doc.getUserId());
            payload.put("page", chunk.page());
            payload.put("text", chunk.text());
            payload.put("symbol", doc.getSymbol());
            payload.put("reportPeriod", Objects.toString(doc.getReportPeriod(), ""));
            points.add(Map.of("id", UUID.nameUUIDFromBytes((doc.getGeneration() + ":" + chunk.number())
                    .getBytes(StandardCharsets.UTF_8)).toString(), "vector", vectors.get(i), "payload", payload));
        }
        qdrant("PUT", "/points?wait=true", Map.of("points", points), false);
    }

    public record Hit(int page, String text, double score) { }

    public List<Hit> search(ReportDocument doc, List<Double> query) {
        JsonNode points = qdrant("POST", "/points/query", Map.of("query", query, "limit", 5,
                "score_threshold", config.getMinScore(), "with_payload", true,
                "filter", filter(doc)), false).path("result").path("points");
        if (!points.isArray()) {
            throw failure("Qdrant 返回格式异常");
        }
        List<Hit> hits = new ArrayList<>();
        for (JsonNode point : points) {
            JsonNode p = point.path("payload");
            if (!doc.getId().equals(p.path("documentId").asText())
                    || !doc.getGeneration().equals(p.path("generation").asText())
                    || doc.getUserId() != p.path("userId").asLong()) {
                throw failure("检索结果归属不匹配");
            }
            int page = p.path("page").asInt();
            String text = p.path("text").asText();
            if (page < 1 || page > doc.getTotalPages() || text.isBlank() || text.length() > 800) {
                throw failure("检索片段格式异常");
            }
            hits.add(new Hit(page, text, point.path("score").asDouble()));
        }
        return hits;
    }

    public void deleteGeneration(ReportDocument doc) {
        qdrant("POST", "/points/delete?wait=true", Map.of("filter", filter(doc)), false);
    }

    private Map<String, Object> filter(ReportDocument doc) {
        return Map.of("must", List.of(condition("documentId", doc.getId()),
                condition("generation", doc.getGeneration()), condition("userId", doc.getUserId())));
    }

    private Map<String, Object> condition(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private JsonNode qdrant(String method, String path, Object body, boolean allowMissing) {
        return call(method, config.getQdrantUrl().replaceAll("/+$", "") + "/collections/" + config.getCollection() + path,
                config.getQdrantApiKey(), false, body, allowMissing);
    }

    private JsonNode call(String method, String url, String key, boolean bearer, Object body, boolean allowMissing) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");
            if (!key.isBlank()) {
                request.header(bearer ? "Authorization" : "api-key", bearer ? "Bearer " + key : key);
            }
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (allowMissing && response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure((bearer ? "Embedding" : "Qdrant") + " 服务返回 HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("RAG 请求已中断");
        } catch (java.io.IOException exception) {
            throw failure("RAG 服务连接或响应解析失败，请检查服务配置");
        }
    }

    private IllegalArgumentException failure(String message) {
        return new IllegalArgumentException(message);
    }
}
