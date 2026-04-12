package app.services;

import app.dtos.requests.ContentRequestDTO;
import app.dtos.responses.ContentDTO;
import app.entities.Content;
import app.entities.enums.ContentType;
import app.exceptions.ApiException;
import app.persistence.daos.ContentDAO;
import app.persistence.daos.UserInteractionDAO;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ContentService {
    private final ContentDAO contentDAO;
    private final UserInteractionDAO interactionDAO;

    public ContentService(ContentDAO contentDAO) {
        this(contentDAO, null);
    }

    public ContentService(ContentDAO contentDAO, UserInteractionDAO interactionDAO) {
        this.contentDAO = contentDAO;
        this.interactionDAO = interactionDAO;
    }

    public ContentDTO create(ContentRequestDTO request) {
        validateRequest(request);

        Content created = contentDAO.create(Content.builder()
                .title(request.title().trim())
                .body(request.body().trim())
                .contentType(request.contentType())
                .category(normalizeNullable(request.category()))
                .source(normalizeNullable(request.source()))
                .author(normalizeNullable(request.author()))
                .build());

        return ContentDTO.fromEntity(created);
    }

    public List<ContentDTO> getAll(ContentType type, boolean activeOnly) {
        List<Content> content = resolveContentList(type, activeOnly);

        return toSortedContentDtos(content);
    }

    public List<ContentDTO> getFeedForUser(Integer userId, ContentType type) {
        if (userId == null) {
            throw ApiException.badRequest("User id is required");
        }
        if (interactionDAO == null) {
            throw ApiException.internal("Interaction DAO is not configured for feed lookups");
        }

        Set<Integer> seenContentIds = interactionDAO.getByUserId(userId).stream()
                .map(interaction -> interaction.getContent().getId())
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        List<Content> content = resolveContentList(type, true).stream()
                .filter(item -> !seenContentIds.contains(item.getId()))
                .toList();

        return toSortedContentDtos(content);
    }

    private static List<ContentDTO> toSortedContentDtos(List<Content> content) {
        return content.stream()
                .sorted(Comparator.comparing(Content::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ContentDTO::fromEntity)
                .toList();
    }

    public ContentDTO getById(Integer id) {
        return ContentDTO.fromEntity(getExistingContent(id));
    }

    public ContentDTO update(Integer id, ContentRequestDTO request) {
        validateRequest(request);

        Content existing = getExistingContent(id);
        existing.setTitle(request.title().trim());
        existing.setBody(request.body().trim());
        existing.setContentType(request.contentType());
        existing.setCategory(normalizeNullable(request.category()));
        existing.setSource(normalizeNullable(request.source()));
        existing.setAuthor(normalizeNullable(request.author()));

        Content updated = contentDAO.update(existing);
        return ContentDTO.fromEntity(updated);
    }

    public void delete(Integer id) {
        getExistingContent(id);
        boolean deleted = contentDAO.delete(id);
        if (!deleted) {
            throw ApiException.notFound("Content not found with id " + id);
        }
    }

    private List<Content> resolveContentList(ContentType type, boolean activeOnly) {
        if (type != null && activeOnly) {
            return contentDAO.getByType(type).stream()
                    .filter(Content::isActive)
                    .toList();
        }
        if (type != null) {
            return contentDAO.getByType(type);
        }
        if (activeOnly) {
            return contentDAO.getAllActive();
        }
        return contentDAO.getAll();
    }

    private Content getExistingContent(Integer id) {
        if (id == null) {
            throw ApiException.badRequest("Content id is required");
        }

        return contentDAO.getById(id)
                .orElseThrow(() -> ApiException.notFound("Content not found with id " + id));
    }

    private static void validateRequest(ContentRequestDTO request) {
        if (request == null) {
            throw ApiException.badRequest("Request body is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw ApiException.badRequest("Title is required");
        }
        if (request.body() == null || request.body().isBlank()) {
            throw ApiException.badRequest("Body is required");
        }
        if (request.contentType() == null) {
            throw ApiException.badRequest("Content type is required");
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
