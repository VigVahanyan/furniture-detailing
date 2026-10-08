package am.furnituredetailing.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

/**
 * Minimal Anthropic Messages API client (POST /v1/messages) on the JDK HttpClient.
 */
public class AnthropicClaudeClient implements ClaudeGateway {

    private static final System.Logger log = System.getLogger(AnthropicClaudeClient.class.getName());
    private static final String API_VERSION = "2023-06-01";

    private final ClaudeProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AnthropicClaudeClient(ClaudeProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
    }

    @Override
    public String ask(String prompt, List<ImageInput> images) {
        if (!props.configured()) {
            throw new ClaudeException(401, "ANTHROPIC_API_KEY is not set");
        }
        String body = buildRequest(prompt, images);
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/v1/messages"))
                .timeout(props.timeout())
                .header("content-type", "application/json")
                .header("x-api-key", props.apiKey())
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ClaudeException("Cannot reach Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeException("Request interrupted", e);
        }
        return parseResponse(response.statusCode(), response.body());
    }

    String buildRequest(String prompt, List<ImageInput> images) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", props.model());
        root.put("max_tokens", props.maxTokens());
        ArrayNode content = mapper.createArrayNode();
        for (ImageInput img : images) {
            ObjectNode block = content.addObject();
            block.put("type", "image");
            ObjectNode source = block.putObject("source");
            source.put("type", "base64");
            source.put("media_type", img.mediaType());
            source.put("data", Base64.getEncoder().encodeToString(img.data()));
        }
        content.addObject().put("type", "text").put("text", prompt);
        ObjectNode message = root.putArray("messages").addObject();
        message.put("role", "user");
        message.set("content", content);
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String parseResponse(int status, String body) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (IOException e) {
            throw new ClaudeException(status, "Unreadable response from Anthropic API (HTTP " + status + ")");
        }
        if (status / 100 != 2) {
            String msg = json.path("error").path("message").asText("HTTP " + status);
            log.log(System.Logger.Level.WARNING, "Anthropic API error " + status + ": " + msg);
            throw new ClaudeException(status, msg);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : json.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            String stop = json.path("stop_reason").asText("?");
            StringBuilder types = new StringBuilder();
            for (JsonNode block : json.path("content")) types.append(types.isEmpty() ? "" : ",").append(block.path("type").asText("?"));
            log.log(System.Logger.Level.WARNING, "Claude returned no text: stop_reason=" + stop + ", blocks=[" + types + "], usage=" + json.path("usage"));
            String why = switch (stop) {
                case "refusal" -> "Claude отказался отвечать на этот запрос (попробуйте другое фото или формулировку)";
                case "max_tokens" -> "ответ не поместился в лимит токенов: увеличьте CLAUDE_MAX_TOKENS";
                default -> "stop_reason=" + stop;
            };
            throw new ClaudeException(502, "Claude returned no text: " + why);
        }
        if ("max_tokens".equals(json.path("stop_reason").asText())) {
            log.log(System.Logger.Level.WARNING, "Claude answer hit max_tokens; JSON may be incomplete");
        }
        return text.toString();
    }
}
