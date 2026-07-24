# Entourage, Content & Fixes — Design (Batch A + B)

## Scope

Two batches of a three-batch split. This spec covers everything that is an edit
to files that already exist. **Batch C — persistence, meta-progression unlocks,
ads and IAP — is explicitly out of scope and gets its own spec.**

The split exists because of one finding: **there is no persistence layer.**
`startNewGame` (`GameViewModel.kt:277-296`) rolls `unlockedGearIds` fresh every
run — two random items per category. There is no Room, DataStore or
SharedPreferences anywhere in `app/src/main/java`, and none in the gradle files.
`ARCHITECTURE.md` claims Room; that claim is stale and should be corrected when
Batch C lands. There is no billing or ad SDK either. Both "unlock items by
playing" and "ads disabled once purchased" need the same missing foundation, so
they belong together in a later spec.

### Batch A — combat, movement and content
1. Frontline entourage always outruns the player.
2. Buster the wardog: double HP.
3. Trojan Horse spearmen inherit the retinue panoply.
4. Stilts detach from small players (render bug).
5. Bosses cleave — anti-swarm difficulty fix.
6. Seven new weapon handles (unlock-gated, dormant).
7. Ten new armour pieces.

### Batch B — sound folders
8. Six new asset folders, shipped empty, with triggers wired.

---

## 1. Frontline entourage leads the charge

**Problem.** Ancillary `speedBoost` is applied to *the player*, not to the ally.
Recruiting Buster gives the player +0.2 speed (`totalSpeedBoost`, consumed at
`GameViewModel.kt:604`); Buster himself carries a fixed hardcoded
`speedBoost = 1.0f` set at spawn that never scales with anything. So the larger
the entourage, the further ahead of it the player runs. Any fix using a bigger
constant on the ally is defeated by the next recruit.

**Fix.** A *relative* floor, in `CombatEngine.followerCatchUpMultiplier`
(`CombatEngine.kt:145`). This is the single chokepoint every follower-movement
call site already routes through (`:360`, `:486`, `:499`, `:513`), so one change
covers all of them.

Current behaviour: an ally behind the player gets up to 2.5× to *close the gap*,
dropping to 1× once within 150px. It can reach parity but never lead.

New behaviour: for frontline allies advancing forward, the returned multiplier
is floored so the resulting speed is never below
`player.moveSpeed * FRONTLINE_LEAD`:

```
lead = (player.moveSpeed * FRONTLINE_LEAD) / fighter.moveSpeed
return max(catchUp, lead)
```

- `FRONTLINE_LEAD = 1.2f` — a named constant in `CombatEngine.Companion`
  alongside `TROJAN_ROLL_MULT`, so it is tunable on-device without hunting.
- Because it is a ratio against the player's *live* `moveSpeed`, it survives
  mounts, stacked ancillary boosts, `nakedBoldness` and `lateGameMultiplier`.
- **Frontline kinds:** `fanatic`, `wardog`, `plague_peasant`, `raven` — matched
  via the existing `isKind()` helper. All four are melee chargers.
- Backline lobbers (`hag`, `greaser`, `beekeeper`, `firebrand`, `archer`,
  `crossbowman`) are deliberately excluded: they are `isRanged` and built to
  kite. Pushing them forward would break their AI.
- The Trojan Horse is already handled separately by `TROJAN_ROLL_MULT = 1.9f`
  (`CombatEngine.kt:97`) and is not touched.
- The floor applies **only when `direction > 0f`** (advancing). Kiting retreats
  (`:499`, `:513`) and the regroup shuffle (`:360`) keep their existing
  behaviour — a retreat-speed floor would be wrong.
- Pallbearers keep their existing early return; they carry the throne and must
  stay under it.

**Check:** a unit test in `CombatEngineTest.kt` — give the player a large
`speedBoost`, advance a wardog and the player for a fixed dt, assert the wardog's
displacement exceeds the player's. It must fail if the floor is removed.

## 2. Buster: double HP

`GameViewModel.kt:874` — `maxHp = 75f, hp = 75f` becomes `150f`. The per-level
`allyHpBonus` at `:941` still stacks on top, unchanged. Pets stack, so two
Busters are 150 each; that is the existing intent.

No test — a literal constant change.

## 3. Trojan Horse spearmen inherit the panoply

**Problem.** `hasRetinuePanoply` arms the retinue in a single pass at battle
start (`GameViewModel.kt:951-960`), after every spawn block has run. The three
Trojan spearmen do not exist yet at that point — they spawn later, when the horse
dies (`GameViewModel.kt:1404-1416`). They therefore never get the kit.

**Fix at the shared point, not the call site.** Extract the arming block into a
private `applyRetinuePanoply(fighter: FighterState)` that sets `helm_spangen`,
`armor_chainmail` and appends `armor_gauntlets`. Call it from:
- the start-of-battle loop (`:951`), which keeps its existing exclusion filter
  for beasts, the decoy and pallbearers;
- the Trojan spearman spawn, guarded by the same `state.hasRetinuePanoply` flag.

Duplicating the gear block in both places is the wrong fix — the next
late-spawning ally would be broken again.

**Check:** unit test — set `hasRetinuePanoply`, kill the Trojan Horse, assert
each spawned spearman wears chainmail.

## 4. Stilts detach from small players

**Root cause, quantified.** The two are drawn in different transform spaces and
only agree at size ≈ 1.0.

`drawStilts` (`MountRenderer.kt:306`) is called inside the mount block
(`TapestryRenderer.kt:157-161`), which applies
`scale(horseScale / effectiveSize, pivot = (cx, cy+80))`. That sits inside the
outer `scale(effectiveSize, pivot = (posX, 358))` from `drawCharacter`
(`:64`) — the fighter's own size pivots at the ground line so every size stands
on the floor. Net screen position of the stilt top
(`footY = cy + 105f - (STILTS_LIFT_PX - 45f)`, `MountRenderer.kt:314`):

```
358 - 78 * effectiveSize - 20 * horseScale
```

The body's feet, meanwhile, are drawn by `drawLegs` at local `cy + 155f`
(`TapestryRenderer.kt:470`) inside the *same* outer ground-pivoted scale, then
lifted by `adjustedMountOffsetY = -STILTS_LIFT_PX / effectiveSize`
(`TapestryRenderer.kt:229`) — which cancels the scale, giving a fixed 90px screen
lift. Net foot position ≈ `265`, **near enough size-independent by design**.

Resulting error:

| `fighter.size` | stilt top | feet | error |
|---|---|---|---|
| 0.65 | ~287 | ~266 | stilt top **21px below** the feet — the player hovers |
| 1.0 | ~260 | ~265 | ~5px, reads as correct |
| 1.4 | ~229 | ~264 | feet **35px below** the top — legs sunk into the poles |

The `horseScale` random jitter (0.9–1.1, seeded from `fighter.id.hashCode()`)
adds a further ±2px, so two same-sized men get slightly different attachment.

**Fix.** Stop drawing the stilts in the mount's transform space. Draw them
inside the body's own lifted transform, alongside `drawLegs`, anchored to the
boot sole (`cy + 150f`) and running down to the true ground
(`cy + 155f + STILTS_LIFT_PX / effectiveSize`). They then scale with the man and
inherit his leg-swing pivots automatically — `drawStilts` already mirrors the
leg angles (`sin(animFrame) * 0.45f`), so the sync becomes structural rather
than hand-matched.

Consequence, accepted: a small man gets proportionally thinner stilts. That is
correct — the poles are his, not scenery.

The mount dispatch at `TapestryRenderer.kt:157-161` loses its `isStilts` branch;
the `isMounted` guard in `drawLegs` (`:463`) already special-cases stilts and
stays.

**Check:** `ArtScreenshotTest.kt:176-177` already renders `small_stilts` and
`normal_stilts` via Roborazzi. Add a `large_stilts` case at size 1.4 and re-bless
all three goldens. No new harness needed.

## 5. Bosses cleave

**Problem.** Boss HP (`EnemyFactory.kt:184` — `baseHpFor(level) * 9.5f`, or
`13f` for the giants) is the only real difficulty knob today, and a bigger
sponge is longer, not harder. The actual reason bosses fall is **swarming**: a
boss faces the player plus up to eight followers and only ever swings at one of
them.

**Fix.** When a fighter with `bossType != null` connects a melee blow, apply it
to every opposing fighter within its reach, not just the current target.

- Primary target takes full damage; secondary targets take
  `BOSS_CLEAVE_FRACTION` of it — `0.6f`, a named constant in
  `CombatEngine.Companion`, tunable on-device.
- Reach is the boss's existing computed reach; the giants (size 2.3 swinging a
  `handle_stump` maul) get a wide arc for free, which is the intent.
- Boss retinue (`isBossRetinue`) does **not** cleave — only the boss itself.
- Shield blocking, armour and evasion resolve per target through the existing
  damage path; cleave changes *who is hit*, not how damage is computed.

Explicitly **not** doing, this batch: enrage phases, crowd-control immunity, or
raising the HP/damage multipliers. Cleave is the lever that addresses the stated
cause; the others are available later if it proves insufficient.

**Check:** unit test — a boss with three allies inside its reach damages all
three in one swing; a non-boss with the same reach damages only its target.

## 6. Seven new weapon handles

Added to `GameData.WeaponHandle` (`SimulationModels.kt:171`).

| id | Name | Character |
|---|---|---|
| `handle_oar` | Ship's Oar | Long, broad-bladed, adds blunt. Fits the landing-beach opening. |
| `handle_femur` | Thighbone Grip | Stubby, light, very fast. Low reach. |
| `handle_antler` | Stag Antler | Short, branching, adds pierce. |
| `handle_trumpet` | Herald's Trumpet | Flared brass horn as a haft. Light, comic. |
| `handle_wheelbarrow` | Wheelbarrow | Absurd, very heavy, glacial. |
| `handle_anchor` | Ship's Anchor | Heaviest in the game. Massive blunt, worst speed penalty. |
| `handle_plank` | Nail-Studded Plank | Applies poison on hit (rust/tetanus). |

**Rendering.** `drawWeapon` (`TapestryRenderer.kt:2019`) has a generic straight-
haft path plus bespoke `if (weaponHandle.id == ...)` blocks for the cart wheel,
stump, ram, plough and blessed branch. Oar, femur and plank are straight and need
data only — every head sockets onto them through the generic path. Antler,
trumpet, wheelbarrow and anchor each need a bespoke block in the same style.
The grip-offset table at `:2032` needs an entry for any handle whose grip point
is not the default.

**Poison on the plank.** `poisonDuration` and `POISON_DPS` already exist and are
already driven by the hag. Add a hook in the melee damage path: if the attacker's
handle is `handle_plank`, set `victim.poisonDuration`. Roughly five lines,
reusing the existing effect.

**Noted trade-off, accepted by the user:** the wheelbarrow and the anchor are
mechanically the same joke as the existing cart wheel, tree stump and battering
ram — absurdly heavy, glacially slow. They add flavour, not new mechanics.

## 7. Ten new armour pieces

Added to `GameData.ArmorPiece` (`SimulationModels.kt`).

**Body armour** (occupies the `armor` slot):

| id | Name | Defense | Note |
|---|---|---|---|
| `armor_brigandine` | Brigandine | ~35 | Fills a genuine hole — defense currently jumps 22 (leather) straight to 50 (lamellar). |
| `armor_bearskin` | Bearskin Cloak | ~28 | Heavy pelt, drapes down the back. |
| `armor_habit` | Monk's Habit | 0 | High score multiplier, in the manner of `armor_jester`. |
| `armor_apron` | Chef's Apron | 0 | Comic. |
| `armor_frock` | Maid's Frock | 0 | Comic. |
| `armor_smock` | Old Smock | ~5 | Peasant rags. |
| `armor_toga` | Emperor's Toga | 0 | High score multiplier. |

**Layers** (stack via `extraArmors`, like the existing gauntlets/boots/coif):

| id | Name | Defense | Note |
|---|---|---|---|
| `armor_greaves` | Iron Greaves | ~15 | Shin plates. |
| `armor_spaulders` | Spaulders | ~15 | Shoulder plates. |
| `armor_surcoat` | Surcoat | ~3 | Cloth over mail; takes the player's chosen colour. The first cosmetic-led armour. |

**Rendering.** Body armour is a colour on the torso plus an optional texture case
in the `when (armor.id)` at `TapestryRenderer.kt:681` (and the `extraArmors`
mirror at `:704`). Greaves and spaulders need new layer draws in the manner of
the boots (`:477`, `:499`) and coif (`:1340`), and must be added to the layer
skip-list at `:687`. The zero-defence comic outfits also need entries in
`scoreMultiplier` (`SimulationModels.kt:592`) alongside `armor_jester`.

**Check:** render every new handle and armour piece through the existing
`MagazinePreviewTest` / `ArtScreenshotTest` Roborazzi harness. This matters more
than usual here — see the gating decision below.

## 8. Gating — dormant until Batch C

The new handles are unlock-gated. There is already an exact precedent:
`GameData.STRANGE_HEAD_IDS` (`SimulationModels.kt:169`), a set of ids held out of
the normal pools and offered only by a rare card.

Batch A adds `GameData.UNLOCKABLE_HANDLE_IDS` the same way and excludes it from
the base roll at `GameViewModel.kt:286`. **No unlock trigger is wired in this
batch** — this is the user's explicit decision. Batch C wires persistence and the
milestone grants that make them reachable.

**Stated consequence:** the seven handles are unreachable in normal play until
Batch C ships. The screenshot tests in §6/§7 are therefore the *only* verification
that the new art is correct, which makes blessing those goldens carefully a
requirement, not a nicety.

The armour pieces are **not** unlock-gated, but they follow the existing base-roll
convention at `GameViewModel.kt:288`, which already excludes the stackable layers
and the comic outfit from the random roll (`armor_gauntlets`, `armor_boots`,
`armor_coif`, `armor_jester`) because those arrive via level-up cards instead:

- **Roll normally:** `armor_brigandine`, `armor_bearskin`, `armor_smock` — real
  body armour with real defence, playable on landing.
- **Join the exclusion list:** `armor_greaves`, `armor_spaulders`,
  `armor_surcoat` (layers, granted by the layered-armour card at
  `GameViewModel.kt:1831`) and `armor_habit`, `armor_apron`, `armor_frock`,
  `armor_toga` (zero-defence score-multiplier outfits, alongside the jester).

**Incidental fix, same lines.** `startNewGame` seeds `initialGear` with
`"armor_none"` and `"head_none"` (`GameViewModel.kt:281-282`). Neither id exists
— the real ids are `armor_bare` and `helm_none`, which the second copy of this
block at `:1991-1992` gets right. The two adds are silent no-ops today. Correct
them while editing the adjacent exclusion list.

**Seam worth recording for Batch C:** `unlockedGearIds` is already the pool that
the gear tabs filter on (`MainActivity.kt:1484-1487`, `:1542`). Batch C does not
need a new concept — it needs to seed that set from a saved profile instead of a
random roll, and to add ids to it at milestones.

---

## 9. Sound folders (Batch B)

Six new directories under `app/src/main/assets/`, each with a `README.txt`
matching the existing `armour`/`flesh`/`shield` convention, and each added to the
folder list at `SoundSynth.kt:76`.

| Folder | Trigger |
|---|---|
| `trojan` | Belly bursts open — `GameViewModel.kt:1404`, alongside the existing `CRUNCH`. |
| `bee` | Humble Bede's hive bursts. |
| `herald` | Sir Boast-a-lot, on spawn. |
| `fanatic` | Mad Boris, as he charges. |
| `monk` | Brother Tuck, occasional, in the manner of the existing hag cackle. |
| `plague` | Wretched Aldwin, occasional coughing. |

The existing machinery needs no change: `prepare()` enumerates each folder and
loads any `.wav`/`.ogg`/`.mp3` into a `SoundPool`; `playFolder` returns `false`
for an empty folder and the caller falls through silently. So all six ship empty
and start working the moment recordings are dropped in.

Playback follows the `playDogBark` / `playHagCackle` pattern (`SoundSynth.kt:151`,
`:157`) — one small `fun play<X>()` per folder, each guarded by `sfxEnabled`.

**Check:** an assertion that every folder named in the `SoundSynth.kt:76` list
exists on disk. Cheap, and it catches the classic failure where a folder is
renamed and the sound silently stops.

---

## Out of scope (Batch C)

Recorded here so the boundary is unambiguous:

- Any persistence layer (DataStore or Room).
- Cross-run unlock milestones and the triggers that grant them.
- Ads, the reward flow (extra attachment / extra pool item / more armour choice),
  and the test-build and post-purchase kill switches.
- Play Billing and the purchased-state flag.
- Correcting the stale Room claim in `ARCHITECTURE.md`.

## Testing summary

| Item | Check |
|---|---|
| Frontline lead | Unit test: wardog outruns a speed-boosted player |
| Buster HP | None — literal constant |
| Trojan panoply | Unit test: spawned spearmen wear chainmail |
| Stilts | Roborazzi: `small_stilts`, `normal_stilts`, new `large_stilts` |
| Boss cleave | Unit test: boss hits three in-reach allies; non-boss hits one |
| Handles & armour | Roborazzi magazine/art preview render of every new piece |
| Base-roll ids | Unit test: every id seeded into `initialGear` resolves to a real `GearItem` |
| Sound folders | Assertion that every listed folder exists |
