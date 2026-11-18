# Friends & Social Feed

Open `/social` from the existing sidebar. The page uses the shared shell, Inter,
Playfair Display, and dark cards with warm accents. On narrow screens, use the
Feed and Friends buttons to switch columns.

Java classes are grouped under `com.betterme.social` into `controller` (routes
and API errors), `service` (business logic and photo processing), `repository`
(database queries), `model` (JPA entities), and `dto` (response records).

## Behavior

- Search by public full name; emails and password hashes are never returned.
- Send requests, accept or decline incoming requests, and see accepted friends.
- The feed includes your own posts and accepted friends' posts, newest first,
  10 at a time. Optional categories describe explicitly shared progress; nothing
  is automatically copied from goals or journals.
- Each post supports text (2,000 characters), one optional photo, likes, and
  inline comments (1,000 characters). Comment threads load 20 at a time.
- Comments support grouped reply threads, displayed beneath the parent comment
  and loaded 20 at a time. The nullable `social_comments.parent_id` column is
  added on startup; existing comments remain top-level comments. Reply routes
  apply the same post visibility checks and validate that the parent belongs
  to that post.
- Replies also have a Reply action. A nullable `reply_to_id` records the specific
  reply being answered, and the UI shows "Replying to [name]". Replies stay in the
  original thread; the server validates that the target is in that same thread.
- Authors can delete their own comments and replies after confirmation. Deleting
  a top-level comment removes its replies and their likes. Deleting a reply keeps
  other replies, clearing any "Replying to" reference to the deleted reply.
- Comments and replies have Like buttons with counts and saved liked states.
  `social_comment_likes` stores one like per user/comment, added through the
  normal schema update on startup. The server checks post access and comment
  ownership by the post, and serializes writes with the post lock. Deleting a
  post removes comment likes before removing its comments.
- Failed posts preserve their text and selected photo. Failed requests can be retried.
- A post author's top-right options menu allows permanent deletion, including its
  photo, likes, and comments. The server checks authorship before deleting.
- The top-right X removes a post from the current user's feed only. Hidden posts
  stay hidden after refresh; this does not delete the original or affect friends.

## Database and photos

The app uses `spring.jpa.hibernate.ddl-auto=update`, without a migration framework.
On the next normal app startup Hibernate adds these tables:
`social_friendships`, `social_posts`, `social_photos`, `social_likes`,
`social_comments`, and `social_hidden_posts`. Existing user, goal, and journal data are retained.

Friend pairs use ordered user IDs and a unique constraint. Writes lock the user
pair in ID order to serialize opposite requests. Only the recipient can respond.
Likes use a unique post/user constraint and a post lock; setting a liked state is
idempotent so retries do not flip it again.

There was no existing media storage integration. For this initial version,
photos are stored separately from post metadata as private PostgreSQL `bytea`
data. Feed queries never load photo bytes. Actual JPEG/PNG content is validated,
limited to 2 MB and 20 megapixels, resized to at most 1,600 pixels on the longest
edge, and re-encoded as JPEG without original metadata. No uploaded files are
placed under public static resources. `/social/posts/{id}/photo` checks friendship
access and returns `Cache-Control: no-store`. Photo volume consumes the team's
database storage; larger usage should move this table's payloads to a private
object storage bucket while keeping the same access checks.

## Validation

H2 is a test-only dependency. The Social tests explicitly override the datasource
with an in-memory database; they do not connect to Supabase:

```powershell
.\mvnw.cmd '-Dtest=SocialIntegrationTest,SocialImageServiceTest' test
```

The tests cover friendship lifecycle, recipient authorization, duplicate and
simultaneous requests, pending/unrelated access denial, unique likes, comments,
pagination, CSRF, page rendering, and actual image upload validation.

Optional real-browser checks use `SocialBrowserFixture` on port 18081 with a
separate disposable H2 database, and `src/test/browser/social.cjs`. Install
`playwright-core` under `target/social-browser-tools`, compile test classes, and
run the fixture with the test runtime classpath from the Surefire XML report.
Run the browser script from this directory using `node src/test/browser/social.cjs`.
Screenshots are written under `target/social-browser-results` and use fixture data.

Chrome checks cover desktop and 390px layouts, request/search actions, posting,
photo preview/removal, failure draft preservation, likes, comments, text escaping,
and direct photo denial for a pending friend. These checks do not verify a live
Supabase deployment or exact PostgreSQL locking behavior.
