package com.betterme.social.dto;

import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.List;

public final class SocialViews {
    private SocialViews() {}
    public record Person(Long id, String name) {}
    public record SearchPerson(Long id, String name, String state, Long requestId) {}
    public record Request(Long id, Person person) {}
    public record Post(Long id, Person author, String text, String category, Instant createdAt,
                       String photoUrl, long likes, boolean liked, long comments) {}
    public record Comment(Long id, Person author, String text, Instant createdAt, long replies, long likes, boolean liked, Person replyingTo) {}
    public record Reaction(boolean liked, long count) {}
    public record PageView<T>(List<T> items, int page, boolean hasNext, long total) {
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.hasNext(), page.getTotalElements());
        }
    }
}
