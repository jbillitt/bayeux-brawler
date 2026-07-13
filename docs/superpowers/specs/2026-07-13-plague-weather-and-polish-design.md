# Plague, Weather & Polish — Batch Design (2026-07-13)

Batch spec covering bug fixes, small-fighter viability, new content (diseased peasant,
divine weather events, armour weight), the stay-ahead-of-the-curve system, visual work,
and vector-builder tooling. Approved in brainstorming with the user.

## Art direction (cross-cutting)

Every new visual in this batch must be **visually distinct at a glance** but built from
the established tapestry vocabulary: stitched fills (`drawStitchedFill`), the period
palette, Bayeux-style linework and proportions. No element should look like it came from
a different game. This applies to the peasant, woad paint, weather border icons,
procedural buildings, chest hair, and the palisade redraw.

## 1. Bug fixes

- **Fists + hilt**: when fists is the (pre)selected weapon at battle start, no hilt is
  equipped or drawn — fists use no handle.
- **Fists range**: extend fist reach and tighten the player's approach distance so a
  melee enemy backing up can still be reached and hit.
- **Throne-mode fists**: throne-mode players with fists must be able to reach enemies —
  apply the same reach/approach fix to throne positioning.
- **Chariot + melee/fists**: the chariot must close to melee range when the player's
  weapon is melee/fists so attacks connect. Ranged chariot behaviour is unchanged.
- **Crossbow bolt size**: embedded bolts (in structures and enemies) are far too large.
  Apply the same size normalisation StuckProj already uses for arrows (divide by
  fighter.size when embedded, multiply by scaleFactor in flight).
- **Dagger**: hilt vector art is too long — shorten it substantially. Dagger wielders
  must close to a guaranteed-hit distance so they definitely land hits regardless of
  their own or the enemy's size.

## 2. Small-fighter viability

Small and medium character sizes are too squishy. Changes (all numbers tunable):

- Bonus HP: roughly +20% for small sizes, scaling with how small.
- Attack speed: scales up as size goes down.
- Fists on small/medium: much faster punch cadence, and a landed punch has ~25% chance
  to interrupt an enemy's attack windup.

Goal: small + fists is a viable playstyle, not a handicap.

## 3. Visuals

- **Barechested style**: the naked-Norman look currently renders as a white shirt with
  blue arms. Replace with skin-tone torso and arms, plus sparse chest/arm hair strokes
  drawn in the player's hair colour. Pants stay untouched.
- **Blue woad paint**: a cosmetic enemy variant with scary blue body paint on skin/face.
  No stat changes. Alongside this, increase general cosmetic variance on enemies.
- **Palisade redraw**: the palisade fence (inline in `drawFortMotte` / `drawFortDinan`)
  reads as a termite mound with spikes. Redraw as distinct pointed timber posts.
- **Procedural Bayeux buildings**: buildings with pillars, arches, and tiled roofs;
  plus taller Rapunzel-style towers with spiralling tile bands, matching structures in
  the real tapestry.

## 4. New content

### Diseased peasant ancillary

- New vector art (distinct silhouette: ragged tunic, hunched, sickly pallor with green
  tinge — tapestry style).
- Behaviour: kamikaze — sprints at the enemy pack, has very low HP, dies to the first
  hits. His infection aura afflicts any fighter who comes near him while alive; his
  corpse stays contagious for a few seconds after death.
- Disease: a mild damage-over-time (not aggressive), and victims tint green while
  infected.
- Player risk: 1% chance per 10 seconds (while the peasant or his contagion is near
  you) that the player catches it. If caught: the player takes the same mild DoT, tints
  green, **and becomes contagious to enemies who come near** — a chaotic upside.
- The reward card text must state the catch-it-yourself risk plainly.

### Divine weather events

- Four events: **lightning bolt** (strikes the nearest/strongest enemy), **flood**
  (sweeps a few enemies away), **hailstorm** (knocks enemies over), **frost** (enemies
  slip and fall).
- Acquired only through late-game rewards; a run gets one or two at most, if lucky.
- Trigger: tap a mini icon in the tapestry border → instant auto-targeted effect. No
  aiming step.
- 60-second cooldown; one charge is ready at battle start.
- Mini icons live in the tapestry border and visually resemble their effect (bolt,
  wave, hailstones, frost crystals) — designed with the frontend-design skill, in
  tapestry-border style.

### Armour weight & chariot collapse

- Armour now has weight. Over a threshold, the chariot collapses in a visible animation
  at battle start and the player fights that battle on foot. Repeats every battle while
  overweight.
- **Silken Garments** reward card: reduces weight back under the threshold while
  preserving armour level.

### Mount selector

- When the player owns more than one mount, the reward screen shows a mount selector to
  swap the active mount. UI designed with the frontend-design skill.

## 5. Stay-ahead-of-the-curve system

Theme: *the game deploys a counter; a reward card is your out.* Weapons/armour only
grow (no switching), so counters must be answerable by cards.

Counters (phased in past mid-run):

- **Shield-wall pairs**: some enemies arrive shield-locked, heavily blocking frontal
  damage. Outs: **Shieldbreaker** card (your hits splinter shields); the peasant's
  disease also bypasses shields.
- **Armoured brutes**: enemy armour tier starts outpacing player damage growth. Out:
  **Armour-Piercing Stitch** card (% of damage ignores armour); weapon-length stacking
  also helps.
- **War-priest**: occasional waves include a priest who heals his side. Outs: lightning
  bolt or any burst-damage card snipes him.

Existing pairs formalised: ranging archers ↔ healer/shield cards; armour overweight ↔
Silken Garments.

**Light reward weighting**: the reward pool detects which counter is currently
pressuring the player and *slightly* up-weights the matching out cards. Guidance only —
the player must always retain real options and choose their own direction; never force
the out.

## 6. Vector builder tooling

- Replace the hardcoded whitelists (`BUILDING_FNS`, `CREATURE_FNS`, `ANCILLARY_IDS` in
  `scripts/vector_builder/server.js`) with auto-discovery: scan the renderer files for
  `fun drawX(...)` declarations and `Ancillary.X ->` branches.
- Result: the asset list loads everything (including `drawThrone`, currently missing)
  and new assets — like the diseased peasant — appear automatically with no server
  edits.
- Weapon-handle / ranged-gear injection points remain out of scope this batch (the
  builder already fails loudly for them).

## Out of scope

- Flanker enemies + rear-guard ancillary (discussed, not picked).
- Run-level disease persistence across battles (battle/run mechanics stay as approved
  above).
- Vector-builder injection points for weapon handles / ranged gear.
