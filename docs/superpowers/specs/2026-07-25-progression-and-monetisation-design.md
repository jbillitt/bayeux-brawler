# Progression & Monetisation — Design (Batch C)

Follows `2026-07-25-entourage-content-and-fixes-design.md` (Batches A + B) and
depends on it: the seven new handles ship there in the base pool, marked with a
✦ as coming unlocks. This batch makes good on that marker — it filters them out
of the random roll and grants them by milestone instead.

## Why this is a separate spec

`startNewGame` (`GameViewModel.kt:277-296`) rolls `unlockedGearIds` fresh every
run. Nothing survives between sessions. Both "unlock items by playing" and "ads
disabled once purchased" need the same missing foundation, so they belong
together — and behind them.

## Decisions taken as defaults

These are stated rather than asked, so the plan can be written. Each is
overrulable; none is hard to reverse before implementation starts.

| Decision | Default | Why |
|---|---|---|
| Persistence | **DataStore Preferences** | Already in the version catalog (`libs.versions.toml:36`, `:87`) and sitting commented out at `app/build.gradle.kts:16`. The saved profile is a set of strings and three scalars — Room is in the catalog too and is the wrong tool for that. One uncommented line, no new dependency. |
| Ad network | **AdMob** (`play-services-ads`) | The default for Android games and the only one with a first-party rewarded-ad flow. A genuinely new dependency. |
| Ad placement | Interstitial every `ADS_PER_DEATHS` deaths (3), rewarded ad opt-in on the level-up screen | Matches the stated "an ad every few deaths" and "watch it, get a reward". |
| Purchase | **Play Billing**, one non-consumable `remove_ads` | Simplest model that satisfies "free, but purchasable to remove ads". |
| Test builds | `BuildConfig.DEBUG` disables ads entirely | See the risk note below. |

## Phasing

Three waves. The split exists because C1 delivers the entire unlock loop with
**zero new art**, which proves the feature before committing to eleven more
drawings in C2.

- **C1 — the loop.** Persistence, milestone tracking, unlock grants, and the
  UI that shows them. Content comes from Batch A's seven ✦-marked handles plus
  promoting the four existing Strange Relics to selectable weapon heads.
- **C2 — new unlockable art.** Special mounts, hats and hairstyles.
- **C3 — ads and IAP.**

C1 is shippable alone. C2 is shippable alone. C3 is independent of both.

---

# C1 — Persistence and the unlock loop

## The profile store

New `GameProfile.kt` in `com.example.game`, wrapping a single DataStore instance.

```
unlockedItemIds:   Set<String>   // gear ids earned across runs
clearedMilestones: Set<String>   // milestone ids already fired, so each grants once
highscore:         Int           // currently reset to 0 every run (GameViewModel.kt:292)
totalDeaths:       Int           // drives the ad cadence in C3
adFreePurchased:   Boolean       // set by C3; read by C3 only
```

**The seam is already there.** `unlockedGearIds` is the pool the gear tabs filter
on (`MainActivity.kt:1484-1487`, `:1542`). C1 does not introduce a new concept —
it changes where that set comes from: the existing random roll **union** the
saved `unlockedItemIds`. The random roll stays, so a fresh install still gets a
varied opening loadout, and earned items are simply always present on top.

Loading is a suspend read at app start into the ViewModel; writes are
fire-and-forget on the ViewModel scope. No migration path is needed — an absent
key means an empty set, which is exactly a new player.

## Milestone tracking

New `Milestone.kt` — an enum of `id`, `label`, and the item id it grants.
Milestones are checked at the points where the game already knows the run's
shape:

- **Level thresholds** — in the post-battle update (`GameViewModel.kt:1690`),
  where `nextLevel` is already computed.
- **Boss defeats** — the boss death path; `bossType` is on `FighterState`.
- **Sieges cleared** — the siege completion path; `SiegeSchedule` already tracks
  the ordinal.
- **Run-style feats** (won a level with no armour, killed a boss bare-fisted) —
  same post-battle hook, reading state that is already in `UiState`.

A fired milestone adds its id to `clearedMilestones` and its item to
`unlockedItemIds`, then pushes a popup. `clearedMilestones` is what makes each
grant once-only; without it a level-threshold check re-fires every run.

## C1 unlock table — existing content only

Seven handles from Batch A:

| Milestone | Unlocks |
|---|---|
| Reach level 5 | Ship's Oar |
| Clear your first siege | Nail-Studded Plank |
| Defeat Harold Godwinson | Stag Antler |
| Clear three sieges | Wheelbarrow |
| Defeat Harald Hardrada | Ship's Anchor |
| Defeat Gog or Magog | Thighbone Grip |
| Defeat William the Bastard | Herald's Trumpet |

Four Strange Relics promoted from attachment-only to selectable weapon heads.
They exist and are drawn already (`SimulationModels.kt:161-164`); this only
removes them from `STRANGE_HEAD_IDS` exclusion *for the player who earned them*,
by adding the id to `unlockedItemIds`. The rare relic card at
`GameViewModel.kt:1810-1821` is unaffected.

| Milestone | Unlocks |
|---|---|
| Win a level wearing no armour | Smoked Eel |
| Reach level 20 | Wheel of Aged Cheese |
| 500 lifetime kills | Irate Goose |
| Defeat any boss bare-fisted | Femur of St. Odo |

## Surfacing it

Without a place to see them, unlocks are invisible. A **Trophies** panel on the
start screen: every milestone as a row, earned ones showing the item, unearned
ones showing the label with the item hidden. Locked rows must state the
condition — a mystery box the player cannot pursue is not a progression loop.

An unlock fired mid-run also shows the existing popup, reusing `addPopup`.

## C1 checks

- Unit test: a milestone grants once — fire it twice, assert one grant.
- Unit test: `unlockedGearIds` is the random roll unioned with the saved set.
- Unit test: an empty/absent store yields an empty set, not a crash.
- Robolectric: write a profile, recreate the store, read it back.

---

# C2 — New unlockable art

Eleven new rendered items. Each is small; together they are the bulk of C2's
cost, which is the reason they are not in C1.

**Special mounts** — new cases alongside `MountRenderer`'s horse, chariot and
stilts, and new `Ancillary` entries with `anc_mount_` ids so
`MOUNT_ANCILLARY_IDS` (`SimulationModels.kt:73`) picks them up.

| Mount | Character | Milestone |
|---|---|---|
| War Ox | Enormous HP, glacial. A plough ox in barding. | Clear five sieges |
| Pack Mule | Terrible stats, large score multiplier. | Reach level 15 |
| Muzzled Bear | Fast and violent; occasionally mauls its own side. | Defeat both Gog and Magog |

**Special hats** — new `HeadgearPiece` entries; drawn in the helm switch.

| Hat | Milestone |
|---|---|
| Antlered Helm | Reach level 25 |
| Winged Helm | Defeat William twice |
| Wolf-Head Cowl | Beat your own highscore by 2× |
| Cooking Pot | Die 25 times — a consolation prize |

**Hairstyles** — currently only `short`, `long`, `bald`
(`TapestryRenderer.kt:1244-1252`, `GameViewModel.kt:302`). Each new style is a
new branch there, a new option in the start-screen picker
(`MainActivity.kt:1446`), and an entry in the `hairStyle`-driven surname
generator (`GameViewModel.kt:475`).

| Style | Milestone |
|---|---|
| Norman tonsure — shaved at the nape | Reach level 10 |
| Norse braids | Defeat Harald Hardrada |
| Monk's tonsure | Win a battle with Brother Tuck still alive |
| Topknot | Reach level 35 |

The Norman tonsure is worth doing first: the shaved nape is on the tapestry
itself and is the single most recognisable Norman silhouette in the source.

**C2 checks:** Roborazzi renders of every new mount, hat and hairstyle through
the existing `ArtScreenshotTest` / `MagazinePreviewTest` harness.

---

# C3 — Ads and in-app purchase

## Risk notes, stated plainly

The app is live — `applicationId com.headspace.bayeuxbrawlers`, `versionCode 24`.
These are not hypotheticals.

1. **Real ad unit ids must never run in a debug build.** Clicking your own live
   ads is the standard route to an AdMob account suspension. The design below
   disables ads outright in debug; if that is ever relaxed, it must be Google's
   published test ad unit ids and nothing else.
2. **UMP consent is required.** Serving personalised ads to EEA/UK users without
   a Google-certified consent form is a policy violation. The UMP SDK ships with
   `play-services-ads`; it is not optional work.
3. **The Play listing must be updated** — the "contains ads" declaration and the
   Data safety form both change when an ad SDK is added.
4. **Ads change the age rating and the families-policy position.** A cartoon
   medieval brawler may be judged child-appealing. Worth confirming before
   release, not after.

## Ad gating

A single `AdGate` object is the only thing that decides whether an ad may show.
One chokepoint, so the kill switches cannot be forgotten at a call site:

```
adsEnabled = !BuildConfig.DEBUG && !profile.adFreePurchased
```

Interstitial on death, at most every `ADS_PER_DEATHS` (3) — read `totalDeaths`
from the profile, which C1 already tracks.

## Rewarded ad

Opt-in only, offered on the existing level-up screen. Watching grants one of:

- an extra weapon attachment beyond the normal offer,
- an extra card in the level-up choice pool,
- a wider armour selection for that choice.

All three are additions to `pendingChoices` (`GameViewModel.kt:1702`), so the
reward is data, not a new mechanic. Reward is granted on the SDK's earned
callback only — never on dismissal.

## Purchase

Play Billing, one non-consumable `remove_ads`. On purchase or on restore, set
`adFreePurchased` in the profile. Query purchases on every launch so a
reinstall restores it. A "Remove Ads" entry on the start screen, hidden once
owned.

## C3 checks

- Unit test: `AdGate` returns false when `BuildConfig.DEBUG`, false when
  purchased, true otherwise — the table, all four combinations.
- Unit test: the interstitial cadence fires on the 3rd death, not the 1st or 2nd.
- Unit test: the rewarded grant fires on earned, not on dismissed.
- Manual: a release build against AdMob test ids before any live id is used.

---

## Also in this batch

`ARCHITECTURE.md` claims Room and a Firebase backend. Neither exists in the
source. Correct it when the real persistence lands, rather than adding a second
wrong claim on top.
