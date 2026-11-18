package com.betterme.social;

import com.betterme.social.repository.FriendshipRepository;
import com.betterme.social.repository.SocialCommentRepository;
import com.betterme.social.repository.SocialLikeRepository;
import com.betterme.social.repository.SocialPhotoRepository;
import com.betterme.social.repository.SocialPostRepository;
import com.betterme.social.service.SocialService;

import com.betterme.user.*;
import com.betterme.wisdom.DailyQuoteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// All tests use a disposable in-memory database, never the team's Supabase database.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:social;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.config.import=", "app.remember-me-key=social-test-only-key"
})
@AutoConfigureMockMvc
class SocialIntegrationTest {
    @Autowired SocialService social;
    @Autowired UserRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired SocialPostRepository posts;
    @Autowired SocialPhotoRepository photos;
    @Autowired SocialLikeRepository likes;
    @Autowired SocialCommentRepository comments;
    @Autowired MockMvc mvc;
    @MockitoBean DailyQuoteService wisdom;
    User a, b, c;

    @BeforeEach
    void setup() {
        comments.deleteAll(); likes.deleteAll(); photos.deleteAll(); posts.deleteAll();
        friendships.deleteAll(); users.deleteAll();
        a = makeUser("Alice", "alice@test.local");
        b = makeUser("Bob", "bob@test.local");
        c = makeUser("Charlie", "charlie@test.local");
        when(wisdom.random()).thenReturn(new DailyQuoteService.Quote("A small step.", "Test"));
    }

    User makeUser(String name, String email) {
        User user = new User(); user.setFullName(name); user.setEmail(email); user.setPasswordHash("test-only");
        return users.save(user);
    }

    void acceptFriendship() {
        social.requestFriend(a.getEmail(), b.getId());
        social.respond(b.getEmail(), social.requests(b.getEmail(), 0).items().getFirst().id(), true);
    }

    Long makePost() {
        social.publish(a.getEmail(), "Finished a chapter <script>alert(1)</script>", "Study", new byte[]{1, 2, 3});
        return social.feed(a.getEmail(), 0).items().getFirst().id();
    }

    @Test
    void friendshipLifecycleAndRecipientOnlyActions() {
        assertThrows(ResponseStatusException.class, () -> social.requestFriend(a.getEmail(), a.getId()));
        social.requestFriend(a.getEmail(), b.getId());
        social.requestFriend(a.getEmail(), b.getId());
        social.requestFriend(b.getEmail(), a.getId());
        assertEquals(1, friendships.count());
        Long request = social.requests(b.getEmail(), 0).items().getFirst().id();
        assertThrows(ResponseStatusException.class, () -> social.respond(a.getEmail(), request, true));
        assertThrows(ResponseStatusException.class, () -> social.respond(c.getEmail(), request, true));
        social.respond(b.getEmail(), request, true);
        assertEquals(b.getId(), social.friends(a.getEmail(), 0).items().getFirst().id());
        assertEquals(a.getId(), social.friends(b.getEmail(), 0).items().getFirst().id());
        social.requestFriend(c.getEmail(), b.getId());
        Long declined = social.requests(b.getEmail(), 0).items().getFirst().id();
        social.respond(b.getEmail(), declined, false);
        assertEquals(0, social.requests(b.getEmail(), 0).total());
        assertEquals(1, friendships.count());
    }

    @Test
    void pendingAndUnrelatedUsersCannotReadPhotosOrInteract() {
        Long post = makePost();
        social.requestFriend(a.getEmail(), b.getId());
        assertEquals(0, social.feed(b.getEmail(), 0).total());
        assertThrows(ResponseStatusException.class, () -> social.photo(b.getEmail(), post));
        assertThrows(ResponseStatusException.class, () -> social.like(b.getEmail(), post, true));
        assertThrows(ResponseStatusException.class, () -> social.comments(b.getEmail(), post, 0));
        assertThrows(ResponseStatusException.class, () -> social.comment(b.getEmail(), post, "Hello"));
        social.respond(b.getEmail(), social.requests(b.getEmail(), 0).items().getFirst().id(), true);
        assertEquals(1, social.feed(b.getEmail(), 0).total());
        assertArrayEquals(new byte[]{1, 2, 3}, social.photo(b.getEmail(), post));
        assertTrue(social.like(b.getEmail(), post, true).liked());
        assertEquals(1, social.like(b.getEmail(), post, true).count());
        assertEquals(0, social.like(b.getEmail(), post, false).count());
        social.comment(b.getEmail(), post, "Well done!");
        assertEquals(1, social.comments(b.getEmail(), post, 0).total());
        assertThrows(ResponseStatusException.class, () -> social.comment(b.getEmail(), post, "  "));
        assertEquals(0, social.feed(c.getEmail(), 0).total());
        assertThrows(ResponseStatusException.class, () -> social.photo(c.getEmail(), post));
        assertThrows(ResponseStatusException.class, () -> social.like(c.getEmail(), post, true));
    }

    @Test
    void paginationSearchAndValidation() {
        for (int i = 0; i < 12; i++) social.publish(a.getEmail(), "Update " + i, "", null);
        var first = social.feed(a.getEmail(), 0);
        assertEquals(10, first.items().size()); assertTrue(first.hasNext());
        assertEquals("Update 11", first.items().getFirst().text());
        assertEquals(2, social.feed(a.getEmail(), 1).items().size());
        assertEquals("NONE", social.search(a.getEmail(), "bob", 0).items().getFirst().state());
        social.requestFriend(a.getEmail(), b.getId());
        assertEquals("SENT", social.search(a.getEmail(), "bob", 0).items().getFirst().state());
        assertEquals("INCOMING", social.search(b.getEmail(), "alice", 0).items().getFirst().state());
        assertThrows(ResponseStatusException.class, () -> social.publish(a.getEmail(), " ", "", null));
        assertThrows(ResponseStatusException.class, () -> social.publish(a.getEmail(), "x".repeat(2001), "", null));
        assertThrows(ResponseStatusException.class, () -> social.publish(a.getEmail(), "Update", "Invalid", null));
    }

    @Test
    void simultaneousOppositeRequestsAndRepeatedLikesStayUnique() throws Exception {
        runTogether(() -> social.requestFriend(a.getEmail(), b.getId()), () -> social.requestFriend(b.getEmail(), a.getId()));
        assertEquals(1, friendships.count());
        Long request = friendships.findAll().getFirst().getId();
        String recipient = social.requests(a.getEmail(), 0).total() == 1 ? a.getEmail() : b.getEmail();
        social.respond(recipient, request, true);
        Long post = makePost();
        runTogether(() -> social.like(b.getEmail(), post, true), () -> social.like(b.getEmail(), post, true));
        assertEquals(1, likes.countByPostId(post));
    }

    void runTogether(Runnable left, Runnable right) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            Future<?> one = pool.submit(() -> { await(gate); left.run(); });
            Future<?> two = pool.submit(() -> { await(gate); right.run(); });
            gate.countDown(); one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS);
        }
    }

    void await(CountDownLatch gate) {
        try { gate.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); }
    }

    @Test
    void socialPageApiCsrfAndPhotoAuthorization() throws Exception {
        mvc.perform(get("/social")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/social").with(user(a.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Share a progress update")));
        mvc.perform(post("/social/api/requests/" + b.getId()).with(user(a.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/social/api/requests/" + b.getId()).with(user(a.getEmail())).with(csrf()))
                .andExpect(status().isOk());
        Long post = makePost();
        mvc.perform(get("/social/posts/" + post + "/photo").with(user(c.getEmail())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/social/posts/" + post + "/photo").with(user(a.getEmail())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/social/api/feed").with(user(c.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void multipartCreatesProtectedPhotoAndRejectsSpoofedUploadAtomically() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart("/social/api/posts").file(new MockMultipartFile("photo", "photo.png", "image/png", png.toByteArray()))
                .param("text", "A new milestone").with(user(a.getEmail())).with(csrf()))
                .andExpect(status().isOk());
        assertEquals(1, photos.count());
        Long post = social.feed(a.getEmail(), 0).items().getFirst().id();
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(social.photo(a.getEmail(), post))));
        mvc.perform(multipart("/social/api/posts").file(new MockMultipartFile("photo", "bad.png", "image/png", "not an image".getBytes()))
                .param("text", "Invalid photo").with(user(a.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest());
        assertEquals(1, posts.count());
    }
}
