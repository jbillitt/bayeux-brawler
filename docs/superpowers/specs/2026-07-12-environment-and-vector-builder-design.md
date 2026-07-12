# Bayeux Brawler: Environment, Tapestry Renderer, and Vector Builder Spec

## 1. Environments & Progression
**Reference Images for Implementation:**
- Ship Intro Inspiration: [Harold's Ship](https://www.vikingeskibsmuseet.dk/frontend/_processed_/1/4/csm_Bayeux2_Harold2_160a6b1299.jpg)

**Fixed Stages:** The game transitions from an infinite single screen to fixed-length stages (e.g., Level 1 is 1.5 screens, Level 5 is 3 screens wide). The camera tracks the player's `posX`, locking them within the stage bounds until the wave/quota is defeated.
**The Ship Intro:** On Level 1, the player spawns on a large Ship structure at the left edge (`X = 0`). The player must disembark the gangplank to trigger the UI and the first enemy wave.
**Hills & Tactical Height:** The environment introduces variable height terrain (`posY` modifiers linked to `posX`). Characters attacking from a higher `posY` gain slight reach and damage bonuses. Terrain acts as physical cover for projectiles.

## 2. Forts, Buildings & Destructibility
**Reference Images for Implementation:**
- Building Spawners (Range Cover): [Small Tapestry House](https://medievalists.gumlet.io/wp-content/uploads/2025/01/mnet25013105.jpg?compress=true&format=webp&quality=80&w=376&dpr=2.6)
- Sallying Lords Fort: [Dinan Castle/Fort](https://www.ianvisits.co.uk/articles/wp-content/uploads/sites/2/2025/12/Bayeux-Tapestry.jpg)

**Hybrid Generation System:** High-level stages (Level 5+) generate Forts and Villages using a mix of Handcrafted Prefabs (e.g., "Saxon Farm") and Modular Assembly (snapping random towers, gates, and walls together).
**Sallying Lords:** These forts act as enemy spawner hubs. Approaching a fort causes elite units and Lords (wearing crowns) to spawn and charge out.
**Organic Destructibility:** Buildings and Forts have hidden health pools and hitboxes. Stray heavy weapon strikes and projectiles deal damage. Upon depletion, the building collapses into a "ruined" frayed state, removing range cover.

## 3. The Woven Aesthetic (Tapestry Renderer 2.0)
**Satin Stitch Fills:** Large colored paths (tunics, roofs, large shields) will be filled with directional, tightly packed zigzag lines instead of flat vectors to mimic authentic Bayeux embroidery.
**Fraying & Wear (VFX):** As characters take damage or buildings collapse, the game spawns loose thread particles and draws trailing frayed yarn on the sprites. 
**Dynamic Fabric Lighting:** A subtle ambient lighting/bump-map overlay will be applied across the coarse linen grid, reacting to the scrolling camera to simulate museum lighting moving across physical threads.

## 4. Vector Builder Enhancements & Auto-Inject
**Kotlin Import/Export:** The `scripts/vector_builder/index.html` tool will be upgraded to parse and load Kotlin `Path` syntax (from `TapestryRenderer.kt`).
**Fill & Color Toggling:** The tool will feature a UI to select enclosed paths and apply colors or the new Satin Stitch logic.
**Rendering Previews:** The tool will include a browser-native preview of the new Satin Stitch and Fraying VFX.
**Auto-Inject Workflow (Node Backend):** To achieve auto-injection, we will write a lightweight local Node.js server (`scripts/vector_builder/server.js`) that runs alongside the web app. 
- When you save an asset in the UI, the web app sends the JSON/Path data to the local server.
- The server directly injects the `Path` code into `TapestryRenderer.kt`.
- The server will report back to the UI indicating exactly which enum objects in `SimulationModels.kt` need to be updated with new stats (e.g., adding a new `WeaponHead` entry) so you know exactly where to apply the mechanical balancing.

### Appendix: How Weapon Heads attach to Shafts
In `TapestryRenderer.kt` (`drawWeapon`), the game calculates a directional vector based on the handle's length (`handleLen`). It starts drawing the handle at `hx, hy` (the hand position) and uses simple trigonometry to calculate the tip of the handle (`shaftEnd`). It then assigns `headPos = shaftEnd` and passes that `headPos` down into `drawWeaponHead()`, which uses it as the origin point `(0,0)` to draw the specific metal weapon head at the exact end of the wooden shaft.
