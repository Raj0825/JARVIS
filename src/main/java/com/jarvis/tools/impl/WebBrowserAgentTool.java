package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Autonomous Web Browser & Research Agent Tool.
 * Autonomously navigates to websites, inspects content, scrapes clean text/tables,
 * and extracts data (pricing, articles, documentation, GitHub repos, flight info, etc.).
 */
@Component
public class WebBrowserAgentTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(WebBrowserAgentTool.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 8000;

    @Override
    public String getName() {
        return "browse_web_page";
    }

    @Override
    public String getDescription() {
        return "Autonomous web browsing and deep content extraction agent: fetches and parses web pages, reads documentation, extracts product prices, articles, GitHub repositories, or search results from the web.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of(
                                "type", "string",
                                "description", "The target website URL to browse and extract content from (e.g. 'https://en.wikipedia.org/wiki/Artificial_intelligence', 'https://github.com/torvalds/linux')"
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Search query if no direct URL is provided (e.g. 'flight prices Mumbai to Delhi', 'latest SpaceX Starship news', 'Sony WH-1000XM5 price')"
                        ),
                        "selector", Map.of(
                                "type", "string",
                                "description", "Optional CSS selector to target specific page elements (e.g. 'article', 'main', '.price', '#readme')"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String url = (String) params.get("url");
        String query = (String) params.get("query");
        String selector = (String) params.get("selector");

        try {
            // 1. If URL is given, directly browse and scrape it
            if (url != null && !url.isBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                log.info("[BrowserAgent] Browsing URL: {}", url);
                return scrapeUrl(url, selector);
            }

            // 2. If query is provided, search DuckDuckGo HTML and pull top results
            if (query != null && !query.isBlank()) {
                log.info("[BrowserAgent] Searching web for query: '{}'", query);
                return searchAndExtract(query);
            }

            return ToolResult.failure("Either 'url' or 'query' must be provided for browse_web_page.");
        } catch (Exception e) {
            log.error("[BrowserAgent] Web browsing error: {}", e.getMessage(), e);
            return ToolResult.failure("Failed to browse web page: " + e.getMessage());
        }
    }

    private ToolResult scrapeUrl(String url, String selector) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .followRedirects(true)
                    .get();

            // Clean irrelevant clutter
            doc.select("script, style, nav, footer, header, noscript, iframe, .ads, .cookie-banner, .advertisement").remove();

            String pageTitle = doc.title();
            String metaDesc = "";
            Element metaTag = doc.selectFirst("meta[name=description], meta[property=og:description]");
            if (metaTag != null) {
                metaDesc = metaTag.attr("content");
            }

            String extractedText;
            if (selector != null && !selector.isBlank()) {
                Elements selected = doc.select(selector);
                extractedText = selected.text();
            } else {
                // Focus on main content area if present
                Element main = doc.selectFirst("main, article, #content, #main-content, .post-content, .article-body");
                extractedText = (main != null) ? main.text() : doc.body().text();
            }

            // Clean and truncate content
            extractedText = cleanText(extractedText, 2500);

            String summary = "Successfully retrieved content from " + pageTitle + " (" + url + ").";
            return ToolResult.success(
                    summary,
                    Map.of(
                            "title", pageTitle,
                            "url", url,
                            "metaDescription", metaDesc,
                            "content", extractedText
                    ),
                    null
            );
        } catch (Exception e) {
            log.warn("[BrowserAgent] Could not scrape URL '{}': {}", url, e.getMessage());
            return ToolResult.failure("Could not scrape URL " + url + ": " + e.getMessage());
        }
    }

    private ToolResult searchAndExtract(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get();

            Elements results = searchDoc.select(".result");
            StringBuilder sb = new StringBuilder();
            int count = 0;

            for (Element res : results) {
                if (count >= 4) break;
                Element titleLink = res.selectFirst(".result__title a");
                Element snippet = res.selectFirst(".result__snippet");

                if (titleLink != null) {
                    String title = titleLink.text();
                    String link = titleLink.absUrl("href");
                    String desc = snippet != null ? snippet.text() : "";

                    sb.append(String.format(Locale.ROOT, "[%d] %s\nURL: %s\nSnippet: %s\n\n", count + 1, title, link, desc));
                    count++;
                }
            }

            if (count == 0) {
                return ToolResult.failure("No web search results found for query: " + query);
            }

            String summary = "Found " + count + " relevant web results for '" + query + "'.";
            return ToolResult.success(
                    summary,
                    Map.of("query", query, "results", sb.toString()),
                    null
            );
        } catch (Exception e) {
            log.warn("[BrowserAgent] DuckDuckGo search failed: {}", e.getMessage());
            return ToolResult.failure("Web search failed: " + e.getMessage());
        }
    }

    private String cleanText(String text, int maxChars) {
        if (text == null) return "";
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() > maxChars) {
            return normalized.substring(0, maxChars) + "... [Content truncated]";
        }
        return normalized;
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
