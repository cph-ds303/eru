package app.integration;

import app.config.hibernate.HibernateTestUtil;
import app.controllers.AuthController;
import app.controllers.ContentController;
import app.controllers.InteractionController;
import app.persistence.daos.ContentDAO;
import app.persistence.daos.UserDAO;
import app.persistence.daos.UserInteractionDAO;
import app.routes.Routes;
import app.security.JwtUtil;
import app.services.AuthService;
import app.services.ContentService;
import app.services.InteractionService;
import app.support.TestApiFactory;
import io.javalin.Javalin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers(disabledWithoutDocker = true)
class AuthContentRoutesRestAssuredTest {
    private static final String JWT_SECRET = "integration-test-secret";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eru_test")
            .withUsername("postgres")
            .withPassword("postgres");

    private static EntityManagerFactory emf;
    private static Javalin app;
    private static AuthService authService;
    private static int port;

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

        ContentDAO contentDAO = new ContentDAO(emf);
        ContentController contentController = new ContentController(new ContentService(contentDAO, new UserInteractionDAO(emf)));
        InteractionController interactionController = new InteractionController(
                new InteractionService(new UserInteractionDAO(emf), new UserDAO(emf), contentDAO)
        );

        port = randomPort();
        app = TestApiFactory.createApp(authController, contentController, interactionController).start(port);

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = Routes.API_CONTEXT_PATH;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterAll
    static void tearDown() {
        RestAssured.reset();
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
    void registerShouldCreateUserAndReturnJwt() {
        given()
                .contentType(ContentType.JSON)
                .body(registerPayload("student1", "student1@example.com"))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(201)
                .body("username", equalTo("student1"))
                .body("userId", equalTo(1))
                .body("token", not(blankOrNullString()));
    }

    @Test
    void adminCanCreateAndReadContent() {
        String token = registerAdminAndReturnToken("admin1");

        Integer contentId =
                given()
                        .contentType(ContentType.JSON)
                        .auth().oauth2(token)
                        .body(contentPayload("Did you know?", "Honey never spoils."))
                .when()
                        .post("/content")
                .then()
                        .statusCode(201)
                        .body("id", notNullValue())
                        .body("title", equalTo("Did you know?"))
                        .body("contentType", equalTo("FACT"))
                        .extract()
                        .path("id");

        given()
        .when()
                .get("/content/{id}", contentId)
        .then()
                .statusCode(200)
                .body("id", equalTo(contentId))
                .body("title", equalTo("Did you know?"))
                .body("body", equalTo("Honey never spoils."));
    }

    @Test
    void authenticatedUserCanFilterOwnInteractions() {
        String token = registerAdminAndReturnToken("student4");

        Integer bookmarkedContentId =
                given()
                        .contentType(ContentType.JSON)
                        .auth().oauth2(token)
                        .body(contentPayload("Saved title", "Saved body"))
                .when()
                        .post("/content")
                .then()
                        .statusCode(201)
                        .extract()
                        .path("id");

        Integer likedContentId =
                given()
                        .contentType(ContentType.JSON)
                        .auth().oauth2(token)
                        .body(contentPayload("Liked title", "Liked body"))
                .when()
                        .post("/content")
                .then()
                        .statusCode(201)
                        .extract()
                        .path("id");

        given()
                .contentType(ContentType.JSON)
                .auth().oauth2(token)
                .body(Map.of("reactionType", "BOOKMARK"))
        .when()
                .post("/content/{id}/interactions", bookmarkedContentId)
        .then()
                .statusCode(200)
                .body("reactionType", equalTo("BOOKMARK"));

        given()
                .contentType(ContentType.JSON)
                .auth().oauth2(token)
                .body(Map.of("reactionType", "LIKE"))
        .when()
                .post("/content/{id}/interactions", likedContentId)
        .then()
                .statusCode(200)
                .body("reactionType", equalTo("LIKE"));

        given()
                .auth().oauth2(token)
                .queryParam("reactionType", "BOOKMARK")
        .when()
                .get("/interactions/me")
        .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].reactionType", equalTo("BOOKMARK"))
                .body("[0].content.title", equalTo("Saved title"));
    }

    @Test
    void feedShouldExcludeContentUserHasAlreadyInteractedWith() {
        String adminToken = registerAdminAndReturnToken("admin-feed");

        Integer seenContentId =
                given()
                        .contentType(ContentType.JSON)
                        .auth().oauth2(adminToken)
                        .body(contentPayload("Seen title", "Seen body"))
                .when()
                        .post("/content")
                .then()
                        .statusCode(201)
                        .extract()
                        .path("id");

        given()
                .contentType(ContentType.JSON)
                .auth().oauth2(adminToken)
                .body(contentPayload("Fresh title", "Fresh body"))
        .when()
                .post("/content")
        .then()
                .statusCode(201);

        String userToken = registerAndReturnToken("student-feed");

        given()
                .contentType(ContentType.JSON)
                .auth().oauth2(userToken)
                .body(Map.of("reactionType", "VIEW"))
        .when()
                .post("/content/{id}/interactions", seenContentId)
        .then()
                .statusCode(200)
                .body("reactionType", equalTo("VIEW"));

        given()
                .auth().oauth2(userToken)
        .when()
                .get("/content/feed")
        .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Fresh title"));
    }

    private static String registerAdminAndReturnToken(String username) {
        registerAndReturnToken(username);
        authService.addRole(username, "ADMIN");

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", "secret123"))
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private static String registerAndReturnToken(String username) {
        return given()
                .contentType(ContentType.JSON)
                .body(registerPayload(username, username + "@example.com"))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(201)
                .extract()
                .path("token");
    }

    private static Map<String, Object> registerPayload(String username, String email) {
        return Map.of(
                "firstName", "Student",
                "lastName", "One",
                "email", email,
                "username", username,
                "password", "secret123"
        );
    }

    private static Map<String, Object> contentPayload(String title, String body) {
        return Map.of(
                "title", title,
                "body", body,
                "contentType", "FACT",
                "category", "General",
                "source", "Test",
                "author", "Tester"
        );
    }

    private static int randomPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate random port", e);
        }
    }
}
