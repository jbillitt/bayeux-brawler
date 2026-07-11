# Bayeux Brawler - Master Implementation Plan

This is the execution plan for all 6 groups of changes, organized by your requested priorities. We will tackle Phase 1 first.

## Phase 1: Tooling & DX Refactor (Top Priority)
*Ensures you can easily add gear yourself with compiler guidance, and provides the visualizer tool.*

1. **Kotlin Exhaustive Type Refactor**
   - Locate where `GameData.WEAPON_HEADS`, `WEAPON_HANDLES`, `SHIELDS`, etc., are defined.
   - Refactor them into Kotlin `enum class` or `sealed class`.
   - Update `TapestryRenderer.kt` and `GameViewModel.kt`. By replacing `if/else` with exhaustive `when` blocks on these enums, the Kotlin compiler will natively flag errors anytime you add a new enum value but forget to provide its rendering or stat logic.
2. **HTML5 Vector Tool & Visualizer**
   - Create a new directory at `scripts/vector_builder/`.
   - Build a standalone, purely local `index.html` + `app.js` tool.
   - Features: 
     - **Item Configurator:** Start by selecting item type (Weapon Head, Shield, Hat, Face, Hair) and giving it a name.
     - **Color Palette:** Select thread/fill colors from the existing Bayeux aesthetic palette.
     - **Mannequin Toggle:** A background layer showing the default player character as a reference guide (so you know where the hand or head is), which is excluded from the final code export.
     - **Drawing Tools:** A grid canvas with line/path drawing tools.
     - **Animation Visualizer:** A "Simulate Swing/Walk" animation toggle.
     - **Exporter:** Spits out the formatted Kotlin `Path` block and enum entry stub based on your selected item type.
3. **Pain Sounds (Custom Audio)**
   - Create the `app/src/main/res/raw/` directory.
   - Update audio playback logic to check for user-dropped sound files (e.g., `pain_1.wav`).
   - Implement a random chance (e.g., 30%) for these to play on hit to prevent spamming.

## Phase 2: Engine & Progression Scaling (Group 1)
1. **Enemy Spawning Cap:** Hard-cap spawned enemies to 10 in `GameViewModel.kt`.
2. **Stat Scaling:** Convert excess spawn counts into a `lateGameMultiplier` applied to enemy HP, damage, and speed.
3. **Player Status Immunity:** Explicitly block `Bleed` and `Poison` from being applied to the player.
4. **Cavalry Buff:** Apply +50% movement speed to mounted enemies.
5. **Ranged Escalation:** Add level checks (`>15`, `>20`, `>25`) to ranged enemy logic for attack speed, multishot, and projectile speed.
6. **Dynamic Versioning:** Sync start menu text with `BuildConfig.VERSION_NAME` and increment patch.

## Phase 3: Shield & Speed Mechanics (Group 2)
1. **Breakable Shields:** Add a `shieldHp` state. Render shattering particles and play crunch sound when it hits 0.
2. **Shield Upgrades:** Add Oak Reinforcing, Iron Plating, and Shield Helmet to the roguelike pool with respective mass penalties.
3. **Speed Advantages:** 
   - Add ranged deflection chance based on speed.
   - Add logic to interrupt slow enemy attack progress when hit by a faster attacker.
   - Add a capped melee dodge chance.

## Phase 4: New Mechanics & Styles (Group 3)
1. **The Throne:** Add random start menu option. Implement logic to spawn 4 pallbearers and distribute player stats/gear to the front two.
2. **Stilts Mount:** Add Stilts as a selectable mount with elevated hitboxes and its own HP pool.
3. **Brawler Overhaul:** If unarmed, unlock wrestling moves (Throw, Weapon Steal) and Brawler gear (knuckles, stat boosts). Randomize attack selection.

## Phase 5: Arsenal & Armoury (Group 4)
1. **New Gear Arrays:** Add Urumi, slingshot, ram/plough shafts.
2. **Organic Attachments:** Refactor `TapestryRenderer` attachment drawing to use randomized angles, branching, and direct-welding without handles.
3. **New Armor Slots:** Add Gauntlets, Boots, and Coifs. Gate their appearance behind higher levels.
4. **Comedy Outfits:** Add Jester Hat/Cape/Pantaloons for massive score multi (20x) but 0 armor. Fix score display on the share screen.

## Phase 6: Companions (Group 5)
1. **Local Hag:** Spawns in backline, lobs mud, applies slow/minor poison.
2. **Trojan Horse:** Rolls forward, absorbs hits, spawns 3 knights on death in the enemy backline.
3. **Lil Guy on Back:** Renders on player's back, passively shoots projectiles.
4. **Battle Surgeon:** Spawns behind player, applies passive slow HP regen.
