# Engine & Progression Scaling - Design Spec

## Overview
This specification outlines the updates for the "Engine & Progression Scaling" sub-project (Group 1) for Bayeux Brawler. These changes address late-game performance degradation by capping on-screen entities, ensure the game remains challenging by scaling enemy stats dynamically instead of adding raw numbers, and introduce escalating mechanics for enemy cavalry and ranged units.

## 1. Enemy Spawning Cap & Stat Scaling
**Problem:** The current `enemiesCount` formula scales infinitely with the level, resulting in severe performance drops when too many enemies (e.g., 15+) are rendered simultaneously on the Canvas.
**Solution:**
- Introduce a hard cap on the maximum number of enemies generated per wave: **10**.
- If the original `enemiesCount` calculation yields a number greater than 10, the excess is converted into a `lateGameMultiplier`.
  - Example: If the formula generates 15 enemies, spawn 10 enemies, and grant them all a `1.5x` multiplier.
- The `lateGameMultiplier` is applied to:
  - `maxHp` (and starting `hp`)
  - `baseDamage`
  - Attack speed (reduces delay)

## 2. Late-Game "Weird" Enemy Upgrades
**Problem:** Late-game enemies currently use standardized loadouts, whereas the player gets absurd roguelike weapon attachments (spikes, extra blades, extensions).
**Solution:**
- Starting at Level 12+, enemies have a percentage chance (e.g., `25%` scaling up with level) to spawn with random `attachment` and `extension` upgrades on their weapons.
- **Player Protection (Status Immunity):** To balance these powerful enemies and prevent unavoidable instantaneous deaths, the combat simulation will explicitly block enemies from applying `Bleed` or `Poison` status effects to the player. The player remains capable of inflicting these on enemies.

## 3. Escalating Ranged Threats
**Problem:** Ranged enemies (archers, slingers, etc.) fall behind in threat level as the player's movement speed and armor increase in the late game.
**Solution:**
- Ranged enemies will evolve based on the current level:
  - **Level 15+:** Attack cooldown delay is reduced by 40% (Machine-gunners).
  - **Level 20+:** Ranged attacks spawn 2 to 3 projectiles per swing in a vertical spread pattern (Multishot).
  - **Level 25+:** Projectile speed and damage are doubled (Snipers).
- These effects stack (e.g., at Level 25, they fire 2-3 fast, high-damage projectiles with a short cooldown).

## 4. Fast Cavalry
**Problem:** Mounted enemies do not close the distance fast enough to act as real shock troops.
**Solution:**
- Any enemy generated with the `isMounted` flag will receive a flat `+50%` multiplier to their movement speed calculation, allowing them to charge down the player rapidly.

## 5. Dynamic Versioning & Increment
**Problem:** The version number in the start menu is hardcoded text, which easily falls out of sync with the actual build version.
**Solution:**
- Update `MainActivity.kt` (or the relevant Compose UI) to read the version dynamically from `BuildConfig.VERSION_NAME`.
- Increment the patch version of the project (e.g., v0.1.2 -> v0.1.3) during implementation to adhere to the project's development rules.
