# Bug Fixes — Design (Sub-project B)

Seven player-reported bugs, each diagnosed to a code site. All fixes are behaviour-preserving
elsewhere: no balance or content changes beyond what is listed here.

## 1. Lil Guy's grey mullet

**Report:** "backpack boy has a mullet on his head, a weird grey triangle."

**Diagnosis:** Ancillary heads are styled from `faceSeed = abs(anc.name.hashCode())`
(`TapestryRenderer.kt:2674-2704`). For `LIL_GUY` the seed lands on hair colour
`Color(0xFF888888)` (grey) and style case 1 ("longer locks down the neck") — a closed triangle
path off the back of the head. Result: grey mullet triangle.

**Fix:** Lil Guy gets an authored look instead of a hash roll: fixed warm-brown
(`0xFF8B5A2B`) "classic bowl cap" hair (style case 0). No other ancillary's seeded look changes.

## 2. Lil Guy's projectiles → sling darts

**Report:** shots read as invisible / "he doesn't do anything".

**Diagnosis:** His rocks fire fine (`GameViewModel.kt:1139-1160`, 0.7/sec per copy, 14 dmg) but
render in the fallback "sling stone" branch (`MainActivity.kt:1912-1919`) at
`r = 4.5f * sizeMultiplier(0.5) ≈ 2.25px` — near-invisible. Stuck in a body, ROCK falls into the
generic triangle-head branch (`TapestryRenderer.kt:580`).

**Fix:** New `ProjectileType.DART` for Lil Guy's shots:
- In flight: short dark shaft (~18px) with a small iron tip and a thin motion trail, visually
  distinct from enemy sling stones and archer arrows.
- Stuck: renders like a small bolt (shaft, no oversized triangle head).
- Damage/rate unchanged.

## 3. Follower catch-up sprint

**Report:** Old Maud, greasers, running man etc. fall behind when the player pushes forward.

**Fix:** Followers (isPlayer=true bodies, excluding pallbearers, which are position-locked)
get a distance-scaled speed multiplier toward the player:
`mult = 1 + clamp((distanceToPlayer - 150f) / 300f, 0f, 1.5f)` — amble when close, hustle up to
2.5× when far. Never teleport. Applies to movement only, not attack speed.

## 4. Enemies leap at the flying Raven

**Report:** enemies jump up to the Raven mid-flight.

**Diagnosis:** The raven renders airborne (visual Y offset) but combat is X-axis only; attacker
lunge animation tracks the target's rendered position, so swings at the raven launch attackers
upward.

**Fix (decision: keep targetable, fix anim):** The raven remains a legal melee target, but
attackers never leave the ground: lunge/strike animation vertical component is clamped to
ground level when the target is a raven. No gameplay numbers change.

## 5. Hag green tint "delay"

**Report:** poison tint seems delayed. **Decision:** instant is correct — kill the perceived lag.

**Diagnosis:** `poisonDuration` is set on the impact frame (`CombatEngine.kt:999-1003`) and
`skinTone` keys directly off it (`TapestryRenderer.kt:322`) — but only exposed skin
(face/hands) tints, which is subtle on armoured enemies and reads as "late".

**Fix:** On mud impact, spawn an immediate green splat burst (particles, hag-poison green
`0xFF2E7D32` family) and tint exposed skin from that same frame (already the case). While
poisoned, add a faint periodic green drip particle so the state is legible on armoured bodies.
Poison green stays distinct from plague/jaundice tints.

## 6. Thrice-Blessed / twins odds and follower rows

**Report:** triple-follower card too common; twins too rare; long follower trains walk off-screen.

**Current:** Thrice-Blessed offered on 50% of wins at level 8+ (`GameViewModel.kt:1233`), target
picked uniformly at random; twins on rally are 15% (`GameViewModel.kt:232`).

**Fix:**
- Thrice-Blessed offer chance 50% → 20%, and each follower can be offered/tripled at most once
  per run (track consumed ids).
- Target pick is weighted toward followers you already own multiples of (twins → ×3 = 6
  bodies is intended and stays; a follower with duplicates is ~3× as likely to be picked).
- Rally twins chance 15% → 30%.
- Entourage rendering: the trailing follower line wraps into a second row beneath the first
  after 5 bodies instead of extending off-screen (`TapestryRenderer.drawAncillaries` offsets).

## 7. Pallbearers don't reflect weapon upgrades in throne mode

**Diagnosis:** Front pallbearers inherit only base `weaponHead`/`weaponHandle`/`shield`
(`GameViewModel.kt:657-676`). The lord's `extraAttachments`, `handleExtensionCount`,
`rangedUpgrades`, `shieldUpgrades`, `brawlerUpgrades` and dual-wield flag never transfer.

**Fix:** Front pair inherit the lord's full kit: extra attachments, handle extensions, ranged /
shield / brawler upgrades, dual-wield flag, and shield HP computed the same way as the player's.
Rear pair stay bare (throne carriers). Renders and fights accordingly.

## 8. Flail heads spin like angle grinders

**Diagnosis:** Spike angle includes `+ animFrame` unconditionally
(`TapestryRenderer.kt:1383`), so spikes orbit the ball constantly even at idle.

**Fix:** Spikes are fixed to the ball, rotating with `chainAngle` only; continuous `animFrame`
spin removed. Idle flails sway on the chain; attacking flails swing hard — but the ball never
rotates like a saw blade.

## Out of scope

Performance work (sub-project C), new content (sub-project D), any music/audio changes
(sub-project A, already planned).
