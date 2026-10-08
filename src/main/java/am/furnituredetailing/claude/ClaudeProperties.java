package am.furnituredetailing.claude;

import java.time.Duration;
import java.util.Map;

/**
 * Connection settings for the Anthropic Messages API, read from environment variables.
 */
public record ClaudeProperties(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens,
        Duration timeout,
        int maxImages
) {
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    public ClaudeProperties {
        if (model == null || model.isBlank()) model = DEFAULT_MODEL;
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.anthropic.com";
        if (maxTokens <= 0) maxTokens = 16000;
        if (timeout == null) timeout = Duration.ofSeconds(120);
        if (maxImages <= 0) maxImages = 3;
    }

    /**
     * ANTHROPIC_API_KEY (required for analysis), CLAUDE_MODEL, CLAUDE_BASE_URL, CLAUDE_MAX_TOKENS, CLAUDE_TIMEOUT_SECONDS.
     */
    public static ClaudeProperties fromEnv(Map<String, String> env) {
        return new ClaudeProperties(
                env.get("ANTHROPIC_API_KEY"),
                env.get("CLAUDE_MODEL"),
                env.get("CLAUDE_BASE_URL"),
                parseInt(env.get("CLAUDE_MAX_TOKENS"), 16000),
                Duration.ofSeconds(parseInt(env.get("CLAUDE_TIMEOUT_SECONDS"), 120)),
                3);
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null || s.isBlank() ? def : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
