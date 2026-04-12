package app.config;

import app.controllers.AiController;
import app.controllers.AuthController;
import app.controllers.ContentController;
import app.controllers.InteractionController;
import app.integration.openai.OpenAiClient;
import app.integration.openai.OpenAiService;
import app.persistence.daos.ContentDAO;
import app.persistence.daos.UserDAO;
import app.persistence.daos.UserInteractionDAO;
import app.routes.Routes;
import app.security.JwtUtil;
import app.services.AuthService;
import app.services.ContentService;
import app.services.InteractionService;
import app.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DependencyContainer {
    private static final Logger logger = LoggerFactory.getLogger(DependencyContainer.class);

    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_JWT_SECRET = "dev-secret-change-me";

    private final AuthController authController;
    private final Routes routes;

    public DependencyContainer() {
        OpenAiService openAiService = createOpenAiService();
        JwtUtil jwtUtil = new JwtUtil(resolveJwtSecret());

        ContentDAO contentDAO = new ContentDAO();
        UserDAO userDAO = new UserDAO();
        UserInteractionDAO userInteractionDAO = new UserInteractionDAO();

        AiController aiController = new AiController(openAiService, contentDAO);
        ContentController contentController = new ContentController(new ContentService(contentDAO, userInteractionDAO));
        InteractionController interactionController = new InteractionController(
                new InteractionService(userInteractionDAO, userDAO, contentDAO)
        );

        this.authController = new AuthController(new AuthService(userDAO, jwtUtil));
        this.routes = new Routes(aiController, authController, contentController, interactionController);
    }

    public AuthController getAuthController() {
        return authController;
    }

    public Routes getRoutes() {
        return routes;
    }

    private OpenAiService createOpenAiService() {
        String apiKey = resolveApiKey();
        if (apiKey == null) {
            logger.warn("OPENAI_API_KEY was not found. /api/v1/content/{id}/elaborate will return a configuration error until it is set.");
            return null;
        }

        String model = resolveOpenAiModel();
        logger.info("OpenAI integration enabled with model {}", model);
        OpenAiClient client = new OpenAiClient(apiKey, model);
        return new OpenAiService(client);
    }

    private String resolveApiKey() {
        String envApiKey = resolveFirstEnvironmentValue("OPENAI_API_KEY", "ERU_API_KEY", "eru_api_key");
        if (envApiKey != null && !envApiKey.isBlank()) {
            return envApiKey.trim();
        }

        String configApiKey = Utils.getOptionalPropertyValue("OPENAI_API_KEY", CONFIG_FILE);
        if (configApiKey != null && !configApiKey.isBlank()) {
            return configApiKey.trim();
        }

        return null;
    }

    private String resolveOpenAiModel() {
        String envModel = System.getenv("OPENAI_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            return envModel.trim();
        }

        String configModel = Utils.getOptionalPropertyValue("OPENAI_MODEL", CONFIG_FILE);
        if (configModel != null && !configModel.isBlank()) {
            return configModel.trim();
        }

        return DEFAULT_MODEL;
    }

    private String resolveJwtSecret() {
        String envJwtSecret = System.getenv("JWT_SECRET");
        if (envJwtSecret != null && !envJwtSecret.isBlank()) {
            return envJwtSecret.trim();
        }

        String configJwtSecret = Utils.getOptionalPropertyValue("JWT_SECRET", CONFIG_FILE);
        if (configJwtSecret != null && !configJwtSecret.isBlank()) {
            return configJwtSecret.trim();
        }

        logger.warn("JWT_SECRET was not found. Falling back to a local development secret.");
        return DEFAULT_JWT_SECRET;
    }

    private static String resolveFirstEnvironmentValue(String... keys) {
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
