# Handover Document

## Context
We have just completed an extensive brainstorming session for Bayeux Brawler, a Kotlin Compose Canvas medieval auto-battler. We broke down a massive feature list into 6 distinct sub-projects and generated design specs for all of them.

## The Goal
We need to begin execution on **Phase 1 (Top Priority)** of the master implementation plan. The user wants to start with the tooling and DX refactor so they can continue working on the game without relying on AI credits.

## Key Resources
All specs and the master plan have been saved to the project directory:
- **Master Plan:** `docs/superpowers/specs/master_implementation_plan.md` (READ THIS FIRST)
- **Phase 1-6 Specs:** Located in `docs/superpowers/specs/` (e.g., `2026-07-12-tooling-and-visuals-design.md`, `2026-07-12-progression-scaling-design.md`, etc.)
- **Architecture Overview:** `ARCHITECTURE.md` (Read to understand the custom Canvas engine and state flow).

## Next Steps for the New Agent
1. Read `master_implementation_plan.md`.
2. Acknowledge this handover and confirm you are ready to begin **Phase 1**.
3. Phase 1 consists of three main tasks:
   - **Task A:** Refactor `GearItem` IDs in `SimulationModels.kt` to use Kotlin `enum class` to enforce exhaustive `when` statements in the `TapestryRenderer.kt` drawing logic.
   - **Task B:** Build a standalone HTML5/JS web app (`scripts/vector_builder/index.html`) to allow the user to draw and animate custom gear, outputting the Kotlin `Path` syntax.
   - **Task C:** Create an `app/src/main/res/raw` audio folder for custom pain sounds, and update the audio logic in `SoundSynth.kt` to play them on a random chance.

Please begin by tackling **Phase 1, Task A**!
