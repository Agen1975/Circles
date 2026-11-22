# TODO

## Revisit: Google Play Age Signals API (decided to skip for now, 2026-11-19)

**Status:** not required to publish, so it was skipped. Revisit **before January 2027**.

**Why it matters for this app:** Cosmic Bubbles has AdMob ads and in-app purchases. US state "app store accountability" laws focus on both of these for users under 18.

**Legal landscape as of Nov 2026** (sources conflict, so re-check before acting):
- **Texas:** active. The Play Age Signals API returns age range and "significant change" approval status for eligible users.
- **California:** age sharing becomes mandatory from **January 2027**.
- **Brazil:** mandatory from **January 2027**.
- **Utah and Louisiana:** reportedly delayed to 2027.
- **Elsewhere:** sharing is voluntary. Google said it would roll the API out to all markets by the end of 2026.

**Google's position:** integration is not mandated for every app. Developers are responsible for their own legal compliance. Age signals may only be used to provide age-appropriate experiences, never for ads targeting, analytics or profiling.

**Proposed implementation (about half a day):**
1. Add `implementation("com.google.android.play:age-signals:<latest>")`. It was 0.0.6 (beta) in Nov 2026.
2. Request age signals once at startup, and cache the result with a timestamp.
3. If the user is under 18:
   - set AdMob `RequestConfiguration` to `TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE`, with max ad content rating G or PG;
   - request non-personalised ads.
4. Handle `VERIFICATION_REQUIRED` by prompting the user to open the Play Store, and `NOT_SHARED` by falling back to the current behaviour.
5. Purchases need nothing extra. Play and Family Link already handle parent approval for supervised accounts.
6. Optional: protect the call with the Play Integrity API.

**Before starting, check:**
- Play Console → Policy → App content: any new age-signals declaration.
- Play Console → Age signals page: custom age ranges, which can be changed once a year.
- Google support pages:
  - "Changes to Google Play for upcoming app store bills" (answer/16569691).
  - "Age Signals API and User Data policy" (answer/16585319).
- Docs: https://developer.android.com/google/play/age-signals/overview

## Play Games achievements (almost done, 2026-11-21)

The code is in commit 541d3db, and all 14 achievement IDs are in `games-ids.xml` (checked in the release build). Remaining steps:
- Review and publish the Play Games Services configuration so the achievements go live. Until then, only testers can unlock them.
- Test on the internal track: earn an award, then open Awards → Play Games.

## ✅ DONE: "Winter Lights" seasonal theme (released 2026-11-22, versionCode 8)

Released to production and both Play Console promotional events are created.

**What's in the app:**
- **Season:** Dec 1 to Jan 6 inclusive, built into the app (`Season.java`). No server or network call.
- **Unlocking:** finish any daily puzzle during the season. The theme is then kept for good. Premium players get it too, so the Shop's "all themes" promise still holds.
- **Decoration:** red, green, gold and icy-blue palette (`Theme.java`), falling snow on the game and menu backgrounds (`Snow.java`), and a multicolour star sparkle when a circle pops.
- **Announcing it:** a menu banner while the season runs, the 6 pm reminder for players who haven't unlocked it, an `unlock_theme` analytics event.
- **Testing:** `./gradlew installDebug -PseasonDate=20261210` pretends today is that date. Debug builds only.
- **Store assets:** `promo_winter_lights_card.png` (event card, 1920x1080, no text) and `store_screenshots/phone/7_winter_lights.png`.
- **Promo events** (local time, 4-week cap): Dec 1 to 28 (shown from Nov 28), and Dec 29 to Jan 6.

**After the season (Jan 7):** move the Winter Lights screenshot out of the top listing slots.

- **Later seasons:** add a `Season` and a theme for Spring Bloom (around Easter, which needs a per-year date calculation), Summer and Autumn Harvest (Thanksgiving).

## Open bugs: overlapping on-screen messages (found 2026-11-22)

- "STREAK x2" and "No new pieces!" are drawn at nearly the same height above the NEXT row, so they overlap when one move does both.
- When one move unlocks two achievements, both callouts are drawn at the same spot and the text is garbled.
- Fix idea: stack messages that are on screen at the same time instead of drawing them in one place.
