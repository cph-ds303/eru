package app.config;

import app.controllers.AuthController;
import app.exceptions.ApiException;
import app.routes.Routes;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.util.legacy.LegacyAccessManagerKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ApplicationConfig {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);
    private static final int DEFAULT_PORT = 7070;

    public static Javalin start(int port) {
        ApplicationConfig applicationConfig = new ApplicationConfig();
        Javalin app = applicationConfig.createApp();
        app.start(port);
        logger.info("ERU API started on port {} with base path {}", port, Routes.API_CONTEXT_PATH);
        return app;
    }

    public Javalin createApp() {
        DependencyContainer container = new DependencyContainer();
        return createApp(container);
    }

    public Javalin createApp(DependencyContainer container) {
        Routes routes = container.getRoutes();
        AuthController authController = container.getAuthController();

        Javalin app = Javalin.create(config -> {
            configurePlugins(config);
            configureRoutes(config, routes);
            configureRequestLogging(config);
            configureExceptionHandlers(config);
        });

        LegacyAccessManagerKt.legacyAccessManager(app, (handler, ctx, routeRoles) -> {
            Set<String> allowedRoles = routeRoles.stream()
                    .map(role -> role.toString().toUpperCase())
                    .collect(Collectors.toSet());
            ctx.attribute("allowed_roles", allowedRoles);
            authController.authenticate(ctx);
            authController.authorize(ctx);
            try {
                handler.handle(ctx);
            } catch (Exception e) {
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(e);
            }
        });

        return app;
    }

    public void start() {
        start(DEFAULT_PORT);
    }

    private static void configurePlugins(JavalinConfig config) {
        config.router.contextPath = Routes.API_CONTEXT_PATH;
        config.bundledPlugins.enableRouteOverview("/routes");
    }

    private static void configureRoutes(JavalinConfig config, Routes routes) {
        routes.register(config.routes);
    }

    private static void configureRequestLogging(JavalinConfig config) {
        config.routes.beforeMatched(ctx ->
                logger.info("-> {} {}", ctx.method(), ctx.path())
        );
        config.routes.afterMatched(ctx ->
                logger.info("<- {} {} {}", ctx.method(), ctx.path(), ctx.statusCode())
        );
    }

    private static void configureExceptionHandlers(JavalinConfig config) {
        config.routes.exception(ApiException.class, (e, ctx) -> {
            logger.warn("API error {} {} {} {}", ctx.method(), ctx.path(), e.getCode(), e.getErrorCode());
            ctx.status(e.getCode()).json(Map.of(
                    "errorCode", e.getErrorCode().name(),
                    "message", e.getMessage()
            ));
        });
        config.routes.exception(IllegalStateException.class, (e, ctx) -> {
            logger.error("Illegal state on {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).json(Map.of(
                    "errorCode", "INTERNAL_ERROR",
                    "message", e.getMessage()
            ));
        });
        config.routes.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled error on {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).json(Map.of(
                    "errorCode", "INTERNAL_ERROR",
                    "message", "Internal server error"
            ));
        });
    }
}
