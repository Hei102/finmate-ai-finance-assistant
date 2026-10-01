package com.zth.java.ai.langchain4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagVectorClientTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private String response;
    private int status = 200;
    private RagVectorClient client;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            request.set(body.length == 0 ? mapper.createObjectNode() : mapper.readTree(body));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        RagProperties config = new RagProperties();
        config.setEmbeddingUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings");
        config.setQdrantUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setDimensions(2);
        client = new RagVectorClient(config, mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void restoresEmbeddingOrderAndRejectsMalformedDimensions() {
        response = "{\"data\":[{\"index\":1,\"embedding\":[3,4]},{\"index\":0,\"embedding\":[1,2]}]}";
        assertThat(client.embed(List.of("a", "b"))).containsExactly(List.of(1.0, 2.0), List.of(3.0, 4.0));
        assertThat(request.get().path("dimensions").asInt()).isEqualTo(2);
        response = "{\"data\":[{\"index\":0,\"embedding\":[1]}]}";
        assertThatThrownBy(() -> client.embed(List.of("a"))).hasMessageContaining("维度");
    }

    @Test
    void sendsOwnershipAndGenerationFiltersAndRejectsForeignResults() {
        ReportDocument doc = new ReportDocument();
        doc.setId("d1");
        doc.setUserId(1L);
        doc.setGeneration("g1");
        doc.setTotalPages(10);
        response = "{\"result\":{\"points\":[]}}";
        assertThat(client.search(doc, List.of(1.0, 2.0))).isEmpty();
        assertThat(request.get().path("filter").path("must").toString())
                .contains("documentId", "generation", "userId", "g1");
        response = "{\"result\":{\"points\":[{\"payload\":{\"documentId\":\"d1\",\"generation\":\"g1\",\"userId\":2}}]}}";
        assertThatThrownBy(() -> client.search(doc, List.of(1.0, 2.0))).hasMessageContaining("归属");
    }

    @Test
    void validatesCollectionAndCreatesFilterIndexes() {
        response = "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":2,\"distance\":\"Cosine\"}}}}}";
        client.ensureCollection();
        assertThat(request.get().path("field_name").asText()).isEqualTo("userId");
        assertThat(request.get().path("field_schema").asText()).isEqualTo("integer");
        response = "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":3,\"distance\":\"Cosine\"}}}}}";
        assertThatThrownBy(() -> client.ensureCollection()).hasMessageContaining("不匹配");
    }

    @Test
    void doesNotExposeUpstreamErrorBodies() {
        status = 401;
        response = "sensitive-upstream-details";
        assertThatThrownBy(() -> client.embed(List.of("a"))).hasMessageContaining("401")
                .hasMessageNotContaining("sensitive");
    }
}
