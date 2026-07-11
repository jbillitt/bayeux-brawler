# The Shield & Speed Mechanics - Design Spec

## Overview
This specification outlines the updates for "The Shield & Speed Mechanics" sub-project (Group 2) in Bayeux Brawler. These features introduce destructible shields, bizarre shield-specific upgrades, and new speed-based mechanics to make small, low-mass builds highly viable in melee combat.

## 1. Breakable Shields & Shield Upgrades
**Problem:** Shields provide static buffs and act as a passive stat-stick. There is no dynamic interplay with durability or upgrading them directly with attachments.
**Solution:**
- **Secondary HP Bar:** Shields will be updated to possess a separate HP pool. When a character (player or enemy) is hit while holding a shield, damage is absorbed by the shield's HP pool first based on its blocking stats.
- **Shattering:** When a shield's HP reaches 0, it is destroyed. The renderer will play a visual particle effect (splintering wood/metal) and the `SoundSynth` will trigger a crunch sound. The character loses the shield's stats and enters an unshielded state.
- **Absurd Upgrades:** New roguelike level-up options specific to the shield:
  - *Oak Reinforcing:* Increases shield HP. Adds slight Kg mass.
  - *Iron Plating:* Greatly increases shield HP. Adds medium Kg mass.
  - *Shield Helmet:* A visual helmet drawn atop the shield. Increases shield armor defense. Adds heavy Kg mass.
- **The Mass Tradeoff:** The extra Kg mass from these upgrades will directly penalize both `moveSpeed` and `attackSpeedDelay` in `FighterState`, forcing a choice between mobility and fortress-like defense.

## 2. Speed Mechanics & "Little Guy" Viability
**Problem:** Heavy, large fighters generally dominate because mass increases damage quadratically and armor provides flat reduction. Small, fast fighters lack survivability against both melee and ranged attacks.
**Solution:**
Introduce three distinct speed-scaling advantages:
- **Ranged Deflection:** High-speed fighters gain a passive percentage chance to deflect incoming projectiles (arrows, sling stones, javelins). The chance scales linearly with `moveSpeed`.
- **Attack Interruption (Primary Melee Tactics):** If a fast attacker successfully lands a hit on a slower opponent who is currently in the middle of their attack animation, the slower opponent's attack is *interrupted*. Their swing progress is reset to 0, locking heavy juggernauts down if they are swarmed by fast strikes.
- **Melee Dodge (Secondary Melee Tactics):** A small percentage chance to evade a melee attack entirely, mitigating the damage. This dodge chance scales with speed but has a strict cap (e.g., max 15-20%) so it does not result in total invincibility.
