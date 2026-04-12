package app.controllers;

import app.dtos.internal.AuthenticatedUserDTO;
import app.dtos.requests.ContentRequestDTO;
import app.dtos.responses.ContentDTO;
import app.entities.enums.ContentType;
import app.exceptions.ApiException;
import app.services.ContentService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ContentController {
    private static final Logger logger = LoggerFactory.getLogger(ContentController.class);

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    public void create(Context ctx) {
        ContentRequestDTO request = ctx.bodyAsClass(ContentRequestDTO.class);
        logger.info("Create content request title={} type={}", request.title(), request.contentType());
        ContentDTO created = contentService.create(request);
        ctx.status(201).json(created);
    }

    public void getAll(Context ctx) {
        String typeParam = ctx.queryParam("type");
        String activeOnlyParam = ctx.queryParam("activeOnly");
        boolean activeOnly = Boolean.parseBoolean(activeOnlyParam);
        ContentType type = parseContentType(typeParam);

        List<ContentDTO> content = contentService.getAll(type, activeOnly);
        ctx.status(200).json(content);
    }

    public void getFeed(Context ctx) {
        AuthenticatedUserDTO authenticatedUser = getAuthenticatedUser(ctx);
        ContentType type = parseContentType(ctx.queryParam("type"));

        List<ContentDTO> content = contentService.getFeedForUser(authenticatedUser.userId(), type);
        ctx.status(200).json(content);
    }

    public void getById(Context ctx) {
        Integer id = parseId(ctx.pathParam("id"));
        ContentDTO content = contentService.getById(id);
        ctx.status(200).json(content);
    }

    public void update(Context ctx) {
        Integer id = parseId(ctx.pathParam("id"));
        ContentRequestDTO request = ctx.bodyAsClass(ContentRequestDTO.class);
        logger.info("Update content request id={} title={} type={}", id, request.title(), request.contentType());
        ContentDTO updated = contentService.update(id, request);
        ctx.status(200).json(updated);
    }

    public void delete(Context ctx) {
        Integer id = parseId(ctx.pathParam("id"));
        contentService.delete(id);
        ctx.status(204);
    }

    private static Integer parseId(String rawId) {
        try {
            return Integer.valueOf(rawId);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Path parameter id must be a number");
        }
    }

    private static ContentType parseContentType(String typeParam) {
        if (typeParam == null || typeParam.isBlank()) {
            return null;
        }

        try {
            return ContentType.valueOf(typeParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid content type: " + typeParam);
        }
    }

    private static AuthenticatedUserDTO getAuthenticatedUser(Context ctx) {
        AuthenticatedUserDTO user = ctx.attribute("user");
        if (user == null) {
            throw ApiException.unauthorized("No authenticated user found in request context");
        }
        return user;
    }
}
