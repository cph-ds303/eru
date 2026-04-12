package app.controllers;

import app.dtos.responses.ElaborateResponseDTO;
import app.entities.Content;
import app.exceptions.ApiException;
import app.integration.openai.OpenAiService;
import app.persistence.daos.ContentDAO;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiController {
    private static final Logger logger = LoggerFactory.getLogger(AiController.class);

    private final OpenAiService openAiService;
    private final ContentDAO contentDAO;

    public AiController(OpenAiService openAiService, ContentDAO contentDAO) {
        this.openAiService = openAiService;
        this.contentDAO = contentDAO;
    }

    public void elaborate(Context ctx) {
        if (openAiService == null) {
            throw ApiException.configuration("AI integration is disabled. Set OPENAI_API_KEY to use /api/v1/content/{id}/elaborate");
        }

        Integer contentId = parseId(ctx.pathParam("id"));
        Content content = contentDAO.getById(contentId)
                .orElseThrow(() -> ApiException.notFound("Content not found with id " + contentId));

        logger.info("AI elaborate request contentId={} titleLength={} bodyLength={}",
                contentId,
                content.getTitle() != null ? content.getTitle().length() : 0,
                content.getBody() != null ? content.getBody().length() : 0);

        String explanation;
        try {
            explanation = openAiService.elaborate(content);
        } catch (RuntimeException e) {
            logger.error("AI elaborate failed", e);
            throw ApiException.internal("AI elaboration failed: " + e.getMessage());
        }

        ctx.json(new ElaborateResponseDTO(explanation));
    }

    private static Integer parseId(String rawId) {
        try {
            return Integer.valueOf(rawId);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Path parameter id must be a number");
        }
    }
}
