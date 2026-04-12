package app.integration;

import app.config.hibernate.HibernateTestUtil;
import app.controllers.AuthController;
import app.controllers.ContentController;
import app.controllers.InteractionController;
import app.persistence.daos.ContentDAO;
import app.persistence.daos.UserDAO;
import app.persistence.daos.UserInteractionDAO;
import app.routes.Routes;
import app.services.AuthService;
import app.services.ContentService;
import app.services.InteractionService;
import app.security.JwtUtil;
import app.support.TestApiFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class AuthContentRoutesTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String JWT_SECRET = "integration-test-secret";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eru_test")
            .withUsername("postgres")
            .withPassword("postgres");

    private static EntityManagerFactory emf;
    private static Javalin app;
    private static int port;
    private static AuthService authService;
    private static ContentDAO contentDAO;

    @BeforeAll
    static void setUp() {
        POSTGRES.start();
        emf = HibernateTestUtil.createEntityManagerFactory(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        AuthController authController = new AuthController(
                new AuthService(new UserDAO(emf), new JwtUtil(JWT_SECRET))
        );
        authService = new AuthService(new UserDAO(emf), new JwtUtil(JWT_SECRET));
        contentDAO = new ContentDAO(emf);
        ContentController contentController = new ContentController(new ContentService(contentDAO, new UserInteractionDAO(emf)));
        InteractionController interactionController = new InteractionController(
                new InteractionService(new UserInteractionDAO(emf), new UserDAO(emf), contentDAO)
        );

        port = randomPort();
        app = TestApiFactory.createApp(authController, contentController, interactionController).start(port);
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
        if (emf != null) {
            emf.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void resetDatabase() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.createNativeQuery("TRUNCATE TABLE user_interactions, user_roles, roles, content, users RESTART IDENTITY CASCADE")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    @Test
    void registerAndLoginShouldReturnJwtAndUserData() throws Exception {
        HttpResponse<String> registerResponse = sendJsonRequest(
                "POST",
                "/auth/register",
                null,
                Map.of(
                        "firstName", "Student",
                        "lastName", "One",
                        "email", "student1@example.com",
                        "username", "student1",
                        "password", "secret123"
                )
        );

        assertEquals(201, registerResponse.statusCode());
        JsonNode registerJson = readJson(registerResponse);
        assertEquals("student1", registerJson.get("username").asText());
        assertTrue(registerJson.hasNonNull("token"));

        HttpResponse<String> loginResponse = sendJsonRequest(
                "POST",
                "/auth/login",
                null,
                Map.of("username", "student1", "password", "secret123")
        );

        assertEquals(200, loginResponse.statusCode());
        JsonNode loginJson = readJson(loginResponse);
        assertEquals("student1", loginJson.get("username").asText());
        assertEquals(1, loginJson.get("userId").asInt());
        assertTrue(loginJson.hasNonNull("token"));
    }

    @Test
    void logoutShouldRevokeToken() throws Exception {
        String token = registerAndReturnToken("student-logout");

        HttpResponse<String> logoutResponse = sendRequest("POST", "/auth/logout", token, null);
        assertEquals(204, logoutResponse.statusCode());

        HttpResponse<String> meResponse = sendRequest("GET", "/auth/me", token, null);
        assertEquals(401, meResponse.statusCode());
        JsonNode meJson = readJson(meResponse);
        assertEquals("AUTH_UNAUTHORIZED", meJson.get("errorCode").asText());
    }

    @Test
    void getMyInteractionsShouldReturnOnlyAuthenticatedUsersFilteredInteractions() throws Exception {
        String token = registerAdminAndReturnToken("student4");
        JsonNode bookmarkedContent = createContent(token, "Saved title", "Saved body");
        JsonNode likedContent = createContent(token, "Liked title", "Liked body");

        sendJsonRequest(
                "POST",
                "/content/" + bookmarkedContent.get("id").asInt() + "/interactions",
                token,
                Map.of("reactionType", "BOOKMARK")
        );
        sendJsonRequest(
                "POST",
                "/content/" + likedContent.get("id").asInt() + "/interactions",
                token,
                Map.of("reactionType", "LIKE")
        );

        HttpResponse<String> response = sendRequest(
                "GET",
                "/interactions/me?reactionType=BOOKMARK",
                token,
                null
        );

        assertEquals(200, response.statusCode());
        JsonNode json = readJson(response);
        assertEquals(1, json.size());
        assertEquals("BOOKMARK", json.get(0).get("reactionType").asText());
        assertEquals("Saved title", json.get(0).get("content").get("title").asText());
    }

    @Test
    void feedShouldExcludeContentUserHasAlreadyViewed() throws Exception {
        String adminToken = registerAdminAndReturnToken("admin-feed");
        JsonNode seenContent = createContent(adminToken, "Seen title", "Seen body");
        createContent(adminToken, "Fresh title", "Fresh body");

        String userToken = registerAndReturnToken("student-feed");

        HttpResponse<String> interactionResponse = sendJsonRequest(
                "POST",
                "/content/" + seenContent.get("id").asInt() + "/interactions",
                userToken,
                Map.of("reactionType", "VIEW")
        );
        assertEquals(200, interactionResponse.statusCode());

        HttpResponse<String> response = sendRequest("GET", "/content/feed", userToken, null);

        assertEquals(200, response.statusCode());
        JsonNode json = readJson(response);
        assertEquals(1, json.size());
        assertEquals("Fresh title", json.get(0).get("title").asText());
    }

    @Test
    void createContentShouldSucceedForAdminUser() throws Exception {
        String token = registerAdminAndReturnToken("student2");

        HttpResponse<String> createResponse = sendJsonRequest(
                "POST",
                "/content",
                token,
                Map.of(
                        "title", "Did you know?",
                        "body", "Honey never spoils.",
                        "contentType", "FACT",
                        "category", "Science",
                        "source", "Smithsonian",
                        "author", "Unknown"
                )
        );

        assertEquals(201, createResponse.statusCode());
        JsonNode createJson = readJson(createResponse);
        assertEquals("Did you know?", createJson.get("title").asText());
        assertEquals("FACT", createJson.get("contentType").asText());
        assertTrue(createJson.get("active").asBoolean());
        assertNotNull(createJson.get("id"));
    }

    @Test
    void createContentShouldFailWithoutToken() throws Exception {
        HttpResponse<String> response = sendJsonRequest(
                "POST",
                "/content",
                null,
                Map.of(
                        "title", "Unauthorized",
                        "body", "This should fail",
                        "contentType", "FACT"
                )
        );

        assertEquals(401, response.statusCode());
        JsonNode json = readJson(response);
        assertEquals("AUTH_UNAUTHORIZED", json.get("errorCode").asText());
        assertFalse(json.get("message").asText().isBlank());
    }

    @Test
    void getContentByIdShouldReturnCreatedContent() throws Exception {
        String token = registerAdminAndReturnToken("student3");
        JsonNode created = createContent(token, "Stored title", "Stored body");

        HttpResponse<String> response = sendRequest("GET", "/content/" + created.get("id").asInt(), null, null);

        assertEquals(200, response.statusCode());
        JsonNode json = readJson(response);
        assertEquals(created.get("id").asInt(), json.get("id").asInt());
        assertEquals("Stored title", json.get("title").asText());
        assertEquals("Stored body", json.get("body").asText());
    }

    @Test
    void getContentByIdShouldReturnNotFoundForMissingRecord() throws Exception {
        HttpResponse<String> response = sendRequest("GET", "/content/9999", null, null);

        assertEquals(404, response.statusCode());
        JsonNode json = readJson(response);
        assertEquals("RESOURCE_NOT_FOUND", json.get("errorCode").asText());
    }

    private static String registerAndReturnToken(String username) throws Exception {
        HttpResponse<String> response = sendJsonRequest(
                "POST",
                "/auth/register",
                null,
                Map.of(
                        "firstName", "Student",
                        "lastName", "One",
                        "email", username + "@example.com",
                        "username", username,
                        "password", "secret123"
                )
        );
        return readJson(response).get("token").asText();
    }

    private static String registerAdminAndReturnToken(String username) throws Exception {
        registerAndReturnToken(username);
        authService.addRole(username, "ADMIN");

        HttpResponse<String> loginResponse = sendJsonRequest(
                "POST",
                "/auth/login",
                null,
                Map.of("username", username, "password", "secret123")
        );
        return readJson(loginResponse).get("token").asText();
    }

    private static JsonNode createContent(String token, String title, String body) throws Exception {
        HttpResponse<String> response = sendJsonRequest(
                "POST",
                "/content",
                token,
                Map.of(
                        "title", title,
                        "body", body,
                        "contentType", "FACT",
                        "category", "General",
                        "source", "Test",
                        "author", "Tester"
                )
        );
        return readJson(response);
    }

    private static HttpResponse<String> sendJsonRequest(
            String method,
            String path,
            String bearerToken,
            Object body
    ) throws Exception {
        return sendRequest(method, path, bearerToken, OBJECT_MAPPER.writeValueAsString(body));
    }

    private static HttpResponse<String> sendRequest(
            String method,
            String path,
            String bearerToken,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + Routes.API_CONTEXT_PATH + path))
                .header("Content-Type", "application/json");

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode readJson(HttpResponse<String> response) throws IOException {
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static int randomPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate random port", e);
        }
    }
}
