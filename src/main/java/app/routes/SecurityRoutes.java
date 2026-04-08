package app.routes;

import app.security.AppRole;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Map;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    public static void register(JavalinDefaultRoutingApi routes) {
        routes.get("/", ctx -> ctx.json(Map.of(
                "application", "ERU API",
                "status", "running"
        )), AppRole.ANYONE);
        routes.get("/health", ctx -> ctx.json(Map.of("status", "ok")), AppRole.ANYONE);
    }
}
