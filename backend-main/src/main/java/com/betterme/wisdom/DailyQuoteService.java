package com.betterme.wisdom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DailyQuoteService {

    public record Quote(String text, String author) {}
    public record ZenQuote(String q, String a, String h) {}

    private static final List<Quote> BACKUP = List.of(
        new Quote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant"),
        new Quote("The happiness of your life depends upon the quality of your thoughts.", "Marcus Aurelius"),
        new Quote("It is not that we have a short time to live, but that we waste much of it.", "Seneca"));

    private static final long REFRESH_MS = 60 * 60 * 1000; // 1 hour

    private List<Quote> pool = BACKUP;
    private long loadedAt = 0;

    public synchronized Quote random() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > REFRESH_MS) {
            loadedAt = now;
            pool = fetchPool();
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private List<Quote> fetchPool() {
        try {
            ZenQuote[] result = RestClient.create().get()
                .uri("https://zenquotes.io/api/quotes")
                .retrieve()
                .body(ZenQuote[].class);
            if (result != null) {
                List<Quote> list = new ArrayList<>();
                for (ZenQuote z : result) {
                    // skip empty items and the "too many requests" message ZenQuotes sends when limited
                    if (z.q() != null && z.a() != null && !z.a().equalsIgnoreCase("zenquotes.io")) {
                        list.add(new Quote(z.q(), z.a()));
                    }
                }
                if (!list.isEmpty()) return list;
            }
        } catch (Exception e) {
            // API down or limited: use the backup quotes below
        }
        return BACKUP;
    }
}
