# New Content — Design (Sub-project D)

## Scope (user-confirmed)

1. **Boss battles** — the 1066 trio.
2. **Siege mode** — occasional wall-assault levels with real (simple) elevation.
3. **4–6 new enemy archetypes** — extending the existing `EnemyArchetype` /
   `EnemyFactory` system (`EnemyFactory.kt`), not a new system.
4. **New battlegrounds** — feasting hall, fleet crossing, Mont-Saint-Michel,
   plus the castle siege set (required by siege mode anyway).

Framing note that shapes everything: **the player is a Norman attacker; the
enemies are defenders.** The William fight is *treason* — we want the Norman
throne for ourselves.

## 1. Bosses — the 1066 trio

| Boss | Level | Character |
|---|---|---|
| **Harold Godwinson** | 10 | Saxon king behind a shield wall; housecarl retinue. Vulnerable moment mirrors the tapestry: player ranged hits can score an "arrow to the eye" crit (bonus damage window). |
| **Harald Hardrada** | 20 | Giant berserker; Stamford Bridge framing — his retinue holds a choke (bridge prop in the backdrop), fed to the player a few at a time until he engages. |
| **William the Bastard** | 30, then every 10 (40, 50…) with scaling | **The treason fight.** Norman-on-Norman: his retinue are `NORMAN_LOYALIST` knights with our own kit. Plays the existing THRONE theme (the usurpation is literal — we want his throne). |

Shared boss mechanics:
- Boss = oversized fighter model via existing size/`isThroneMode`-style
  scaling in `TapestryRenderer.kt` + signature gear (crown, banner, axe).
- Boss HP bar + named latin headline banner (extend `FlavourText.latinHeadline`).
- Entry stinger: `MedievalAudioSynth.playSound(SoundType.DRUM_ROLL)` + a
  dedicated boss mood flag through the existing `appliedMusicMoods` pipe;
  William's fight forces the THRONE theme, Harold/Hardrada keep BRAWL rules.
- Boss levels are never siege levels.

## 2. Siege mode

**Occurrence:** seeded-deterministic, roughly every 6–8 non-boss levels from
level 5 (exact cadence decided in implementation; must be reproducible from
`gameSeed + level` like other per-level generation).

**Layout:** castle wall (siege backdrop set) on the enemy side; a raised
**parapet Y-band** holds ranged defenders (`WALL_ARCHER`, slingers); a **gate
with HP** at ground level; melee defenders **queue behind the gate**, inactive
until it breaks.

**Elevation (new mechanic — none exists in the codebase today):**
- Parapet fighters live in an elevated Y-band. Melee attacks cannot reach
  them; only player/ancillary ranged attacks can hit them.
- Parapet defenders get a **downhill damage bonus** firing on ground targets.

**Gate:** melee-attackable structure with HP. Breaking it (a) releases the
queued melee enemies, (b) triggers the wall-defender split, (c) spawns the
ladder.

**Ladder rules — explicit invariants (user called out bug risk; these are
hard requirements and each gets a dedicated unit test):**
1. On gate break, a fixed fraction of parapet defenders climb down to meet
   the player at the gate alongside the melee queue; the rest hold the wall.
   Melee-only defenders never climb up.
2. The ladder spawns only after the gate breaks.
3. The player can mount the ladder **only while ≥1 living enemy is on the
   parapet**.
4. If the player is on the parapet and the last parapet enemy dies, the
   player **automatically climbs down** — no stranding.
5. If the parapet is empty, the ladder is inert (no interaction, no pathing
   onto it) — no climbing up to nothing.
6. Climbing (either direction) is an uninterruptible short transition state:
   no attacks land on or from a climbing fighter, so death-mid-climb
   softlocks are impossible.
7. Win condition unchanged: all enemies dead, ground + parapet. Battle can
   always end regardless of who is where.

## 3. New enemy archetypes (5, extending `EnemyArchetype`)

| Archetype | Role |
|---|---|
| `WALL_ARCHER` | Siege parapet ranged defender; downhill bonus; climbs down per ladder rule 1. |
| `TORCH_BEARER` | Defender who tries to **set the player on fire** — lobbed torch projectile applying an IGNITE damage-over-time (plumbed like the existing poison: mirror `poisonDuration` handling in `CombatEngine.kt`). Does **not** burn buildings — they're his buildings; we're the attackers. |
| `DANE_AXE_EXECUTIONER` | Slow two-hander with a long telegraphed wind-up; armor-shredding hit (interacts with existing shield/armor plumbing). |
| `MONK_MILITIA` | Weak robed defender who chants: small attack-speed buff aura to nearby defenders; priority-target incentive. |
| `NORMAN_LOYALIST` | Elite knight with Norman kit (our silhouette, hostile colours) — William's retinue for the treason fight; also seeds rare post-30 regular spawns. |

(Harold/Hardrada retinues reuse existing `HOUSECARL` / `BERSERKER` archetypes
with elite stat tiers — no new enum entries needed for them.)

## 4. New battlegrounds

Extend `BackgroundObjectType` + `BuildingRenderer.kt`, selected per level band
in the existing `bgObjects` generation (`GameViewModel.kt:995`):

1. **Feasting hall** — interior band (tables, hanging shields).
2. **Fleet crossing** — ships and sea band, straight off the tapestry.
3. **Mont-Saint-Michel** — the mount plus the quicksand scene band.
4. **Castle siege set** — motte-and-bailey wall, gate, parapet; used only on
   siege levels (wall/gate here are *interactive* objects, not just backdrop).

All render through the standard stitched pipeline, so they inherit
sub-project C's backdrop cache and batched stitching for free. Static parts
go in the cached layer; the gate (destructible) and parapet fighters are live.

## 5. Weather overlay full-bleed fix (user-reported glitch)

Divine weather flourishes currently glitch and cover only part of the field.
Cause: `drawWeatherFlourish` (`MainActivity.kt:2405`) draws each
`DivineWeather` with hardcoded partial geometry against raw canvas `w/h` —
LIGHTNING bolts pinned at `w*0.52f`/`w*0.72f` with ground at `h*0.78f`, FLOOD
a single narrow band, etc. — with no clipping or fitting to the tapestry
border, so coverage depends on device aspect ratio.

Fix requirements:
- Define one `innerFieldRect(size)` helper = full canvas inset by the border
  thickness (`2.dp` border + border band artwork).
- Every `DivineWeather` case must **wash the entire inner rect** (full-screen
  inside the border): geometry derived from the inner rect, and the whole
  flourish clipped to it (`clipRect`) so nothing paints over the border.
- Audit each weather case for hardcoded fractions; sweep effects (FLOOD)
  must traverse the full inner width edge-to-edge including start/end
  overshoot so no strip is left untouched.
- Stays a live overlay — never enters sub-project C's static backdrop cache.

## Non-goals

- No new game modes beyond siege (throne/brawl untouched).
- No new ancillaries this sub-project.
- No 60 fps/elevation-wide pathfinding — elevation exists only as the siege
  parapet band + ladder state machine.

## Verification

- Ladder invariants 1–7: one unit test each (`GameViewModel`/siege state
  machine tests) — these are the explicit anti-softlock guarantees.
- Boss schedule: levels 10/20/30/40… map to the right boss; siege cadence is
  seed-deterministic and never lands on a boss level.
- Archetype factory: new archetypes produce valid kits across level ranges
  (mirror existing `EnemyFactory` tests).
- IGNITE: applies, ticks, expires like poison; no stacking exploit.
- Weather: per-`DivineWeather` geometry extracted to a testable function;
  assert bounds cover the full inner rect and never exceed it, across phone
  and tablet aspect ratios.
- Full `.\gradlew.bat :app:testDebugUnitTest` green; manual pass: each boss,
  one full siege (including going up/down the ladder), each new backdrop,
  each weather flourish at full bleed.
