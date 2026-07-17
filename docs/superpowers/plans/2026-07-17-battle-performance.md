# Battle Performance Implementation Plan (Sub-project C)

**Design:** `docs/superpowers/specs/2026-07-17-battle-performance-design.md`

**TDD loop per task:** write the failing test, run
`.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.*"` to red,
implement, run to green. Commit per task with conventional messages
(`perf: ...` / `test: ...`). Zero visual change is a hard requirement.

## Task 1 — Extract stitch geometry into a pure, testable function

- `app/src/main/java/com/example/game/StitchCraft.kt:28` — pull the segment
  math (row stepping, `segmentLen`, deterministic jitter formulas
  `((row * 31 + seg * 17) % 7) * 0.25f - 0.75f` etc., vertical anchor
  positions) out of `drawStitchedFill` into
  `internal fun stitchSegments(bounds: Rect): StitchGeometry`
  (lists of start/end offsets for horizontal + anchor segments).
- Test (new `app/src/test/java/com/example/game/StitchCraftTest.kt`):
  - For small, wide, tall, and cap-hitting bounds, segment endpoints exactly
    match the current inline formulas (reimplement the old math in the test as
    the oracle).
  - Density caps hold: rows ≤ ceil(height/3.5) and ≤ 41; segments per row
    bounded by width/segmentLen + 1.
- `drawStitchedFill` now consumes `stitchSegments` but still issues
  per-segment `drawLine` (behaviour-neutral refactor). Green = geometry locked.

## Task 2 — Batch stitches into two drawPath calls

- `StitchCraft.kt`: build one reused `Path` (file-private, `rewind()` per
  shape) from `StitchGeometry.horizontal`, stroke once via
  `drawPath(style = Stroke(width = 1.5f, cap = StrokeCap.Round))` with
  `stitchColor`; second reused path for anchors with `anchorColor`.
- Test: `stitchSegments` untouched (Task 1 tests stay green). Add a
  `StitchCraftTest` case asserting the path-builder visits every segment of a
  known geometry exactly once (feed a recording fake).
- Manual: screenshot compare one battle frame before/after (thread caps are
  identical, stroke params identical — expect pixel-equal within AA noise).

## Task 3 — Static backdrop cache

- New `app/src/main/java/com/example/game/TapestryBackdropCache.kt`:
  - `data class BackdropKey(level: Int, borderSeed: Long, w: Int, h: Int)`
  - `class TapestryBackdropCache { fun bitmapFor(key, drawStatic: DrawScope.() -> Unit): ImageBitmap }`
    — renders via `CanvasDrawScope` into an `ImageBitmap` on key miss, returns
    cached bitmap on hit.
- Test (new `TapestryBackdropCacheTest.kt`): same key → same bitmap instance
  and `drawStatic` invoked once; changed level/seed/size → re-invoked. (Bitmap
  creation is Android-graphics; if it fails on the JVM, split key/hit logic
  into a plain `BackdropCachePolicy` class and unit-test that instead.)
- Wire-up `app/src/main/java/com/example/MainActivity.kt:1678` Canvas block:
  move linen weave lines, border, latin headline, and the
  `uiState.backgroundObjects.forEach` block (`MainActivity.kt:1747`,
  `BuildingRenderer.kt`) behind the cache; per-frame path becomes
  `drawImage(cachedBitmap)` then live actors/particles/weather flash.
- Verify: existing renderer/UI tests green; manual rotation check rebuilds at
  new size.

## Task 4 — Drift-free tick scheduling

- `app/src/main/java/com/example/game/GameViewModel.kt:811-820`: extract
  `internal class TickScheduler(val periodMs: Long = 33)` with
  `fun nextDelay(nowMs: Long): Long` (deadline accumulator:
  `deadline += period; return (deadline - now).coerceAtLeast(0)`), use it in
  `startGameLoop` instead of bare `delay(33)`.
- Test (`GameViewModelTest.kt` or new `TickSchedulerTest.kt`): with a fake
  clock that runs 2 ms late every tick, average spacing stays 33 ms (no
  drift); a huge stall (200 ms) doesn't produce a burst of zero-delay
  catch-up ticks beyond one.

## Task 5 — Allocation hygiene in updateSimulation

- `GameViewModel.kt:822-848`:
  - Popups: age in place; only assign `_popupsState.value` to a new filtered
    list when at least one popup expired this tick.
  - Particles: keep in-place aging (already mutating); only rebuild the list
    when something expired or `particleBuffer` is non-empty; otherwise
    republish the same instance (or skip the assignment entirely).
  - `weatherCooldowns.mapValues` copy already guarded by `any > 0` — leave.
- Test (`GameViewModelTest.kt`): drive one tick where no popup/particle
  expires and none spawn → assert `popupsState.value` / `particlesState.value`
  are the *same instances* as before the tick (referential equality). Drive an
  expiring tick → new instance, expired items gone, cap still enforced
  (`MAX_PARTICLES` `takeLast` path unchanged).

## Task 6 — Final verification

- `.\gradlew.bat :app:compileDebugKotlin` clean.
- Full `.\gradlew.bat :app:testDebugUnitTest` green (all suites, not just game).
- `.\gradlew.bat :app:assembleDebug` and manual device pass on the four jank
  scenarios: early battle, 30-follower late battle, projectile volley,
  absolute-unit player.
- Commit history: one conventional commit per task.
