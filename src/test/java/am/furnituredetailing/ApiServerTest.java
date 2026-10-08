package am.furnituredetailing;

import am.furnituredetailing.claude.ClaudeGateway;
import am.furnituredetailing.claude.ClaudeProperties;
import am.furnituredetailing.design.DesignService;
import am.furnituredetailing.web.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Full HTTP round trip with a fake Claude. */
class ApiServerTest {

    static class FakeClaude implements ClaudeGateway {
        final List<String> prompts = new ArrayList<>();
        final List<Integer> imageCounts = new ArrayList<>();
        String answer = """
                Вот разбивка:
                ```json
                {"summary":"Прихожая","modules":[
                  {"type":"wardrobe","name":"Л: шкаф","W":500,"H":1650,"D":580,"doors":1,"rod":true},
                  {"type":"bogus","W":99999,"doors":40}
                ],"notes":["зеркало не входит"]}
                ```""";

        @Override
        public String ask(String prompt, List<ImageInput> images) {
            prompts.add(prompt);
            imageCounts.add(images.size());
            return answer;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    private FakeClaude fake;
    private ApiServer server;

    @BeforeEach
    void start() throws Exception {
        fake = new FakeClaude();
        var props = new ClaudeProperties("test-key", "claude-test", null, 0, Duration.ofSeconds(5), 3);
        server = new ApiServer(new DesignService(fake, props, mapper), props, mapper).start("127.0.0.1", 0);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void analyzeSendsPhotoAndReturnsNormalizedModules() throws Exception {
        var res = post("/api/analyze", """
                {"width":1900,"height":2700,"depth":580,"notes":"прихожая",
                 "images":[{"mediaType":"image/jpeg","data":"AQID"}]}""");
        assertEquals(200, res.statusCode(), res.body());
        JsonNode p = mapper.readTree(res.body());
        assertEquals("Прихожая", p.path("summary").asText());
        assertEquals(2, p.path("modules").size());
        assertEquals(500, p.path("modules").get(0).path("W").asInt());
        assertTrue(p.path("modules").get(0).path("rod").asBoolean());
        assertEquals("wardrobe", p.path("modules").get(1).path("type").asText());
        assertEquals(3600, p.path("modules").get(1).path("W").asInt());
        assertEquals(6, p.path("modules").get(1).path("doors").asInt());
        assertEquals(List.of(1), fake.imageCounts);
        assertTrue(fake.prompts.get(0).contains("ширина 1900, высота 2700, глубина 580"));
        assertEquals(0, p.path("modules").get(0).path("x").asInt());
        assertEquals(500, p.path("modules").get(1).path("x").asInt());   // auto-placed to the right
        assertTrue(p.path("drawingSvg").asText().startsWith("<svg"));
    }

    @Test
    void drawingEndpointReturnsSvg() throws Exception {
        var res = post("/api/drawing", """
                {"thickness":16,"title":"Тест","modules":[{"type":"dresser","name":"Комод","W":800,"H":850,"D":450,"drawers":4,"x":0,"y":0}]}""");
        assertEquals(200, res.statusCode());
        assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("image/svg+xml"));
        assertTrue(res.body().contains("ЛДСП 16 мм"));
        assertTrue(res.body().contains(">Тест</text>"));
        assertEquals(400, post("/api/drawing", "{\"modules\":[]}").statusCode());
    }

    @Test
    void rejectsEmptyRequest() throws Exception {
        var res = post("/api/analyze", "{}");
        assertEquals(400, res.statusCode());
        assertTrue(fake.prompts.isEmpty());
    }

    @Test
    void rejectsUnsupportedImageType() throws Exception {
        var res = post("/api/analyze", "{\"images\":[{\"mediaType\":\"application/pdf\",\"data\":\"AQ==\"}]}");
        assertEquals(400, res.statusCode());
    }

    @Test
    void answerWithoutJsonIsBadGateway() throws Exception {
        fake.answer = "Не могу разобрать фото.";
        var res = post("/api/analyze", "{\"notes\":\"шкаф\"}");
        assertEquals(502, res.statusCode());
        assertTrue(mapper.readTree(res.body()).has("error"));
    }

    @Test
    void normalizesPastedJson() throws Exception {
        var res = post("/api/proposal/normalize", "{\"modules\":[{\"type\":\"rack\",\"W\":900}]}");
        assertEquals(200, res.statusCode());
        JsonNode m = mapper.readTree(res.body()).path("modules").get(0);
        assertEquals(900, m.path("W").asInt());
        assertEquals(1800, m.path("H").asInt());
    }

    @Test
    void statusAndUi() throws Exception {
        var status = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/status")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(mapper.readTree(status.body()).path("claudeConfigured").asBoolean());
        var ui = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("Раскрой ЛДСП"));
    }
}
