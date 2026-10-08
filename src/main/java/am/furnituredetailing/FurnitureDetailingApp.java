package am.furnituredetailing;

import am.furnituredetailing.claude.AnthropicClaudeClient;
import am.furnituredetailing.claude.ClaudeProperties;
import am.furnituredetailing.design.DesignService;
import am.furnituredetailing.web.ApiServer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Entry point. Environment: ANTHROPIC_API_KEY, optional CLAUDE_MODEL, PORT (default 8080), HOST (default 127.0.0.1).
 */
public final class FurnitureDetailingApp {

    private FurnitureDetailingApp() {
    }

    public static void main(String[] args) throws Exception {
        var env = System.getenv();
        ClaudeProperties props = ClaudeProperties.fromEnv(env);
        ObjectMapper mapper = new ObjectMapper();
        DesignService service = new DesignService(new AnthropicClaudeClient(props, mapper), props, mapper);

        String host = env.getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
        ApiServer server = new ApiServer(service, props, mapper).start(host, port);

        System.out.printf("Furniture Detailing -> http://%s:%d   (model: %s, Claude %s)%n", host, server.port(), props.model(),
                props.configured() ? "connected" : "NOT configured: set ANTHROPIC_API_KEY");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
