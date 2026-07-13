# Handover Document (2026-07-13, plague/weather/polish batch — mid-execution)

## Context
Brainstormed, specced, and planned a 20-task batch, then executed roughly half of it via a
mixed workflow: Claude subagents (until session limits bit) and Gemini/antigravity (worktree
branches, merged back). **9 of 20 tasks are done and on main. Main compiles and the full
unit suite is green** (`gradle testDebugUnitTest` exit 0 at commit 2607954).

## Governing documents (read these first)
- Spec: `docs/superpowers/specs/2026-07-13-plague-weather-and-polish-design.md`
- Plan (per-task briefs, model annotations, sequencing): `docs/superpowers/plans/2026-07-13-plague-weather-and-polish.md`
- Progress ledger (source of truth for what's done): `.superpowers/sdd/progress.md`
- Extracted per-task briefs + implementer reports: `.superpowers/sdd/task-N-brief.md` / `task-N-report.md`

## DONE (on main, reviewed)
- Task 1 fists never equip hilt (02a80cb) — root cause was the post-defeat gear reroll.
- Task 2 fists reach ground+throne, melee no longer backpedals, fist interrupt 25% (904bf30).
  Constants: `CombatEngine.FIST_INTERRUPT_CHANCE`, `MELEE_VS_RANGED_CHASE_MULT`.
- Task 3 small-fighter HP buff + steeper melee attack-speed size scale (8cbcf50).
- Task 4 chariot closes to melee range — approach subtracts mount reach (bb917ae).
- Task 5 crossbow bolts: new `ProjectileType.BOLT`, smaller flying + embedded (e6ed854, 85bd18a).
- Task 6 dagger hilt 15f draw length; short-reach (<1.2f) weapons approach to 0.6x boundary (e8a409e).
- Task 7 armour weight: `ARMOR_WEIGHT_LIMIT = 22f` in GameViewModel; chariot collapse at battle
  start spawns a BROKEN_CHARIOT background object + popup; `silken_garments` card gates recovery
  (merged in 6d45064/5b8123b; collapsed-wreck art ported as 2607954 — drawChariot now takes
  `fighter: FighterState?, isCollapsed: Boolean`).
- Task 12 mount selector on reward screen; `BattleSimState.activeMount`, `selectMount()` (6d45064).
- Task 18 vector builder auto-discovery (274a786): `discoverAssets()` scans renderer files for
  `fun drawX(` (minus EXCLUDED_FNS) + ancillary branches; verify script confirms 22 regions
  roundtrip clean.

## NOT DONE (11 tasks, all in main session's queue)
Tasks 8, 9 (plague peasant behaviour then art), 10, 11 (weather core then icons), 13 (curve
counters), 14 (barechested), 15 (woad), 16 (palisade), 17 (procedural buildings), 18b (JSON
art port — must run AFTER 16 & 17), 19 (final verification, playtest checklist, bump to
v0.3.1, final whole-branch review vs merge-base dae44d1^..). Briefs for 8-17 not yet
extracted (`scripts/task-brief` in the subagent-driven-development skill dir does it).

## Known minor findings (feed to Task 19's final review)
Recorded in `.superpowers/sdd/progress.md`: EOF blank-line cruft in 4 files; indentation slip
at the `hasSilkenGarments` copy() in GameViewModel; `totalArmorMass` computed twice
(startBattle + reward gen); ballista stuck-shaft factor changed 1.0→0.4 / cap 1.8→1.2 —
**eyeball ballista spears on device**; mount thumbnail dummy FighterState per recompose.

## Process facts worth knowing
- User has Gemini/antigravity CLI for outsourcing; handover statement pattern worked but
  Gemini's "all merged" claim was false for 2 of 8 tasks — ALWAYS audit `git log` + worktrees
  after an external agent reports. Its stranded work sat on branches; salvaged by cherry-pick
  (task 18) and file-scoped diff apply (task 7 art).
- Four stale Gemini-internal worktrees remain registered (under `C:/Users/Josh/.gemini/...brain...`);
  left alone deliberately — `git worktree prune` after deleting those dirs if the user agrees.
- Untracked cruft: `test_out.txt` (Gemini's), `tapestry_options.html`, `logcat*.txt` — user
  wants to be asked before deletion.
- Uncommitted pre-existing edit: `docs/superpowers/specs/2026-07-12-environment-and-vector-builder-design.md`.
- User preference: no more Claude subagents this run (credit burn) — execute remaining tasks
  inline. Remaining tasks were re-annotated opus/low-medium in the plan.
- Build: global gradle (`gradle compileDebugKotlin` / `gradle testDebugUnitTest`), no wrapper.
  adb at `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`. Never add assets under `app/build/`.
- Art direction rule for all remaining visual tasks: distinct at a glance, but tapestry
  vocabulary only (drawStitchedFill, period palette, Bayeux linework).
