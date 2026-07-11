# New Mechanics & Combat Styles - Design Spec

## Overview
This specification covers the "New Mechanics & Combat Styles" sub-project (Group 3) for Bayeux Brawler. It introduces major new game modes (the Throne auto-battler), mount variations (Stilts), and a massive overhaul to unarmed combat with dynamic wrestling moves and bare-fisted upgrades.

## 1. The "Pallbearer Throne" Auto-Battler Mode
**Concept:** A new, alternative way to play the game where the player acts as a non-combatant commander.
**Mechanics:**
- **Trigger:** A random chance on the start screen will display a special option: *"I won't fight. A true leader makes others fight for him!"*
- **Execution:** When selected, the player rides on a throne carried by four pallbearers (two in front, two in back). 
- **Combat:** The player's configured weapon and shield are distributed to the two front pallbearers (e.g., one pallbearer wields the weapon, the other wields the shield, or they share dual-wielded weapons). These front units perform the actual auto-attacking and blocking.
- **Upgrades:** Any roguelike upgrades (attachments, armor) acquired during the run are applied to the front pallbearers.
- **Failure State:** If the front pallbearers are killed, the throne drops and the run is over.

## 2. The "Stilts" Mount
**Concept:** A comedic alternative to the Warhorse that changes hitboxes and reach dynamics.
**Mechanics:**
- **Equipping:** Stilts function as a Mount. The player can swap between unlocked mounts (e.g., Warhorse vs. Stilts) in the level-up reward menu.
- **Combat Dynamics:** 
  - The player's hitbox is elevated. Standard ground attacks from enemies cannot strike the player's body; they strike the stilts.
  - The player's weapon reach originates from the elevated height. Short weapons (e.g., daggers) will swing uselessly above the enemies' heads.
- **Durability:** Stilts possess their own HP pool. When the HP is depleted by enemy attacks, the stilts shatter. The player falls to the ground and resumes combat as a standard foot unit.

## 3. Unarmed / Wrestling Combat Overhaul
**Concept:** Starting a run completely bare-fisted unlocks an exclusive, highly dynamic unarmed combat system.
**Mechanics:**
- **Exclusive Upgrades:** If the player has no weapon head/handle equipped, the level-up pool features Brawler-specific upgrades:
  - *Gear:* Brass knuckles, spiked wraps.
  - *Stats:* Massive multipliers to fist damage.
  - *Moves:* Unlockable wrestling abilities.
- **Wrestling Abilities:**
  - *The Throw:* The player lifts an enemy overhead and hurls them into another enemy. Both enemies take damage and fall to the ground. If they survive, they stand back up to resume fighting.
  - *Weapon Steal:* The player disarms an enemy, steals their weapon, and attacks with it while retaining their brawler stat multipliers.
- **Combat Loop:** When attacking, the system randomly selects between standard punches and any unlocked wrestling moves.

## 4. Quality of Life & Fixes
- **UI Validation:** The start menu will allow players to leave the 'handle' slot empty if they have selected Ranged weapons or Fists. Javelins are the exception and can still be attached to a shaft.
- **Bandage Visuals:** Visual bandages indicating accumulated damage will no longer appear on the fighter *during* combat. They will only be applied and rendered *after* the battle is complete (e.g., on the victory screen).
- **Friendly Fire:** The combat AI targeting logic will be updated to explicitly filter out allied ancillaries (e.g., the Fanatic) to prevent accidental team-killing.
