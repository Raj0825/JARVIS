package com.jarvis.llm;

import com.jarvis.model.JarvisSettings;
import com.jarvis.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory that creates the correct LlmClient implementation based on current user settings.
 */
@Component
public class LlmFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmFactory.class);

    @Autowired
    private SettingsService settingsService;

    public LlmClient createClient() {
        JarvisSettings settings = settingsService.getSettings("default");
        return createClientFromSettings(settings);
    }

    public LlmClient createClientFromSettings(JarvisSettings settings) {
        if (settings == null || settings.getProvider() == null) {
            log.info("[LlmFactory] No settings found, using Mock client");
            return new MockLlmClient();
        }

        String apiKey = settings.getApiKey();
        String model = settings.getModel();
        double temperature = settings.getTemperature();

        log.info("[LlmFactory] Creating client for provider={}, model={}", settings.getProvider(), model);

        return switch (settings.getProvider()) {
            case OPENAI -> {
                String baseUrl = settings.getApiBaseUrl() != null && !settings.getApiBaseUrl().isBlank()
                        ? settings.getApiBaseUrl() : "https://api.openai.com/v1";
                yield new OpenAiCompatibleClient(baseUrl, apiKey, model, temperature);
            }
            case ANTHROPIC -> new AnthropicClient(apiKey, model, temperature);
            case GEMINI -> new GeminiClient(apiKey, model, temperature);
            case OLLAMA -> {
                String ollamaUrl = settings.getApiBaseUrl() != null && !settings.getApiBaseUrl().isBlank()
                        ? settings.getApiBaseUrl() : "http://localhost:11434/v1";
                yield new OpenAiCompatibleClient(ollamaUrl, "ollama", model, temperature);
            }
            case MOCK -> new MockLlmClient();
        };
    }
}
