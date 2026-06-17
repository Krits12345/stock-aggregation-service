package com.stockaggregator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line client for the candle aggregation service.
 *
 * <p>Calls GET /api/v1/candles and prints the candles to the console. Parameters
 * come from CLI flags and/or a JSON config file (flags win). See {@link ClientParams}.
 *
 * <pre>
 *   mvn -q exec:java -Dexec.args="--symbol RELIANCE --timeframe 15m \
 *       --start '2026-01-01 09:15:00' --end '2026-01-01 15:30:00'"
 *
 *   mvn -q exec:java -Dexec.args="--config config.json"
 * </pre>
 */
public class StockClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        try {
            ClientParams params = ClientParams.resolve(args);
            render(fetch(params));
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static JsonNode fetch(ClientParams p) throws Exception {
        String url = p.baseUrl + "/api/v1/candles?" + query(p);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(p.timeoutSeconds))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalArgumentException("could not reach " + url + ": " + e.getMessage());
        }

        if (response.statusCode() != 200) {
            throw new IllegalArgumentException(
                    "HTTP " + response.statusCode() + ": " + messageOf(response.body()));
        }
        return MAPPER.readTree(response.body());
    }

    /** Build the URL-encoded query string from the resolved parameters. */
    private static String query(ClientParams p) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("symbol", p.symbol);
        q.put("timeframe", p.timeframe);
        q.put("start_date", p.startDate);
        q.put("end_date", p.endDate);
        if (p.page != null) q.put("page", p.page);
        if (p.pageSize != null) q.put("page_size", p.pageSize);

        List<String> parts = new ArrayList<>();
        q.forEach((k, v) -> parts.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return String.join("&", parts);
    }

    /** Pull a friendly message out of an error response body, falling back to the raw text. */
    private static String messageOf(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // not JSON; use the raw body
        }
        return body;
    }

    private static void render(JsonNode data) {
        JsonNode candles = data.path("candles");
        int total = data.path("pagination").path("total_candles")
                .asInt(data.path("count").asInt(candles.size()));

        System.out.println("=== Fetched Candle Data ===");
        System.out.printf("Symbol: %s | Timeframe: %s | Total Candles: %d%n",
                data.path("symbol").asText(), data.path("timeframe").asText(), total);
        System.out.println();

        int i = 1;
        for (JsonNode c : candles) {
            System.out.printf("%3d %s | O: %-10s | H: %-10s | L: %-10s | C: %-10s | V: %d%n",
                    i++,
                    c.path("datetime").asText(),
                    c.path("open").asText(),
                    c.path("high").asText(),
                    c.path("low").asText(),
                    c.path("close").asText(),
                    c.path("volume").asLong());
        }

        JsonNode pg = data.path("pagination");
        if (pg.path("total_pages").asInt(1) > 1) {
            System.out.printf("%n[page %d/%d | page_size %d]%n",
                    pg.path("page").asInt(), pg.path("total_pages").asInt(), pg.path("page_size").asInt());
        }
        System.out.println("===========================");
    }
}
