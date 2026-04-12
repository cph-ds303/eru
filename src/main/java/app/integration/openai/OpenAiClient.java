package app.integration.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenAiClient {

    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String elaborateContent(String title, String body) {
        try {
            String prompt = """
                    You are helping a learning platform explain short educational content.
                    Explain the content in 3-5 sentences.
                    Use simple, clear language.
                    Focus on learning, not casual chat.
                    Do not invent facts beyond what can reasonably be inferred from the content.
                    If the content is unclear, incomplete, or potentially misleading, say that clearly before explaining what you can.

                    Title: %s

                    Content:
                    %s
                    """.formatted(title, body);

            String requestBody = objectMapper.createObjectNode()
                    .put("model", model)
                    .put("input", prompt)
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());

            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode part : content) {
                            JsonNode text = part.path("text");
                            if (!text.isMissingNode() && !text.isNull()) {
                                return text.asText();
                            }
                        }
                    }
                }
            }

            throw new RuntimeException("Could not parse OpenAI response: " + response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI request was interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }
}
