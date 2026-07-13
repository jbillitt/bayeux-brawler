# Handover (2026-07-13) — plague/weather/polish batch COMPLETE

**All 20 tasks are done and on main. v0.4.0 Northumbria.** Unit suite green, `gradle compileDebugKotlin`
clean, `node scripts/vector_builder/verify_asset_roundtrip.js` passes (25 regions).

## What shipped this session (tasks 8-19)
- **8** Plague peasant (4aa4251) — `Ancillary.PLAGUE_PEASANT`, contagion radius, DoT, player catch roll.
- **9** Peasant art + disease tint (381a862) — one `skinTone()` for all flesh; he renders through the
  normal character pipeline, *not* `drawAncillaries` (that would draw a decorative second peasant).
- **10** Divine weather (8139937) — `DivineWeather`, `triggerWeather()`, cooldowns, level-12 gating.
- **11** Weather border icons + flourishes (a69da94) — one `weatherIconCenter()` shared by renderer
  and tap handler so they cannot drift apart.
- **13** Curve counters (63eb15e) — shield-walls (L12), brutes (L15), war-priest (L18); Shieldbreaker
  and Armour-Piercing outs; `seenCounters` drives the light guidance.
- **14** Barechested (afa29f5) — root cause was six hardcoded sleeve sites, not the torso.
- **15** Woad + cosmetic variance (e9796f2) — enemy faces were rolling from FighterState defaults, not
  the factory rng, which is why waves looked samey.
- **16** Palisade (4cbff1f) — shared `drawPalisadeRun()`; Dinan's cone flattened too.
- **17** Procedural buildings (478ff9f) — `drawBayeuxBuilding`, `drawSpiralTower`, seeded.
- **18b** Data-driven art (0c1177a) + workbench (9452c18) — see below.
- **19** v0.4.0 Northumbria.

## The two things worth knowing next session

**1. There is now a way to SEE the art without a device.**
`app/src/test/java/com/example/game/ArtScreenshotTest.kt` renders the real draw functions to PNGs via
the Roborazzi/Robolectric NATIVE graphics setup that was already in the project.

    gradle testDebugUnitTest --tests "*ArtScreenshotTest*" -Proborazzi.test.record=true --rerun

Writes `app/src/test/screenshots/{buildings,fighters,procedural}.png`. These are NOT golden-image
assertions — nothing fails on a pixel diff. They exist to be looked at. This caught two real bugs that
would otherwise have shipped blind (the termite-mound palisade, and an arcade that drew as black blobs).

The vector builder cannot do this job: its Kotlin parser only understands literal `moveTo`/`lineTo`,
so it renders *nothing* for any procedural art.

**2. Building art is data-driven now, and assets are DISCOVERED, not registered.**
`app/src/main/assets/art/*.json` — any asset with a `"spawn"` block joins the level-gen pool on next
launch, drawn through the generic `BackgroundObjectType.VECTOR`. A new building needs **no Kotlin at
all**: no enum value, no when-branch, no pool edit. Parsed with the platform `org.json` — deliberately
no serialization dependency.

Procedural art (the seeded palisade) stays code but reads its position from a named **anchor** in the
asset, so editing the hill in the builder moves the wall with it. No layer needs a do-not-touch marker.

Workbench: `cd scripts/vector_builder && node server.js` → http://localhost:3000/art.html

Only `building_manor` is ported so far. The rest (ship, palace, tower, bosham, forts, trojan horse) are
still Kotlin — port them the same way: transcribe, check in the screenshot harness, then delete the
Kotlin body. Do not port the seeded parts.

## Known issues (unfixed, deliberate)
- **`CombatEngineTest.throneModeFistsPlayerReachesAdjacentEnemy` is flaky** (~1 run in 8). Pre-existing
  from Task 2; depends on random attack rolls. Failed once in ~10 full-suite runs, 0/6 in isolation.
- **Poison and bleed are ~15x weaker than they were.** `applyFlatDamage` clamps every hit to a minimum
  of 1 point, so per-tick DoT was landing a full point *per frame* — a nominal 4/sec poison dealt ~60/sec.
  Fixed at the root (`applyDotDamage` accrues sub-point damage). The constants now mean what they say.
  **This needs a playtest** — the Hag especially may now feel feeble. Constants: `CombatEngine.POISON_DPS`,
  `BLEED_DPS`.
- `ARMOR_WEIGHT_LIMIT` 22 → 20, because scale+extras (21.5kg) did not exceed 22 and so never collapsed the
  chariot, contradicting its own spec. The armour-weight tests were also flaky: they pinned the armour but
  not the helmet, and `BattleSimState` defaults headgear to a **random** piece (0-6kg).
- Skipped deliberately: the peasant's hunched posture, and forearm hair on bare-chested fighters.

## Process
- Untracked cruft still present, never touched: `test_out.txt`, `tapestry_options.html`, logcats.
- Four stale Gemini worktrees still registered under `C:/Users/Josh/.gemini/...`; `git worktree prune`
  after deleting those dirs if wanted.
- Gemini's subscription has expired; it is no longer available for outsourcing.
