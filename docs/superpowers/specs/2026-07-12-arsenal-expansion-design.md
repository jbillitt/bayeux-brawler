# The Arsenal & Armoury Expansion - Design Spec

## Overview
This specification details the "Arsenal & Armoury Expansion" sub-project (Group 4) for Bayeux Brawler. It expands the gear catalog with new, highly thematic weapons and shafts, revamps the visual rendering of weapon attachments to be more chaotic, and adds new armor slots with extreme risk/reward outfits.

## 1. Expanded Weaponry & Absurd Shafts
- **New Weapon Heads:** 
  - *Urumi:* A flexible whip-sword that utilizes the existing chain physics for its rendering.
  - *Big Rock Throwing Weapon:* A slingshot/catapult hybrid.
  - *Others:* Club, Spike Mace, Proper Axe Heads, Saws.
- **New Weapon Shafts:**
  - *Ram, Plough, Tree Stump:* These handles are visually massive. They provide immense Kg mass (resulting in huge quadratic damage scaling) but severely penalize both attack speed delay and movement speed.

## 2. Organic Weapon Growth (Attachments)
**Problem:** Currently, when the player selects an `attachment` roguelike upgrade, they render somewhat uniformly, making ultra-upgraded weapons look too clean.
**Solution:**
- Update `TapestryRenderer` attachment drawing logic to be highly chaotic and imaginative:
  - **Direct Welds:** Blades and spikes can attach directly to the ends or sides of other weapon heads without needing extra handles.
  - **Asymmetrical Angles:** Attachments will render at slight, off-kilter angles (e.g., a sword blade duct-taped crookedly to a mace head).
  - **Branching:** Weapons will grow outwards asymmetrically, creating a terrifying "tree of pain" silhouette as more attachments are acquired.

## 3. Expanded Armor Slots & Comedy Outfits
- **New Slots:** The gear model (`FighterState` and customization UI) will support three new slots: `Gauntlets`, `Boots`, and `Coif`.
  - *Progression Gate:* These slots are not available immediately. They will unlock dynamically as the player reaches higher game levels.
- **Comedy Outfits (High Risk/Reward):** 
  - *Items:* Jester Hat, Cape, Pantaloons.
  - *Mechanic:* Since the game rewards unarmored builds with a 10x score multiplier, these comedy items will grant an *even higher* multiplier (e.g., 20x) while providing 0 armor.

## 4. UI Fixes
- **Victory/Share Screen:** The share screen logic currently shares the score multiplier but omits the actual final score. The share string formatting will be updated to explicitly include the `finalScore`.
