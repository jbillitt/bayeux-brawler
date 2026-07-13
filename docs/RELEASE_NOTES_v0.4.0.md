# v0.4.0 "Northumbria"

The biggest batch yet: a plague, four acts of God, an answer to the late-game wall, and a long
overdue apology to anyone who chose to fight with their fists.

## New

**The Plague-Bearer.** Recruit Wretched Aldwin, a dying peasant with 15 hit points and no weapon,
who sprints at the enemy and breathes on them. His pestilence spreads to everyone standing near him
— living or freshly dead — and rots them from the inside. There is a small chance you catch it too.
He is not a good man to stand behind.

**Divine weather.** From level 12, the heavens can be persuaded to intervene. Hold up to two, called
down by tapping their embroidered roundel in the tapestry border:

- **Divine Bolt** — the sky smites your mightiest foe.
- **The Deluge** — a wall of water sweeps the furthest enemies off the field entirely, horse and all.
- **Hailstorm** — fist-sized hail batters every foe flat.
- **Killing Frost** — ice underfoot; the enemy host slips and falls.

**The Saxons have answers now, and so do you.** Shield-wall pairs lock up from level 12, armoured
brutes lumber in from 15, and from 18 a war-priest walks behind the host mending whoever is worst
hurt — kill him first, or you will cut them down slower than he sews them up. Meet a counter and its
answer joins your reward pool: **Shieldbreaker** (triple damage to shields) and the
**Armour-Piercing Stitch** (a third of your damage ignores armour). It is offered, never forced.

**Mount selector.** Own more than one mount and you choose which one you ride, on the reward screen.

**Armour has weight.** Load yourself with iron scale and the war chariot collapses under you at the
start of the battle — a wreck on the field and a long walk. The **Silken Garments** card lightens
your armour below the limit without costing you an ounce of protection.

## Balance

- **Small fighters are viable.** Tiny and small builds get bonus health and swing markedly faster.
  They are no longer fragile as wet parchment, and the descriptions no longer claim they are.
- **Fists work.** They reach further, they no longer stall at arm's length while the enemy backs away,
  they connect from the throne, and small brawlers punch fast enough to interrupt a foe mid-swing.
- **Poison and bleed now do what they say.** They were secretly landing a full point of damage *every
  frame* — a "4 per second" poison was dealing about sixty. Fixed. They are much weaker now, and
  correct. Expect the Hag to feel different.
- The war chariot closes to melee range instead of circling politely out of reach.
- Short-reach weapons (daggers) step properly inside the hitbox rather than whiffing at the edge.

## Fixed

- Choosing bare fists no longer straps a mysterious hilt to your hands.
- Crossbow bolts and ballista spears embed at a sane size instead of harpooning half the screen.
- The dagger's grip is a grip, not a broomstick.
- **Bare-chested means bare-chested** — skin and chest hair, not a crisp white shirt with blue sleeves.
- **The palisade is made of timber again.** It had become a smooth lump with spikes on top, which read
  as a termite mound. It is now individual sharpened posts, uneven, crooked, with daylight between them.

## Also

- **Blue woad.** About one Saxon in seven daubs himself before battle — bars across the eyes, a stripe
  down the jaw, a spiral on a bare chest. Enemy faces, hair and builds vary far more widely, so a wave
  no longer looks like one man cloned eight times.
- **New scenery**: procedural Bayeux halls with arcades of round-headed arches, and tall spiral-tiled
  towers with pennants. No two are the same building.

## Under the hood

- Building art is now data-driven (`assets/art/*.json`) and can be edited in the vector builder's new
  workbench with no compiler involved. New art is *discovered*, not registered: drop in a file, it
  appears in the game.
- The art can now be rendered to PNG in a unit test, so it can actually be looked at without a device.
  This caught the termite-mound palisade and an arcade that was drawing as black blobs.
