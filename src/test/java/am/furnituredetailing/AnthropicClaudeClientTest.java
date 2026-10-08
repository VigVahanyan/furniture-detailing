package am.furnituredetailing;

import am.furnituredetailing.claude.AnthropicClaudeClient;
import am.furnituredetailing.claude.ClaudeException;
import am.furnituredetailing.claude.ClaudeGateway.ImageInput;
import am.furnituredetailing.claude.ClaudeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the client against a local stub of /v1/messages. */
class AnthropicClaudeClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenKey = new AtomicReference<>();

    private AnthropicClaudeClient start(int status, String reply) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            seenBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seenKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        var props = new ClaudeProperties("k-123", "claude-test", "http://127.0.0.1:" + server.getAddress().getPort(),
                1000, Duration.ofSeconds(5), 3);
        return new AnthropicClaudeClient(props, mapper);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsImageBlocksAndReadsText() throws Exception {
        var client = start(200, """
                {"content":[{"type":"text","text":"{\\"modules\\":[]}"}],"stop_reason":"end_turn"}""");

        String answer = client.ask("hello", List.of(new ImageInput("image/png", new byte[]{9, 8})));

        assertEquals("{\"modules\":[]}", answer);
        assertEquals("k-123", seenKey.get());
        JsonNode req = mapper.readTree(seenBody.get());
        assertEquals("claude-test", req.path("model").asText());
        JsonNode content = req.path("messages").get(0).path("content");
        assertEquals("image", content.get(0).path("type").asText());
        assertEquals("image/png", content.get(0).path("source").path("media_type").asText());
        assertEquals("CQg=", content.get(0).path("source").path("data").asText());
        assertEquals("hello", content.get(1).path("text").asText());
    }

    @Test
    void surfacesApiErrors() throws Exception {
        var client = start(401, """
                {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""");
        ClaudeException e = assertThrows(ClaudeException.class, () -> client.ask("x", List.of()));
        assertEquals(401, e.status());
        assertTrue(e.getMessage().contains("invalid x-api-key"));
    }
}
