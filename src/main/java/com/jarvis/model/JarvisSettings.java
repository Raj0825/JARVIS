package com.jarvis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "settings")
public class JarvisSettings {

    public enum LlmProvider {
        OPENAI, ANTHROPIC, GEMINI, OLLAMA, MOCK
    }

    @Id
    private String id;

    @Indexed(unique = true)
    @Builder.Default
    private String userId = "default";

    @Builder.Default
    private LlmProvider provider = LlmProvider.MOCK;

    @Builder.Default
    private String model = "jarvis-mock-v1";

    /** Stored as-is (no encryption in v1 — kept server-side, never sent to frontend) */
    private String apiKey;

    /** Base URL override for Ollama or compatible APIs */
    private String apiBaseUrl;

    @Builder.Default
    private double temperature = 0.7;

    @Builder.Default
    private String systemPrompt = "You are Jarvis, an intelligent AI assistant created for a holographic personal assistant interface. Respond concisely and helpfully. When using tools, be precise about the action you are taking. Address the user respectfully.";

    /** Whether effectful (write) tools are permitted */
    @Builder.Default
    private boolean allowWrites = false;

    /** TTS voice name (browser speechSynthesis voiceURI or 'default') */
    @Builder.Default
    private String ttsVoice = "default";

    @Builder.Default
    private double ttsPitch = 0.85;

    @Builder.Default
    private double ttsRate = 1.0;

    @Builder.Default
    private String sttLanguage = "en-US";

    @Builder.Default
    private String theme = "cyan";

    @Builder.Default
    private String userName = "Raj Shah";

    @Builder.Default
    private String userCallSign = "Mr. Raj";
}
