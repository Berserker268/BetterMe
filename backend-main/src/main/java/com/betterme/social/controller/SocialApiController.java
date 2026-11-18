package com.betterme.social.controller;

import com.betterme.social.service.SocialImageService;
import com.betterme.social.service.SocialService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import static com.betterme.social.dto.SocialViews.*;

@RestController
@RequestMapping("/social/api")
public class SocialApiController {
    private final SocialService social;
    private final SocialImageService images;
    public SocialApiController(SocialService social, SocialImageService images) {
        this.social = social;
        this.images = images;
    }

    @GetMapping("/feed")
    public PageView<Post> feed(Authentication auth, @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.feed(auth.getName(), page);
    }

    @PostMapping(value = "/posts", consumes = "multipart/form-data")
    public Map<String, String> post(Authentication auth, @RequestParam("text") String text,
                                     @RequestParam(name = "category", defaultValue = "") String category,
                                     @RequestParam(name = "photo", required = false) MultipartFile photo) {
        byte[] image = images.prepare(photo);
        social.publish(auth.getName(), text, category, image);
        return Map.of("message", "Your progress update has been shared.");
    }

    @DeleteMapping("/posts/{id}")
    public Map<String, String> deletePost(Authentication auth, @PathVariable("id") Long id) {
        social.deletePost(auth.getName(), id);
        return Map.of("message", "Post deleted.");
    }

    @PutMapping("/posts/{id}/hidden")
    public Map<String, String> hidePost(Authentication auth, @PathVariable("id") Long id) {
        social.hidePost(auth.getName(), id);
        return Map.of("message", "Post removed from your feed.");
    }

    @GetMapping("/people")
    public PageView<SearchPerson> people(Authentication auth, @RequestParam("q") String query,
                              @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.search(auth.getName(), query, page);
    }

    @GetMapping("/friends")
    public PageView<Person> friends(Authentication auth, @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.friends(auth.getName(), page);
    }

    @GetMapping("/requests")
    public PageView<Request> requests(Authentication auth, @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.requests(auth.getName(), page);
    }

    @PostMapping("/requests/{personId}")
    public Map<String, String> request(Authentication auth, @PathVariable("personId") Long personId) {
        return Map.of("message", social.requestFriend(auth.getName(), personId));
    }

    @PostMapping("/requests/{id}/accept")
    public Map<String, String> accept(Authentication auth, @PathVariable("id") Long id) {
        social.respond(auth.getName(), id, true);
        return Map.of("message", "Friend request accepted.");
    }

    @PostMapping("/requests/{id}/decline")
    public Map<String, String> decline(Authentication auth, @PathVariable("id") Long id) {
        social.respond(auth.getName(), id, false);
        return Map.of("message", "Friend request declined.");
    }

    public record LikeInput(boolean liked) {}
    public record CommentInput(String text, Long replyToId) {}

    @PutMapping("/posts/{id}/like")
    public Reaction like(Authentication auth, @PathVariable("id") Long id, @RequestBody LikeInput input) {
        return social.like(auth.getName(), id, input.liked());
    }

    @GetMapping("/posts/{id}/comments")
    public PageView<Comment> comments(Authentication auth, @PathVariable("id") Long id,
                                     @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.comments(auth.getName(), id, page);
    }

    @PostMapping("/posts/{id}/comments")
    public Comment comment(Authentication auth, @PathVariable("id") Long id, @RequestBody CommentInput input) {
        return social.comment(auth.getName(), id, input.text());
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public Map<String, Object> deleteComment(Authentication auth, @PathVariable("postId") Long postId,
                                            @PathVariable("commentId") Long commentId) {
        long removed = social.deleteComment(auth.getName(), postId, commentId);
        return Map.of("message", "Comment deleted.", "removed", removed);
    }

    @PutMapping("/posts/{postId}/comments/{commentId}/like")
    public Reaction likeComment(Authentication auth, @PathVariable("postId") Long postId,
                                @PathVariable("commentId") Long commentId, @RequestBody LikeInput input) {
        return social.likeComment(auth.getName(), postId, commentId, input.liked());
    }

    @GetMapping("/posts/{postId}/comments/{commentId}/replies")
    public PageView<Comment> replies(Authentication auth, @PathVariable("postId") Long postId,
                                    @PathVariable("commentId") Long commentId,
                                    @RequestParam(name = "page", defaultValue = "0") int page) {
        return social.replies(auth.getName(), postId, commentId, page);
    }

    @PostMapping("/posts/{postId}/comments/{commentId}/replies")
    public Comment reply(Authentication auth, @PathVariable("postId") Long postId,
                         @PathVariable("commentId") Long commentId, @RequestBody CommentInput input) {
        return social.reply(auth.getName(), postId, commentId, input.text(), input.replyToId());
    }
}
