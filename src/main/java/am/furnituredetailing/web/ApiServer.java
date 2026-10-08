package am.furnituredetailing.web;

import am.furnituredetailing.claude.ClaudeException;
import am.furnituredetailing.claude.ClaudeGateway.ImageInput;
import am.furnituredetailing.claude.ClaudeProperties;
import am.furnituredetailing.design.DesignProposal;
import am.furnituredetailing.design.DesignRequest;
import am.furnituredetailing.design.ModuleSpec;
import am.furnituredetailing.drawing.AssemblyDrawing;
import am.furnituredetailing.design.DesignService;
import am.furnituredetailing.design.InvalidDesignInputException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * HTTP layer on the JDK server: serves the constructor UI and the JSON API.
 *
 * <pre>
 * GET  /                      constructor UI
 * GET  /api/status            {claudeConfigured, model, maxImages}
 * POST /api/analyze           {width,height,depth,notes,thickness,images:[{mediaType,data,kind?"room"}]} → proposal + drawingSvg
 * POST /api/proposal/normalize   any text containing proposal JSON → normalized proposal
 * POST /api/drawing           {modules, thickness, title} → assembly drawing (image/svg+xml)
 * </pre>
 */
public class ApiServer {

    private static final System.Logger log = System.getLogger(ApiServer.class.getName());
    static final int MAX_BODY_BYTES = 30 * 1024 * 1024;

    private final DesignService service;
    private final ClaudeProperties props;
    private final ObjectMapper mapper;
    private HttpServer server;

    public ApiServer(DesignService service, ClaudeProperties props, ObjectMapper mapper) {
        this.service = service;
        this.props = props;
        this.mapper = mapper;
    }

    public ApiServer start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
        return this;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            switch (method + " " + path) {
                case "GET /", "GET /index.html" -> sendResource(ex, "/static/index.html", "text/html; charset=utf-8");
                case "GET /api/status" -> sendJson(ex, 200, Map.of(
                        "claudeConfigured", props.configured(), "model", props.model(), "maxImages", props.maxImages()));
                case "POST /api/analyze" -> sendJson(ex, 200, analyze(readBody(ex)));
                case "POST /api/proposal/normalize" -> sendJson(ex, 200, service.parse(new String(readBody(ex), StandardCharsets.UTF_8)));
                case "POST /api/drawing" -> sendSvg(ex, drawing(readBody(ex)));
                default -> sendJson(ex, 404, Map.of("error", "Not found: " + method + " " + path));
            }
        } catch (InvalidDesignInputException e) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (PayloadTooLarge e) {
            sendJson(ex, 413, Map.of("error", "Запрос слишком большой. Загрузите фото до 5 МБ."));
        } catch (JsonProcessingException e) {
            sendJson(ex, 400, Map.of("error", "Некорректный JSON в запросе."));
        } catch (ClaudeException e) {
            sendClaudeError(ex, e);
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.ERROR, "Unhandled error on " + path, e);
            sendJson(ex, 500, Map.of("error", "Внутренняя ошибка сервера."));
        } finally {
            ex.close();
        }
    }

    private Object analyze(byte[] body) throws IOException {
        AnalyzeRequest req = mapper.readValue(body, AnalyzeRequest.class);
        List<AnalyzeRequest.Image> sent = req.images() == null ? List.<AnalyzeRequest.Image>of() : req.images().stream()
                .filter(i -> i != null && i.data() != null && !i.data().isBlank())
                .toList();
        List<ImageInput> images = sent.stream().filter(i -> !i.isRoom()).map(ApiServer::decode).toList();
        List<ImageInput> room = sent.stream().filter(AnalyzeRequest.Image::isRoom).map(ApiServer::decode).toList();
        int thickness = req.thickness() == null || req.thickness() <= 0 ? 18 : req.thickness();
        DesignProposal p = service.analyze(new DesignRequest(req.width(), req.height(), req.depth(), req.notes(), thickness), images, room);
        return AnalysisResult.of(p, AssemblyDrawing.render(p.modules(), thickness, null));
    }

    private String drawing(byte[] body) throws IOException {
        DrawingRequest req = mapper.readValue(body, DrawingRequest.class);
        if (req.modules() == null || req.modules().isEmpty()) {
            throw new InvalidDesignInputException("Нет модулей для чертежа.");
        }
        List<ModuleSpec> mods = req.modules().stream().filter(Objects::nonNull).limit(40).toList();
        int thickness = req.thickness() == null || req.thickness() <= 0 ? 18 : req.thickness();
        return AssemblyDrawing.render(mods, thickness, req.title());
    }

    private static void sendSvg(HttpExchange ex, String svg) throws IOException {
        byte[] out = svg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "image/svg+xml; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, out.length);
        ex.getResponseBody().write(out);
    }

    private static ImageInput decode(AnalyzeRequest.Image img) {
        String data = img.data();
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);   // tolerate data: URLs
        String type = img.mediaType() == null ? "" : img.mediaType().toLowerCase();
        if ("image/jpg".equals(type)) type = "image/jpeg";
        try {
            return new ImageInput(type, Base64.getDecoder().decode(data));
        } catch (IllegalArgumentException e) {
            throw new InvalidDesignInputException("Фото повреждено при загрузке. Выберите его ещё раз.");
        }
    }

    private void sendClaudeError(HttpExchange ex, ClaudeException e) throws IOException {
        int status = switch (e.status()) {
            case 401, 403 -> 503;      // our key problem, not the user's
            case 429, 529 -> 429;
            case 400 -> 400;
            default -> 502;
        };
        String msg = switch (e.status()) {
            case 401, 403 -> "Claude не подключён: проверьте ANTHROPIC_API_KEY. (" + e.getMessage() + ")";
            case 429, 529 -> "Claude перегружен или исчерпан лимит API. Попробуйте через минуту.";
            default -> "Ошибка Claude: " + e.getMessage();
        };
        sendJson(ex, status, Map.of("error", msg));
    }

    private static byte[] readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
            if (data.length > MAX_BODY_BYTES) throw new PayloadTooLarge();
            return data;
        }
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] out = mapper.writeValueAsBytes(body instanceof Map<?, ?> m ? new LinkedHashMap<>(m) : body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
    }

    private static void sendResource(HttpExchange ex, String name, String type) throws IOException {
        try (InputStream in = ApiServer.class.getResourceAsStream(name)) {
            if (in == null) {
                byte[] msg = "UI not found".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, msg.length);
                ex.getResponseBody().write(msg);
                return;
            }
            byte[] out = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", type);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
        }
    }

    private static final class PayloadTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
