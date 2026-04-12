package app.routes;

import app.controllers.AiController;
import app.security.AppRole;
import io.javalin.router.JavalinDefaultRoutingApi;

public class AiRoutes {

    private AiRoutes() {
    }

    public static void register(JavalinDefaultRoutingApi routes, AiController aiController) {
        routes.post("/content/{id}/elaborate", aiController::elaborate, AppRole.USER);
    }
}
