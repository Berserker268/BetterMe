package com.betterme.social.service;

import com.betterme.social.model.Friendship;
import com.betterme.social.model.CommentLike;
import com.betterme.social.repository.CommentLikeRepository;
import com.betterme.social.model.HiddenPost;
import com.betterme.social.repository.HiddenPostRepository;
import com.betterme.social.model.SocialComment;
import com.betterme.social.model.SocialLike;
import com.betterme.social.model.SocialPhoto;
import com.betterme.social.model.SocialPost;
import com.betterme.social.repository.FriendshipRepository;
import com.betterme.social.repository.SocialCommentRepository;
import com.betterme.social.repository.SocialLikeRepository;
import com.betterme.social.repository.SocialPhotoRepository;
import com.betterme.social.repository.SocialPostRepository;

import com.betterme.user.User;
import com.betterme.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
import static com.betterme.social.dto.SocialViews.*;

@Service
@Transactional(readOnly = true)
public class SocialService {
    private static final Set<String> CATEGORIES = Set.of("Study", "Fitness", "Prayer", "Habit", "Skill", "Other");
    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final SocialPostRepository posts;
    private final SocialPhotoRepository photos;
    private final SocialLikeRepository likes;
    private final SocialCommentRepository comments;
    private final CommentLikeRepository commentLikes;
    private final HiddenPostRepository hiddenPosts;
    private final EntityManager entityManager;

    public SocialService(UserRepository users, FriendshipRepository friendships, SocialPostRepository posts,
                         SocialPhotoRepository photos, SocialLikeRepository likes,
                         SocialCommentRepository comments, CommentLikeRepository commentLikes,
                         HiddenPostRepository hiddenPosts, EntityManager entityManager) {
        this.users = users;
        this.friendships = friendships;
        this.posts = posts;
        this.photos = photos;
        this.likes = likes;
        this.comments = comments;
        this.commentLikes = commentLikes;
        this.hiddenPosts = hiddenPosts;
        this.entityManager = entityManager;
    }

    public Person me(String email) { return person(user(email)); }

    public PageView<SearchPerson> search(String email, String query, int page) {
        User viewer = user(email);
        String name = query == null ? "" : query.strip();
        if (name.length() < 2 || name.length() > 80) throw bad("Search using 2–80 characters.");
        return PageView.of(users.findByFullNameContainingIgnoreCaseAndIdNot(name, viewer.getId(),
                PageRequest.of(Math.max(0, page), 10, Sort.by("fullName").ascending().and(Sort.by("id"))))
                .map(other -> {
                    Friendship relationship = pair(viewer.getId(), other.getId()).orElse(null);
                    String state = relationship == null ? "NONE" : relationship.isAccepted() ? "FRIENDS"
                            : relationship.getRequester().getId().equals(viewer.getId()) ? "SENT" : "INCOMING";
                    return new SearchPerson(other.getId(), other.getFullName(), state,
                            relationship == null ? null : relationship.getId());
                }));
    }

    public PageView<Person> friends(String email, int page) {
        User viewer = user(email);
        return PageView.of(friendships.friends(viewer.getId(), paging(page, 20))
                .map(f -> person(f.getLowUser().getId().equals(viewer.getId()) ? f.getHighUser() : f.getLowUser())));
    }

    public PageView<Request> requests(String email, int page) {
        User viewer = user(email);
        return PageView.of(friendships.incoming(viewer.getId(), paging(page, 20))
                .map(f -> new Request(f.getId(), person(f.getRequester()))));
    }

    @Transactional
    public String requestFriend(String email, Long otherId) {
        User viewer = user(email);
        if (viewer.getId().equals(otherId)) throw bad("You cannot add yourself.");
        long low = Math.min(viewer.getId(), otherId), high = Math.max(viewer.getId(), otherId);
        // Always lock in ID order. Opposite/concurrent requests share the same two locks.
        User lowUser = entityManager.find(User.class, low, LockModeType.PESSIMISTIC_WRITE);
        User highUser = entityManager.find(User.class, high, LockModeType.PESSIMISTIC_WRITE);
        if (lowUser == null || highUser == null) throw missing();
        Friendship existing = pair(low, high).orElse(null);
        if (existing != null) {
            if (existing.isAccepted()) return "Already friends.";
            return existing.getRequester().getId().equals(viewer.getId())
                    ? "Request already sent." : "This person already sent you a request. Accept it below.";
        }
        Friendship friendship = new Friendship();
        friendship.setLowUser(lowUser);
        friendship.setHighUser(highUser);
        friendship.setRequester(viewer);
        friendships.save(friendship);
        return "Friend request sent.";
    }

    @Transactional
    public void respond(String email, Long requestId, boolean accept) {
        User viewer = user(email);
        Friendship friendship = friendships.lockById(requestId).orElseThrow(this::missing);
        boolean member = friendship.getLowUser().getId().equals(viewer.getId()) || friendship.getHighUser().getId().equals(viewer.getId());
        if (!member || friendship.getRequester().getId().equals(viewer.getId())) throw missing();
        if (friendship.isAccepted()) {
            if (accept) return;
            throw bad("This request has already been accepted.");
        }
        if (accept) friendship.setAccepted(true);
        else friendships.delete(friendship);
    }

    public PageView<Post> feed(String email, int page) {
        User viewer = user(email);
        return PageView.of(posts.feed(viewer.getId(),
                PageRequest.of(Math.max(0, page), 10, Sort.by("createdAt").descending().and(Sort.by("id").descending())))
                .map(post -> view(post, viewer.getId())));
    }

    @Transactional
    public void publish(String email, String text, String category, byte[] photo) {
        User viewer = user(email);
        String body = cleanText(text, 2000, "Write a progress update before posting.");
        String tag = category == null ? "" : category.strip();
        if (!tag.isEmpty() && !CATEGORIES.contains(tag)) throw bad("Choose one of the available categories.");
        SocialPost post = new SocialPost();
        post.setAuthor(viewer);
        post.setText(body);
        post.setCategory(tag.isEmpty() ? null : tag);
        post.setHasPhoto(photo != null);
        posts.save(post);
        if (photo != null) {
            SocialPhoto image = new SocialPhoto();
            image.setPost(post);
            image.setContent(photo);
            photos.save(image);
        }
    }

    @Transactional
    public void deletePost(String email, Long postId) {
        User viewer = user(email);
        SocialPost post = posts.lockById(postId).orElseThrow(this::missing);
        if (!post.getAuthor().getId().equals(viewer.getId())) throw missing();
        commentLikes.deleteAllForPost(postId);
        comments.deleteAllForPost(postId);
        likes.deleteAllForPost(postId);
        photos.deleteAllForPost(postId);
        hiddenPosts.deleteAllForPost(postId);
        posts.delete(post);
    }

    @Transactional
    public void hidePost(String email, Long postId) {
        User viewer = user(email);
        SocialPost post = visiblePost(viewer, postId, true);
        if (!hiddenPosts.existsByPostIdAndUserId(postId, viewer.getId())) {
            hiddenPosts.save(new HiddenPost(post, viewer));
        }
    }

    public byte[] photo(String email, Long postId) {
        User viewer = user(email);
        visiblePost(viewer, postId, false);
        return photos.findById(postId).orElseThrow(this::missing).getContent();
    }

    @Transactional
    public Reaction like(String email, Long postId, boolean liked) {
        User viewer = user(email);
        SocialPost post = visiblePost(viewer, postId, true);
        SocialLike existing = likes.findByPostIdAndUserId(postId, viewer.getId()).orElse(null);
        if (liked && existing == null) {
            SocialLike reaction = new SocialLike();
            reaction.setPost(post);
            reaction.setUser(viewer);
            likes.save(reaction);
        } else if (!liked && existing != null) likes.delete(existing);
        likes.flush();
        return new Reaction(liked, likes.countByPostId(postId));
    }

    public PageView<Comment> comments(String email, Long postId, int page) {
        User viewer = user(email);
        visiblePost(viewer, postId, false);
        return PageView.of(comments.findByPostIdAndParentIsNull(postId,
                PageRequest.of(Math.max(0, page), 20, Sort.by("createdAt").ascending().and(Sort.by("id"))))
                .map(c -> commentView(c, viewer.getId(), comments.countByParentId(c.getId()))));
    }

    @Transactional
    public Comment comment(String email, Long postId, String text) {
        User viewer = user(email);
        SocialPost post = visiblePost(viewer, postId, true);
        SocialComment comment = new SocialComment();
        comment.setPost(post);
        comment.setAuthor(viewer);
        comment.setText(cleanText(text, 1000, "Write a comment first."));
        comments.save(comment);
        return new Comment(comment.getId(), person(viewer), comment.getText(), comment.getCreatedAt(), 0, 0, false, null);
    }

    public PageView<Comment> replies(String email, Long postId, Long commentId, int page) {
        User viewer = user(email);
        visiblePost(viewer, postId, false);
        SocialComment parent = parentComment(postId, commentId);
        return PageView.of(comments.findByPostIdAndParentId(postId, parent.getId(),
                PageRequest.of(Math.max(0, page), 20, Sort.by("createdAt").ascending().and(Sort.by("id"))))
                .map(c -> commentView(c, viewer.getId(), 0)));
    }

    @Transactional
    public Comment reply(String email, Long postId, Long commentId, String text, Long replyToId) {
        User viewer = user(email);
        SocialPost post = visiblePost(viewer, postId, true);
        SocialComment parent = parentComment(postId, commentId);
        SocialComment target = null;
        if (replyToId != null && !replyToId.equals(parent.getId())) {
            target = comments.findById(replyToId).orElseThrow(this::missing);
            if (!target.getPost().getId().equals(postId) || target.getParent() == null
                    || !target.getParent().getId().equals(parent.getId())) throw missing();
        }
        SocialComment reply = new SocialComment();
        reply.setPost(post);
        reply.setAuthor(viewer);
        reply.setParent(parent);
        reply.setReplyTo(target);
        reply.setText(cleanText(text, 1000, "Write a reply first."));
        comments.save(reply);
        return new Comment(reply.getId(), person(viewer), reply.getText(), reply.getCreatedAt(), 0, 0, false,
                target == null ? null : person(target.getAuthor()));
    }

    @Transactional
    public Reaction likeComment(String email, Long postId, Long commentId, boolean liked) {
        User viewer = user(email);
        visiblePost(viewer, postId, true);
        SocialComment comment = comments.findById(commentId).orElseThrow(this::missing);
        if (!comment.getPost().getId().equals(postId)) throw missing();
        CommentLike existing = commentLikes.findByCommentIdAndUserId(commentId, viewer.getId()).orElse(null);
        if (liked && existing == null) commentLikes.save(new CommentLike(comment, viewer));
        else if (!liked && existing != null) commentLikes.delete(existing);
        commentLikes.flush();
        return new Reaction(liked, commentLikes.countByCommentId(commentId));
    }

    @Transactional
    public long deleteComment(String email, Long postId, Long commentId) {
        User viewer = user(email);
        visiblePost(viewer, postId, true);
        SocialComment comment = comments.findById(commentId).orElseThrow(this::missing);
        if (!comment.getPost().getId().equals(postId) || !comment.getAuthor().getId().equals(viewer.getId())) {
            throw missing();
        }
        comments.clearReplyTargets(postId, commentId);
        commentLikes.deleteForCommentThread(commentId);
        return comments.deleteCommentThread(commentId);
    }

    private Comment commentView(SocialComment comment, Long viewerId, long replyCount) {
        return new Comment(comment.getId(), person(comment.getAuthor()), comment.getText(), comment.getCreatedAt(),
                replyCount, commentLikes.countByCommentId(comment.getId()),
                commentLikes.existsByCommentIdAndUserId(comment.getId(), viewerId),
                comment.getReplyTo() == null ? null : person(comment.getReplyTo().getAuthor()));
    }

    private SocialComment parentComment(Long postId, Long commentId) {
        SocialComment parent = comments.findById(commentId).orElseThrow(this::missing);
        if (!parent.getPost().getId().equals(postId) || parent.getParent() != null) throw missing();
        return parent;
    }

    private SocialPost visiblePost(User viewer, Long postId, boolean lock) {
        SocialPost post = (lock ? posts.lockById(postId) : posts.findById(postId)).orElseThrow(this::missing);
        if (!post.getAuthor().getId().equals(viewer.getId()) &&
                pair(viewer.getId(), post.getAuthor().getId()).filter(f -> f.isAccepted()).isEmpty()) throw missing();
        return post;
    }

    private Post view(SocialPost post, Long viewer) {
        return new Post(post.getId(), person(post.getAuthor()), post.getText(), post.getCategory(), post.getCreatedAt(),
                post.hasPhoto() ? "/social/posts/" + post.getId() + "/photo" : null,
                likes.countByPostId(post.getId()), likes.existsByPostIdAndUserId(post.getId(), viewer), comments.countByPostId(post.getId()));
    }

    private java.util.Optional<Friendship> pair(Long a, Long b) {
        return friendships.findByLowUserIdAndHighUserId(Math.min(a, b), Math.max(a, b));
    }

    private PageRequest paging(int page, int size) {
        return PageRequest.of(Math.max(0, page), size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    }

    private User user(String email) { return users.findByEmail(email).orElseThrow(this::missing); }
    private Person person(User user) { return new Person(user.getId(), user.getFullName()); }
    private String cleanText(String text, int max, String emptyMessage) {
        String value = text == null ? "" : text.strip();
        if (value.isBlank()) throw bad(emptyMessage);
        if (value.length() > max) throw bad("Keep this text under " + max + " characters.");
        return value;
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found or unavailable to you."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
