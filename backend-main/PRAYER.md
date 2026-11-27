# Prayer & Spiritual Tracker

Open `/prayer` from the sidebar. It uses the shared shell and the same dark cards as the Social page.

Java classes live under `com.betterme.prayer`: `controller` (page, JSON API, API errors), `service`
(`PrayerService`, `PrayerTimeService` for Aladhan, `PrayerStatsCalculator`), `repository`, `model`, `dto`.

## Behavior

- Five prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha) come from the Aladhan API for a city, country,
  calculation method and Asr setting the user picks. The page shows which method was used, because times can
  differ from a local mosque timetable. The choice is remembered in the browser only (localStorage).
- If Aladhan can't be reached or returns something unexpected, the page says "Prayer times unavailable".
  It never shows a made-up time, and marking prayers still works. Good lookups are cached for 12 hours.
- Each prayer can be marked or unmarked for a day. Marking twice keeps one row; unmarking something that isn't
  marked does nothing. Future days can't be logged; times can be viewed up to 7 days ahead and logs edited
  up to 365 days back.
- Stats: today's count and percent, a 7-day chart (five beads per day), 7-day and 30-day totals, and a streak.
  Windows include today, even if unfinished. A streak is consecutive days with all five marked (an unfinished
  today doesn't reset it) and only looks inside the 30-day window ("30+" when it reaches the edge).
  No row means "not logged", never "missed".
- "Today" comes from the browser's time zone (sent as `tz`), with `betterme.prayer.default-zone` as fallback.

## Routes

| Route | Purpose |
| --- | --- |
| `GET /prayer` | the page |
| `GET /prayer/api/day?date=&city=&country=&method=&school=&tz=` | times plus the user's marks for one day |
| `PUT /prayer/api/log` body `{prayer, date, completed, tz}` | mark/unmark one prayer (CSRF header required) |
| `GET /prayer/api/stats?tz=` | 7-day chart data, totals, streak |

All reads and writes are for the signed-in user (found by `authentication.getName()`); no user id is accepted from the browser.

## Database

`spring.jpa.hibernate.ddl-auto=update` adds one table on the next normal startup: `prayer_logs`
(`id`, `user_id`, `prayer_name`, `prayer_date`, `prayed_at`) with a unique key on
`(user_id, prayer_date, prayer_name)`. The UML has `prayerName` and `prayedAt`; **`prayer_date` is an extension**
(the calendar day a prayer counts toward) that the team should confirm. Writes for one user are serialised with a
row lock on the user, the same idea `SocialService.requestFriend` uses, so a double-click can't create duplicates.

## Configuration (optional, shown with the built-in defaults)

```properties
betterme.prayer.default-city=Dhaka
betterme.prayer.default-country=Bangladesh
betterme.prayer.default-method=1
betterme.prayer.default-school=1
betterme.prayer.default-zone=Asia/Dhaka
```

The calculation-method list in `PrayerService` (ids 1, 2, 3, 4, 5, 13, 15) should be checked against
aladhan.com/calculation-methods.

## Tests

H2 in memory, never Supabase. Aladhan is mocked or pointed at a closed port, so no internet is needed:

```powershell
.\mvnw.cmd '-Dtest=PrayerIntegrationTest,PrayerTimeServiceTest,PrayerStatsCalculatorTest' test
```

They cover idempotent marking and unmarking, 0/3/5 progress, two users staying separate, rejected future/old/invalid
input, past days in the trend, logging while times are unavailable, simultaneous marks, login and CSRF, Aladhan parsing,
and the unreachable-service path.
