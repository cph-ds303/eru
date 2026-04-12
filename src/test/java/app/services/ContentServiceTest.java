package app.services;

import app.dtos.requests.ContentRequestDTO;
import app.dtos.responses.ContentDTO;
import app.entities.Content;
import app.entities.enums.ContentType;
import app.entities.enums.ReactionType;
import app.exceptions.ApiException;
import app.persistence.daos.ContentDAO;
import app.persistence.daos.UserInteractionDAO;
import app.entities.User;
import app.entities.UserInteraction;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentServiceTest {

    @Test
    void createShouldTrimInputAndReturnCreatedContent() {
        FakeContentDAO contentDAO = new FakeContentDAO();
        ContentService service = new ContentService(contentDAO);

        ContentRequestDTO request = new ContentRequestDTO(
                "  Did you know?  ",
                "  Honey never spoils.  ",
                ContentType.FACT,
                "  Science  ",
                "  National Geographic  ",
                "  Unknown Author  "
        );

        ContentDTO created = service.create(request);

        assertEquals(1, created.id());
        assertEquals("Did you know?", created.title());
        assertEquals("Honey never spoils.", created.body());
        assertEquals(ContentType.FACT, created.contentType());
        assertEquals("Science", created.category());
        assertEquals("National Geographic", created.source());
        assertEquals("Unknown Author", created.author());
        assertTrue(created.active());
        assertNotNull(created.createdAt());
    }

    @Test
    void getAllShouldSupportTypeAndActiveFiltering() {
        FakeContentDAO contentDAO = new FakeContentDAO();
        contentDAO.seed(content(1, "First", ContentType.FACT, true, LocalDateTime.now().minusDays(2)));
        contentDAO.seed(content(2, "Second", ContentType.THEORY, true, LocalDateTime.now().minusDays(1)));
        contentDAO.seed(content(3, "Third", ContentType.FACT, false, LocalDateTime.now()));

        ContentService service = new ContentService(contentDAO);

        List<ContentDTO> result = service.getAll(ContentType.FACT, true);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).id());
        assertEquals("First", result.get(0).title());
    }

    @Test
    void getFeedForUserShouldExcludeContentWithExistingInteractionsAndOnlyReturnActiveContent() {
        FakeContentDAO contentDAO = new FakeContentDAO();
        FakeInteractionDAO interactionDAO = new FakeInteractionDAO();
        contentDAO.seed(content(1, "Seen", ContentType.FACT, true, LocalDateTime.now().minusDays(2)));
        contentDAO.seed(content(2, "Unseen newest", ContentType.FACT, true, LocalDateTime.now()));
        contentDAO.seed(content(3, "Inactive unseen", ContentType.FACT, false, LocalDateTime.now().minusHours(1)));
        interactionDAO.seed(interaction(1, 99, 1, ReactionType.VIEW));

        ContentService service = new ContentService(contentDAO, interactionDAO);

        List<ContentDTO> result = service.getFeedForUser(99, ContentType.FACT);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).id());
        assertEquals("Unseen newest", result.get(0).title());
    }

    @Test
    void updateShouldModifyExistingContent() {
        FakeContentDAO contentDAO = new FakeContentDAO();
        contentDAO.seed(content(7, "Old title", ContentType.FACT, true, LocalDateTime.now().minusHours(3)));
        ContentService service = new ContentService(contentDAO);

        ContentRequestDTO request = new ContentRequestDTO(
                "Updated title",
                "Updated body",
                ContentType.QUOTE,
                "",
                "Updated source",
                ""
        );

        ContentDTO updated = service.update(7, request);

        assertEquals(7, updated.id());
        assertEquals("Updated title", updated.title());
        assertEquals("Updated body", updated.body());
        assertEquals(ContentType.QUOTE, updated.contentType());
        assertNull(updated.category());
        assertEquals("Updated source", updated.source());
        assertNull(updated.author());
    }

    @Test
    void deleteShouldRemoveExistingContent() {
        FakeContentDAO contentDAO = new FakeContentDAO();
        contentDAO.seed(content(4, "Delete me", ContentType.FACT, true, LocalDateTime.now()));
        ContentService service = new ContentService(contentDAO);

        service.delete(4);

        assertTrue(contentDAO.getById(4).isEmpty());
    }

    @Test
    void getByIdShouldThrowNotFoundWhenMissing() {
        ContentService service = new ContentService(new FakeContentDAO());

        ApiException exception = assertThrows(ApiException.class, () -> service.getById(999));

        assertEquals(404, exception.getCode());
        assertEquals("Content not found with id 999", exception.getMessage());
    }

    @Test
    void createShouldRejectMissingTitle() {
        ContentService service = new ContentService(new FakeContentDAO());
        ContentRequestDTO request = new ContentRequestDTO(
                " ",
                "Body",
                ContentType.FACT,
                null,
                null,
                null
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.create(request));

        assertEquals(400, exception.getCode());
        assertEquals("Title is required", exception.getMessage());
    }

    private static Content content(
            Integer id,
            String title,
            ContentType contentType,
            boolean active,
            LocalDateTime createdAt
    ) {
        return Content.builder()
                .id(id)
                .title(title)
                .body(title + " body")
                .contentType(contentType)
                .category("General")
                .source("Source")
                .author("Author")
                .active(active)
                .createdAt(createdAt)
                .build();
    }

    private static UserInteraction interaction(Integer id, Integer userId, Integer contentId, ReactionType reactionType) {
        User user = new User("user" + userId, "secret123");
        user.setId(userId);

        Content content = Content.builder()
                .id(contentId)
                .title("Seen content")
                .body("Seen content body")
                .contentType(ContentType.FACT)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return UserInteraction.builder()
                .id(id)
                .user(user)
                .content(content)
                .reactionType(reactionType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static class FakeContentDAO extends ContentDAO {
        private final Map<Integer, Content> storage = new LinkedHashMap<>();
        private int nextId = 1;

        FakeContentDAO() {
            super((EntityManagerFactory) null);
        }

        void seed(Content content) {
            storage.put(content.getId(), cloneContent(content));
            nextId = Math.max(nextId, content.getId() + 1);
        }

        @Override
        public Content create(Content content) {
            Content copy = cloneContent(content);
            copy.setId(nextId++);
            if (copy.getCreatedAt() == null) {
                copy.setCreatedAt(LocalDateTime.now());
            }
            copy.setActive(true);
            storage.put(copy.getId(), copy);
            return cloneContent(copy);
        }

        @Override
        public List<Content> getAll() {
            return storage.values().stream()
                    .map(FakeContentDAO::cloneContent)
                    .sorted(Comparator.comparing(Content::getId))
                    .toList();
        }

        @Override
        public List<Content> getAllActive() {
            return storage.values().stream()
                    .filter(Content::isActive)
                    .map(FakeContentDAO::cloneContent)
                    .toList();
        }

        @Override
        public Optional<Content> getById(Integer id) {
            Content content = storage.get(id);
            return Optional.ofNullable(content).map(FakeContentDAO::cloneContent);
        }

        @Override
        public List<Content> getByType(ContentType type) {
            return storage.values().stream()
                    .filter(content -> content.getContentType() == type)
                    .map(FakeContentDAO::cloneContent)
                    .toList();
        }

        @Override
        public Content update(Content updatedContent) {
            storage.put(updatedContent.getId(), cloneContent(updatedContent));
            return cloneContent(updatedContent);
        }

        @Override
        public boolean delete(Integer id) {
            return storage.remove(id) != null;
        }

        private static Content cloneContent(Content source) {
            return Content.builder()
                    .id(source.getId())
                    .title(source.getTitle())
                    .body(source.getBody())
                    .contentType(source.getContentType())
                    .category(source.getCategory())
                    .source(source.getSource())
                    .author(source.getAuthor())
                    .active(source.isActive())
                    .createdAt(source.getCreatedAt())
                    .build();
        }
    }

    private static class FakeInteractionDAO extends UserInteractionDAO {
        private final Map<Integer, UserInteraction> storage = new LinkedHashMap<>();

        FakeInteractionDAO() {
            super((EntityManagerFactory) null);
        }

        void seed(UserInteraction interaction) {
            storage.put(interaction.getId(), interaction);
        }

        @Override
        public List<UserInteraction> getByUserId(Integer userId) {
            return storage.values().stream()
                    .filter(interaction -> interaction.getUser().getId().equals(userId))
                    .toList();
        }
    }
}
