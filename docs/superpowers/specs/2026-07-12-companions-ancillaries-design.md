# Companions & Ancillaries - Design Spec

## Overview
This specification details the "Companions & Ancillaries" sub-project (Group 5) for Bayeux Brawler. It introduces four new follower types to the roguelike reward pool, expanding the tactical variety of the player's warband with mud-slinging hags, back-mounted turrets, passive healers, and backline siege engines.

## 1. The Local Hag
**Concept:** A low-fantasy debuff/control unit.
**Mechanics:**
- **Placement:** She spawns behind the player's front line, remaining safely out of immediate melee range.
- **Action:** She periodically scoops up mud from the ground and lobs it as a projectile at the enemy frontline.
- **Effect:** Upon impact, the mud deals minor poison damage and applies a localized "slow" debuff, reducing the affected enemies' `moveSpeed` and `attackSpeedDelay` for a short duration.

## 2. The Trojan Horse Squad
**Concept:** A delayed-release shock troop spawner that targets the enemy backline.
**Mechanics:**
- **Placement:** It spawns on the battlefield as an independent, allied entity (it is not ridden by the player).
- **Action:** It slowly rolls forward toward the enemy forces, drawing aggro and absorbing damage thanks to a massive HP pool.
- **The Payload:** When its HP reaches 0, the Trojan Horse is destroyed. Upon destruction, it instantly spawns 3 allied knights. Because it has rolled forward, these knights will spawn in the middle or rear of the enemy formation, effectively flanking the Saxons.

## 3. "Lil Guy on Back"
**Concept:** A close-range turret that physically attaches to the player.
**Mechanics:**
- **Placement:** The renderer will draw this small ancillary clinging to the back of the player's torso.
- **Action:** Operates independently of the player's attack cooldowns. While the player is engaged in combat, the "Lil Guy" will constantly throw small projectiles (rocks, mud, small daggers) over the player's shoulder at nearby enemies, providing a steady stream of supplementary damage.

## 4. The Battle Surgeon
**Concept:** A sustain-focused support unit for long endurance waves.
**Mechanics:**
- **Placement:** Follows safely behind the player.
- **Action:** Does not engage in combat. Instead, the Surgeon provides a slow, periodic passive heal to the player character during the battle.
- **Balance:** The heal rate should be balanced to help out-sustain long levels without out-pacing the burst damage of enemy champions.
