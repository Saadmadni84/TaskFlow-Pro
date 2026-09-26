package com.taskflow.taskflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the optional AI-assisted dependency suggestion engine.
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * Whether the AI dependency suggestion capability is enabled.
     * Default: false (core application operates completely offline without AI).
     */
    private boolean enabled = false;

    /**
     * Selected AI provider ("gemini", "mock", "noop").
     */
    private String provider = "gemini";

    /**
     * Target LLM model name (e.g., "gemini-1.5-flash").
     */
    private String model = "gemini-1.5-flash";

    /**
     * API key for the configured LLM provider.
     */
    private String apiKey = "";

    /**
     * Maximum HTTP request timeout in seconds when calling the LLM provider.
     */
    private int timeoutSeconds = 10;

    /**
     * Maximum number of candidate suggestions returned to the caller.
     */
    private int maxSuggestions = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxSuggestions() {
        return maxSuggestions;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }
}
