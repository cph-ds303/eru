package app.routes;

import app.controllers.ContentController;
import app.security.AppRole;
import io.javalin.router.JavalinDefaultRoutingApi;

public class ContentRoutes {

    private ContentRoutes() {
    }

    public static void register(JavalinDefaultRoutingApi routes, ContentController contentController) {
        routes.post("/content", contentController::create, AppRole.ADMIN);
        routes.get("/content/feed", contentController::getFeed, AppRole.USER);
        routes.get("/content", contentController::getAll, AppRole.ANYONE);
        routes.get("/content/{id}", contentController::getById, AppRole.ANYONE);
        routes.put("/content/{id}", contentController::update, AppRole.ADMIN);
        routes.delete("/content/{id}", contentController::delete, AppRole.ADMIN);
    }
}
