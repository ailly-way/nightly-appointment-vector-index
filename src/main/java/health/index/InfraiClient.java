package health.index;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiClient {
    private final HttpClient http;
    private final ServiceConfig config;

    public InfraiClient(ServiceConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(ServiceConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> createCollection(int dimension) throws IOException, InterruptedException {
        return post("/v1/vector/collection/create", Map.of(
                "collection", config.collection(), "dimension", dimension, "metric", "cosine",
                "metadata", Map.of("purpose", "patient-safe appointment operations")), true);
    }

    public String scrape(String url) throws IOException, InterruptedException {
        Map<String, Object> data = post("/v1/web/scrape", Map.of("url", url, "format", "markdown"), false);
        Object content = data.get("content");
        if (!(content instanceof String text)) throw new InfraiException("INVALID_RESPONSE", "Scrape response has no content", 200);
        return text;
    }

    public List<Double> embed(String input) throws IOException, InterruptedException {
        Map<String, Object> data = post("/v1/embeddings", Map.of("input", input, "model", "text-embedding-3-small"), false);
        List<Object> rows = Json.array(data.get("data"));
        Map<String, Object> first = Json.object(rows.get(0));
        return Json.array(first.get("embedding")).stream().map(value -> ((Number) value).doubleValue()).toList();
    }

    public Map<String, Object> upsert(String id, List<Double> embedding, Map<String, Object> metadata)
            throws IOException, InterruptedException {
        return post("/v1/vector/upsert", Map.of("collection", config.collection(), "vectors",
                List.of(Map.of("id", id, "values", embedding, "metadata", metadata))), true);
    }

    public Map<String, Object> createNightlySchedule() throws IOException, InterruptedException {
        return post("/v1/cron/create", Map.of("cron_expr", "0 2 * * *",
                "task", config.publicBaseUrl() + "/internal/nightly-reindex"), true);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, boolean idempotent)
            throws IOException, InterruptedException {
        String requestId = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)));
            if (idempotent) builder.header("Idempotency-Key", requestId);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = Json.object(Json.parse(response.body()));
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = Json.object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")), String.valueOf(error.get("message")), response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport response " + response.statusCode());
            return Json.object(envelope.get("data"));
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> { try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException ignored) { return 1000L << attempt; } })
                .orElse(1000L << attempt);
    }

    public static final class InfraiException extends IOException {
        private final String code;
        private final int status;
        InfraiException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
        public String code() { return code; }
        public int status() { return status; }
    }
}

