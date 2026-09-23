package com.jarvis.service;

import com.jarvis.model.JarvisSettings;
import com.jarvis.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing Jarvis settings persisted in MongoDB.
 * Auto-creates default settings if none exist.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    @Autowired
    private SettingsRepository settingsRepository;

    /**
     * Returns settings for the given user, creating defaults if needed.
     */
    public JarvisSettings getSettings(String userId) {
        return settingsRepository.findByUserId(userId).map(s -> {
            boolean changed = false;
            if (s.getUserName() == null || s.getUserName().isBlank()) {
                s.setUserName("Raj Shah");
                changed = true;
            }
            if (s.getUserCallSign() == null || s.getUserCallSign().isBlank()) {
                s.setUserCallSign("Mr. Raj");
                changed = true;
            }
            if (s.getModel() != null && (s.getModel().equals("gemini-2.0-flash") || s.getModel().contains("3.5") || s.getModel().equalsIgnoreCase("gemini-3.5-flash-lite"))) {
                s.setModel("gemini-3.6-flash");
                changed = true;
            }
            return changed ? settingsRepository.save(s) : s;
        }).orElseGet(() -> {
            log.info("[SettingsService] Creating default settings for user '{}'", userId);
            JarvisSettings defaults = JarvisSettings.builder()
                    .userId(userId)
                    .userName("Raj Shah")
                    .userCallSign("Mr. Raj")
                    .model("gemini-3.6-flash")
                    .build();
            return settingsRepository.save(defaults);
        });
    }

    /**
     * Updates settings (partial — only non-null fields).
     * API key is accepted but must never be returned to frontend callers.
     */
    public JarvisSettings updateSettings(String userId, JarvisSettings update) {
        JarvisSettings existing = getSettings(userId);

        if (update.getProvider() != null) existing.setProvider(update.getProvider());
        if (update.getModel() != null && !update.getModel().isBlank()) {
            String m = update.getModel().trim();
            if (m.equals("gemini-2.0-flash") || m.contains("3.5") || m.equalsIgnoreCase("gemini-3.5-flash-lite")) {
                m = "gemini-3.6-flash";
            }
            existing.setModel(m);
        }
        if (update.getApiKey() != null && !update.getApiKey().isBlank() && !update.getApiKey().contains("•")) {
            existing.setApiKey(update.getApiKey().trim());
        }
        if (update.getApiBaseUrl() != null) existing.setApiBaseUrl(update.getApiBaseUrl());
        if (update.getTemperature() > 0) existing.setTemperature(update.getTemperature());
        if (update.getSystemPrompt() != null && !update.getSystemPrompt().isBlank()) existing.setSystemPrompt(update.getSystemPrompt());
        if (update.getTtsVoice() != null) existing.setTtsVoice(update.getTtsVoice());
        if (update.getTtsPitch() > 0) existing.setTtsPitch(update.getTtsPitch());
        if (update.getTtsRate() > 0) existing.setTtsRate(update.getTtsRate());
        if (update.getSttLanguage() != null && !update.getSttLanguage().isBlank()) existing.setSttLanguage(update.getSttLanguage());
        if (update.getTheme() != null) existing.setTheme(update.getTheme());
        if (update.getUserName() != null && !update.getUserName().isBlank()) existing.setUserName(update.getUserName());
        if (update.getUserCallSign() != null && !update.getUserCallSign().isBlank()) existing.setUserCallSign(update.getUserCallSign());

        // allowWrites is a boolean — always update it from the request
        existing.setAllowWrites(update.isAllowWrites());

        JarvisSettings saved = settingsRepository.save(existing);
        log.info("[SettingsService] Settings updated for user '{}': provider={}, model={}, allowWrites={}",
                userId, saved.getProvider(), saved.getModel(), saved.isAllowWrites());
        return saved;
    }

    /**
     * Returns a settings object safe to send to frontend (API key masked).
     */
    public JarvisSettings getSafeSettings(String userId) {
        JarvisSettings s = getSettings(userId);
        JarvisSettings safe = JarvisSettings.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .provider(s.getProvider())
                .model(s.getModel())
                .apiKey(s.getApiKey() != null && !s.getApiKey().isBlank() ? "••••••••••••" : null)
                .apiBaseUrl(s.getApiBaseUrl())
                .temperature(s.getTemperature())
                .systemPrompt(s.getSystemPrompt())
                .allowWrites(s.isAllowWrites())
                .ttsVoice(s.getTtsVoice())
                .ttsPitch(s.getTtsPitch())
                .ttsRate(s.getTtsRate())
                .sttLanguage(s.getSttLanguage())
                .theme(s.getTheme())
                .userName(s.getUserName())
                .userCallSign(s.getUserCallSign())
                .build();
        return safe;
    }
}
