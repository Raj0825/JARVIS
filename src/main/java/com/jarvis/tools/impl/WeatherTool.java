package com.jarvis.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Real-time weather tool using wttr.in (no API key required).
 * Returns live temperature, condition, humidity, and wind.
 */
@Component
public class WeatherTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "Get live, real-time weather conditions, temperature, humidity, and forecast for any city or location worldwide.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "location", Map.of(
                                "type", "string",
                                "description", "The city and optional country/state (e.g. 'Amravati, Maharashtra', 'London', 'New York')"
                        )
                ),
                "required", List.of("location")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String location = (String) params.get("location");
        if (location == null || location.isBlank()) {
            return ToolResult.failure("Location is required.");
        }

        try {
            String encoded = URLEncoder.encode(location.trim(), StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encoded + "?format=j1";

            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Jarvis-Assistant/1.0")
                    .build();

            try (Response response = http.newCall(req).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return ToolResult.failure("Weather service returned HTTP " + response.code());
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode current = root.path("current_condition").get(0);
                if (current == null) {
                    return ToolResult.failure("Could not parse weather for " + location);
                }

                String condition = current.path("weatherDesc").get(0).path("value").asText("Unknown");
                String tempC = current.path("temp_C").asText();
                String tempF = current.path("temp_F").asText();
                String feelsLikeC = current.path("FeelsLikeC").asText();
                String feelsLikeF = current.path("FeelsLikeF").asText();
                String humidity = current.path("humidity").asText();
                String windKmph = current.path("windspeedKmph").asText();

                String resolvedArea = location;
                JsonNode areaNode = root.path("nearest_area").get(0);
                if (areaNode != null) {
                    String areaName = areaNode.path("areaName").get(0).path("value").asText();
                    String region = areaNode.path("region").get(0).path("value").asText();
                    String country = areaNode.path("country").get(0).path("value").asText();
                    resolvedArea = areaName + ", " + region + " (" + country + ")";
                }

                String summary = String.format(
                        "Weather in %s: %s, %s°C (%s°F). Feels like %s°C (%s°F). Humidity: %s%%. Wind: %s km/h.",
                        resolvedArea, condition, tempC, tempF, feelsLikeC, feelsLikeF, humidity, windKmph
                );

                return ToolResult.success(
                        summary,
                        Map.of(
                                "location", resolvedArea,
                                "condition", condition,
                                "temp_C", tempC,
                                "temp_F", tempF,
                                "humidity", humidity,
                                "wind_kmph", windKmph
                        ),
                        null
                );
            }
        } catch (Exception e) {
            log.error("[WeatherTool] Failed to fetch weather for '{}': {}", location, e.getMessage());
            return ToolResult.failure("Failed to fetch weather: " + e.getMessage());
        }
    }
}
