package com.stockaggregator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the request parameters from command-line flags and/or a JSON config
 * file. A flag always wins over the matching config value.
 *
 * <pre>
 *   --config       path to a JSON config file
 *   --base-url     service base URL (default http://localhost:8080)
 *   --symbol       e.g. RELIANCE
 *   --timeframe    1m, 5m, 15m, 30m, 1h, 1d
 *   --start        start_date, 'yyyy-MM-dd HH:mm:ss'
 *   --end          end_date,   'yyyy-MM-dd HH:mm:ss'
 *   --page         page number
 *   --page-size    candles per page
 *   --timeout      HTTP timeout in seconds (default 15)
 * </pre>
 */
public class ClientParams {

    String baseUrl = "http://localhost:8080";
    String symbol;
    String timeframe;
    String startDate;
    String endDate;
    String page;
    String pageSize;
    int timeoutSeconds = 15;

    static ClientParams resolve(String[] args) {
        Map<String, String> flags = parseFlags(args);
        Map<String, Object> cfg = loadConfig(flags.get("config"));

        ClientParams p = new ClientParams();
        p.baseUrl = stripTrailingSlash(pick(flags.get("base-url"), cfg, "base_url", p.baseUrl));
        p.symbol = pick(flags.get("symbol"), cfg, "symbol", null);
        p.timeframe = pick(flags.get("timeframe"), cfg, "timeframe", null);
        p.startDate = pick(flags.get("start"), cfg, "start_date", null);
        p.endDate = pick(flags.get("end"), cfg, "end_date", null);
        p.page = pick(flags.get("page"), cfg, "page", null);
        p.pageSize = pick(flags.get("page-size"), cfg, "page_size", null);
        if (flags.containsKey("timeout")) {
            p.timeoutSeconds = (int) Double.parseDouble(flags.get("timeout"));
        }

        StringBuilder missing = new StringBuilder();
        if (isBlank(p.symbol)) missing.append("symbol ");
        if (isBlank(p.timeframe)) missing.append("timeframe ");
        if (isBlank(p.startDate)) missing.append("start_date ");
        if (isBlank(p.endDate)) missing.append("end_date ");
        if (missing.length() > 0) {
            throw new IllegalArgumentException(
                    "missing parameter(s): " + missing.toString().trim()
                            + " (pass via --flags or --config)");
        }
        return p;
    }

    /** Reads "--key value" pairs into a map keyed by the flag name (no "--"). */
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--") && i + 1 < args.length) {
                flags.put(arg.substring(2), args[++i]);
            }
        }
        return flags;
    }

    private static Map<String, Object> loadConfig(String path) {
        if (path == null) {
            return Map.of();
        }
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("config file not found: " + path);
        }
        try {
            JsonNode node = new ObjectMapper().readTree(Files.readAllBytes(file));
            Map<String, Object> cfg = new HashMap<>();
            node.fields().forEachRemaining(e -> cfg.put(e.getKey(), e.getValue().asText()));
            return cfg;
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read config file: " + e.getMessage());
        }
    }

    private static String pick(String flag, Map<String, Object> cfg, String key, String fallback) {
        if (flag != null) {
            return flag;
        }
        Object fromCfg = cfg.get(key);
        return fromCfg != null ? fromCfg.toString() : fallback;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
