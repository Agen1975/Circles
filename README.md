# Cosmic Bubbles

A relaxed puzzle game for Android: move quarter-circle pieces around an 8×8 board and fit four of them together into complete circles.

This README describes the game as built on the `feature/engagement` branch.

## How to play

- Each piece is one quarter of a circle (top-left, top-right, bottom-left or bottom-right) in one colour.
- **Make a circle:** put four pieces of the same colour into a 2×2 square, each one in its own corner and facing the centre. The circle pops and you score.
- **Move a piece:** drag it to any empty cell, or tap it and then tap an empty cell. Pieces don't have to slide along a path; they can jump anywhere.
- **New pieces arrive after every move that doesn't score.** The next pieces are shown in the tray, and small markers on the board show where they will land. If a move scores, nothing new arrives, so chaining scoring moves keeps the board clear.
- **Hold slot:** park one piece in the HOLD box to use later. Dropping a piece there swaps it with whatever is already held.
- **Rotate:** double-tap a piece to turn it clockwise. Each rotation costs one rotation token and does not count as a move.
- **Game over** (in Classic) happens when the board fills up.

Sometimes falling pieces complete a circle by themselves. That's a "lucky" circle, and it scores a small bonus.

## Scoring

| What you do | Points |
|---|---|
| 1 circle in a move | 10 |
| 2 circles in one move | 30 |
| 3 circles in one move | 60 |
| 4 circles in one move | 100 (and so on) |
| Streak multiplier | Scoring moves in a row multiply points: ×2, ×3 … up to ×5. A move that doesn't score resets it. |
| Clear the whole board | +100 |
| Lucky circle (made by falling pieces) | 10 each |
| Bomb | 3 per piece destroyed, times the streak multiplier |

**Clusters:** circles that share pieces with a neighbouring circle of the same colour all clear together. That's how you make 2, 3 or more circles in one move.

Making 2 or more circles in one move also earns **+1 rotation token**.

## Special pieces

Special pieces appear in Classic, Blitz, Daily and Journey (from level 8). They don't appear in Zen.

- **★ Rainbow piece** (from 60 points): striped in all colours with a white star. It counts as any colour, so it can finish any circle as long as it faces the right corner.
- **Bomb** (from 150 points, rare): a grey ball with a lit fuse. Drop it anywhere to destroy every piece in the 3×3 area around it. Bombs can't be rotated, and they never form part of a circle.

The game explains each special piece once, the first time it shows up in the tray.

## Game modes

| Mode | Rules |
|---|---|
| **Classic** | Endless. Starts with 2 colours, and a 3rd is added at 100 points. 3 pieces arrive per turn. Ends when the board is full. Your game is saved, so you can continue later. |
| **Daily puzzle** | The same board for everyone on a given day. 3 colours, 30 moves. Your first finish each day builds a daily streak and gives +1 undo and +1 hint. |
| **Journey** | 60 levels with goals such as "make 8 circles", "score 300 points" or "make 4 circles of this colour", within a move limit. Earn 1–3 stars depending on how many moves you have left. Later levels add more colours and start with clutter on the board. |
| **Blitz** | Score as much as you can in 60 seconds. A 3rd colour is added at 60 points. |
| **Zen** | No game over and no specials. 3 colours and 2 pieces per turn. When the board fills, it gets thinned out instead of ending. Saved between sessions. |
| **How to play** | A 4-step interactive tutorial. It runs automatically on first launch. |

## Helpers

- **Undo:** one free undo per game, then undo tokens. Up to 30 moves back. On the game-over screen you can undo your last move to keep playing (except in Blitz).
- **Hint:** costs one hint token. It highlights a move that completes a circle right now.
- **Rotate:** uses rotation tokens. You start with 2–3 depending on the mode and earn more by making clusters.
- When you are out of undos or hints, you can optionally watch a short ad to get one.

## Progression and rewards

- **Player level and XP:** every finished game gives XP (based on score, plus bonuses for winning and for the daily puzzle).
- **Themes:** Space (start), Ocean (level 3), Candy (level 5, light) and Minimal (level 8, light). Change them in Settings → Theme.
- **Awards:** 14 achievements, such as First circle, Double, Triple, On a roll, Clean slate, Kaboom, 500 club, Week streak and Perfectionist. Each one gives 1 hint.
- **Daily streak:** play the daily puzzle on consecutive days to grow it.
- **Leaderboards (Google Play Games):** Classic high score, Daily puzzle and Blitz 60s. Scores are sent automatically when a game ends if you're signed in. Open them from the Leaderboards button on the main menu.
- **Share:** share your result from the game-over screen.

## Reminders

If you skip a day, the game sends a single friendly reminder at **6 pm** the next day. For example, it may mention your daily streak, or that today's puzzle is waiting. After two unanswered reminders it stops until you open the game again. You can turn reminders off in Settings. On Android 13+ the game asks for notification permission once, after you've finished a game, and never during the tutorial.

## Settings and accessibility

- Sound and haptics on/off.
- **Colour-blind symbols:** adds a distinct shape to each colour.
- Daily reminder on/off.
- Theme picker.
- Privacy choices, which appear in regions where ad consent is required.

## Shop and ads

- **Premium** (one-time purchase): no interstitial ads, all themes unlocked, and +10 undos and +10 hints.
- **Token packs:** 5 undos + 5 hints, or 20 undos + 20 hints.
- **Ads:** an occasional full-screen ad between games, never during play and never in your first 3 games. After that it's at most once every 3 games, and never for Premium players. Rewarded ads are always optional. Ad consent is handled with Google's consent form where required.
- **Diagnostics:** crash reports (Firebase Crashlytics) and usage statistics (Google Analytics for Firebase), which follow the same consent choices as ads.

## Look and feel

Each circle is drawn as one glossy 3D ball split into four segments. It has a shared highlight, shading, rim and soft seams, and works on both dark and light themes. Clearing circles comes with particles, pitch-rising pop sounds on streaks, screen shake on big moves, and haptic feedback.

## Building

```
./gradlew assembleDebug testDebugUnitTest
```

Release builds need the signing key (`Bubbles_key.jks`), which is kept out of git.
