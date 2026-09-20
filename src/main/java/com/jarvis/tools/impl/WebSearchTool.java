package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search tool using DuckDuckGo instant answer API (no key required).
 * SSRF-protected: blocks private/loopback IP ranges.
 */
@Component
public class WebSearchTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    @Value("${jarvis.ssrf.blocked-prefixes}")
    private String blockedPrefixesRaw;

    @Value("${jarvis.ssrf.allowed-schemes}")
    private String allowedSchemesRaw;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the web for current information, news, facts, or answers to questions. Returns a concise summary.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "The search query to look up")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Search query is required.");
        }

        try {
            // Use DuckDuckGo Instant Answer API (no key required)
            String targetUrl = "https://api.duckduckgo.com/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + "&format=json&no_html=1&skip_disambig=1";

            if (!isSsrfSafe(targetUrl)) {
                return ToolResult.failure("SSRF protection: blocked URL " + targetUrl);
            }

            Request req = new Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Jarvis-Assistant/1.0")
                    .build();

            try (Response response = http.newCall(req).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return ToolResult.failure("Search service returned " + response.code());
                }

                String body = response.body().string();
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(body);

                String abstract_ = root.path("Abstract").asText("");
                String abstractText = root.path("AbstractText").asText("");
                String answerText = root.path("Answer").asText("");
                String abstractSource = root.path("AbstractSource").asText("");
                String abstractUrl = root.path("AbstractURL").asText("");

                StringBuilder summary = new StringBuilder();
                List<Map<String, Object>> results = new ArrayList<>();

                if (!answerText.isEmpty()) {
                    summary.append(answerText);
                    results.add(Map.of("title", "Instant Answer", "snippet", answerText, "url", ""));
                } else if (!abstractText.isEmpty()) {
                    summary.append(abstractText);
                    if (!abstractSource.isEmpty()) summary.append(" (Source: ").append(abstractSource).append(")");
                    results.add(Map.of("title", abstractSource.isEmpty() ? "Search Result" : abstractSource,
                            "snippet", abstractText, "url", abstractUrl));
                } else {
                    // Parse related topics as fallback results
                    for (com.fasterxml.jackson.databind.JsonNode topic : root.path("RelatedTopics")) {
                        if (topic.has("Text") && !topic.path("Text").asText().isEmpty()) {
                            String text = topic.path("Text").asText();
                            String url = topic.path("FirstURL").asText("");
                            results.add(Map.of("title", text.length() > 60 ? text.substring(0, 60) + "..." : text,
                                    "snippet", text, "url", url));
                            if (results.size() >= 4) break;
                        }
                    }
                    if (results.isEmpty()) {
                        String wikiFallback = searchWikipedia(query, results);
                        if (wikiFallback != null && !wikiFallback.isBlank()) {
                            summary.append(wikiFallback);
                        } else {
                            summary.append("No direct answer found for: ").append(query);
                        }
                    } else {
                        summary.append(results.get(0).get("snippet"));
                    }
                }

                return ToolResult.success(
                        summary.toString(),
                        Map.of("query", query, "results", results, "type", "search"),
                        null
                );
            }
        } catch (Exception e) {
            log.error("[WebSearch] Error searching for '{}': {}", query, e.getMessage());
            return ToolResult.failure("Search error: " + e.getMessage());
        }
    }

    private String searchWikipedia(String query, List<Map<String, Object>> results) {
        try {
            String url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                    + java.net.URLEncoder.encode(query, "UTF-8") + "&utf8=&format=json";
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Jarvis-Assistant/1.0")
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode r = om.readTree(resp.body().string());
                    com.fasterxml.jackson.databind.JsonNode searchArr = r.path("query").path("search");
                    if (searchArr.isArray() && searchArr.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(3, searchArr.size()); i++) {
                            com.fasterxml.jackson.databind.JsonNode item = searchArr.get(i);
                            String title = item.path("title").asText();
                            String snippet = item.path("snippet").asText().replaceAll("<[^>]+>", "");
                            if (!snippet.isBlank()) {
                                if (sb.length() > 0) sb.append(". ");
                                sb.append(snippet);
                                results.add(Map.of(
                                        "title", title,
                                        "snippet", snippet,
                                        "url", "https://en.wikipedia.org/wiki/" + title.replace(" ", "_")
                                ));
                            }
                        }
                        if (sb.length() > 0) return sb.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WebSearch] Wikipedia fallback error: {}", e.getMessage());
        }
        return null;
    }

    private boolean isSsrfSafe(String urlStr) {
        try {
            URL url = new URL(urlStr);
            String scheme = url.getProtocol().toLowerCase();
            List<String> allowedSchemes = Arrays.asList(allowedSchemesRaw.split(","));
            if (!allowedSchemes.contains(scheme)) return false;

            String host = url.getHost();
            InetAddress addr = InetAddress.getByName(host);
            String ip = addr.getHostAddress();

            List<String> blocked = Arrays.asList(blockedPrefixesRaw.split(","));
            for (String prefix : blocked) {
                if (ip.startsWith(prefix.strip())) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[SSRF] Could not validate URL {}: {}", urlStr, e.getMessage());
            return false;
        }
    }
}
