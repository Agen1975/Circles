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

## Other open items

- Push `feature/engagement` from Android Studio. This shell has no GitHub credentials. Don't merge into master yet.
- Build a fresh signed release bundle before uploading to Play. The existing `app/release/app-release.aab` is stale. Use versionCode 4 (bumped 2026-11-20 for internal testing).
- Play Console → App content → **Data safety**: declare what Firebase collects (added 2026-11-20). That's crash logs, diagnostics, app interactions and device or other IDs, collected by Crashlytics and Analytics, processed by Google and not shared. Also update the privacy policy.
- AdMob → Privacy & messaging: make sure the EU consent message includes Google Analytics for Firebase as a vendor or purpose, so Analytics consent comes from the same form.
