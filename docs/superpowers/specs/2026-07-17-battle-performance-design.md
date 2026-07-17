# Battle Performance — Design (Sub-project C)

## Problem

Battle jank on device in four confirmed zones:

1. **Always in battle** — fixed per-frame overhead even with few fighters.
2. **Late-game crowds** — degrades as follower/enemy count grows (20–30+ actors).
3. **Spiky moments** — hitches during projectile volleys, blood particles, popups.
4. **Large player models** — burly / absolute-unit variants visibly cost more.

Scope decision (user): runtime FPS only, profile-first mindset but **no measurement
harness** — fixes are driven by code analysis and verified by existing tests plus
on-device eyeball. Release build config (R8, baseline profiles) is explicitly
out of scope for this sub-project.

## Code findings

| Hotspot | Location | Cost |
|---|---|---|
| Full-scene redraw every tick | `MainActivity.kt:1678` (BattlefieldScene Canvas) | Linen weave lines, border, latin headline, and all `backgroundObjects` (ship, Bosham church, manor — `BuildingRenderer.kt`) are re-stitched ~30×/sec despite being static per level. |
| Stitch draw-call volume | `StitchCraft.kt:28` `drawStitchedFill` | Density caps (40 rows × 30 segments) mean a *capped-out* shape still issues ~1,200 `drawLine` calls. Big models hit the cap on multiple layers (tunic, gown, hump, extra armor — `TapestryRenderer.kt:286-504`); crowds multiply it by N fighters. |
| Sim-loop allocation churn | `GameViewModel.kt:822-848` `updateSimulation` | Every 33 ms tick: `popups.filter` new list, `particles.filter { } + particleBuffer` new list(s), `weatherCooldowns.mapValues` new map, plus enemy/projectile list copies. Steady GC pressure → hitches under burst load (the "spiky moments"). |
| Tick drift | `GameViewModel.kt:814-818` | `delay(33)` accumulates scheduler error; effective tick beats against display refresh, producing periodic double/skipped frames. |

## Approach (chosen: C — cache + batch + hygiene)

### Part A — Static backdrop cache

New `TapestryBackdropCache` (renderer-side, next to `StitchCraft.kt`):

- Renders the static-per-level content **once** into an `ImageBitmap` via
  `CanvasDrawScope`: linen weave shading lines, tapestry border, latin headline,
  and all `backgroundObjects` (ship/buildings).
- Cache key: `(level, borderSeed, canvasWidth, canvasHeight)`. Rebuild on level
  change or size change (rotation); otherwise per-frame backdrop cost is a
  single `drawImage`.
- Dynamic elements stay live: fighters, projectiles, particles, popups, weather
  flash overlay. Screenshake already translates the whole scene — the blit
  translates with it, no behaviour change.
- Zero visual change: cache renders through the exact same draw functions.

### Part B — Batched stitching

Rewrite `drawStitchedFill` internals (`StitchCraft.kt:28`):

- Accumulate all horizontal stitch segments into one reused `Path`
  (`moveTo`/`lineTo` per segment, `rewind()` between shapes), stroke once with
  `drawPath(style = Stroke(1.5f, cap = Round))`.
- Same for the sparse vertical anchor stitches: second path, second `drawPath`.
- Result: ~1,200 `drawLine` calls → **2 `drawPath` calls per shape**, identical
  geometry and deterministic jitter (same hash formulas, same offsets).
- Benefits every stitched shape — moving fighters and crowds included — so it
  covers the two zones the backdrop cache can't (crowds, big models).

### Part C — Loop hygiene

`GameViewModel.kt`:

1. **Drift-free tick**: replace bare `delay(33)` with deadline scheduling
   (`nextTick += 33; delay(nextTick - now)`) so ticks stay evenly spaced.
   Sim stays 30 Hz. (Refinement vs. the original `withFrameNanos` idea:
   `viewModelScope` has no frame clock, and 60 Hz rendering of a 30 Hz sim
   would need position interpolation — out of scope. Drift-free 30 Hz removes
   the beat-frequency stutter at a fraction of the complexity.)
2. **Allocation cleanup in `updateSimulation`**: age/advance particles and
   popups in place; only publish a new list to the `StateFlow` when membership
   actually changed (deaths/spawns), not every tick. Skip the
   `weatherCooldowns.mapValues` copy when no cooldown is active (already
   guarded) and reuse a scratch map otherwise. Same visible behaviour, far
   fewer short-lived objects per tick.

## Non-goals

- No build config changes (`isMinifyEnabled` stays as-is this sub-project).
- No measurement harness (overlay/macrobenchmark) — user skipped.
- No visual changes of any kind; tapestry look must be pixel-comparable.
- No sim-rate change, no interpolation, no 60 fps target.

## Verification

- Existing suites must pass unchanged: `TapestryRenderer`, `SimulationModels`,
  `GameViewModel` tests (`.\gradlew.bat :app:testDebugUnitTest`).
- New unit tests:
  - Stitch batching produces the same segment endpoints as the old per-line
    math for representative shapes (extract geometry into a testable function).
  - Backdrop cache invalidates on level/size/seed change and NOT on tick.
  - Deadline scheduler keeps tick spacing (fake-clock test).
  - `updateSimulation` does not publish new particle/popup lists on a
    no-change tick (referential equality check).
- Manual device pass: early battle, 30-follower late battle, projectile
  volley, absolute-unit player — checked for smoothness by eye.
