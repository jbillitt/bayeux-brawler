# BRAWL + THRONE Themes, Choir Voice, Phone-First Drums — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote brawl music to a first-class BRAWL family (medieval speed metal), add a THRONE family (Dark Souls-esque cinematic thriller) with a new CHOIR synth voice, and re-voice the drums so they read on phone speakers.

**Architecture:** The procedural music stack is `resolveSongSpec` (MusicTheory.kt: family/mode/tempo/ground) → `generateSong` (MelodyGenerator.kt) → `planOrchestration` (Orchestrator.kt: voice/line/level assignments) → `ProceduralMedievalComposer.compose` (renders to a stereo PCM loop) → `MedievalHarpPlayer` (AudioTrack streaming, in-phase crossfade on change) ← `MainActivity` LaunchedEffect. We add two Family values selected by explicit flags (never in rotation), give each its own orchestration profile, add `Voice.CHOIR` (formant-filtered sustained voice), and re-voice `membrane()` in InstrumentsPerc.kt.

**Tech Stack:** Kotlin, Android (AudioTrack), plain JUnit4 JVM unit tests in `app/src/test/java/com/example/game/` (run with Gradle, no device needed).

## Global Constraints

- Package: everything lives in `com.example.game` (tests share the package — internal/private-in-file access rules apply).
- No new dependencies; synthesis reuses `Biquad` from `DspCore.kt` and helpers (`midiHz`, `normalise`) already used by the instrument files.
- Selection order in `resolveSongSpec`: `throne` → THRONE (always wins), else `brawl` → BRAWL, else existing random rotation. **BRAWL and THRONE must never appear in normal rotation.**
- The old "Brawl" mood string is deleted end-to-end (MusicTheory branch, Orchestrator checks, MainActivity append). No compatibility shim.
- Moods do **not** alter BRAWL or THRONE (fixed character themes).
- BRAWL: AEOLIAN (occasionally PHRYGIAN), 168–184 bpm, 4/4, 16 bars. THRONE: DORIAN/AEOLIAN, 66–76 bpm, 4/4, 16 bars, descending-tetrachord grounds.
- Grounds are **8-entry** lists — `MelodyGenerator.kt` and `Orchestrator.kt` index `spec.ground[bar % 8]`.
- Test command (Windows): `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"` (expect BUILD SUCCESSFUL / FAILED per step).
- `MixMaster` drum-bus gain is intentionally untouched (YAGNI): re-voicing is verified by band-RMS tests; revisit the bus only if the final on-device rite fails.
- Commits: conventional style (`feat:`/`test:`/`refactor:`), `--no-verify` not needed unless hooks block; keep each task a separate commit.

---

### Task 1: BRAWL + THRONE families in MusicTheory

**Files:**
- Modify: `app/src/main/java/com/example/game/MusicTheory.kt`
- Modify: `app/src/main/java/com/example/game/Orchestrator.kt:35,54-60,66-72` (keep exhaustive `when`s compiling)
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: existing `SongSpec`, `Mode`, `GroundBar`.
- Produces: `Family.BRAWL`, `Family.THRONE`, `Mode.PHRYGIAN`, and
  `fun resolveSongSpec(seed: Long, moods: List<String>, brawl: Boolean = false, throne: Boolean = false): SongSpec` — later tasks rely on these exact names/defaults.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/example/game/SoundTest.kt`, **delete** the now-obsolete `brawlMoodIsFastMinorAndAudible()` test (the "Brawl" mood is being removed) and add:

```kotlin
    @Test
    fun throneAndBrawlSelectionOrder() {
        for (seed in 1L..10L) {
            assertEquals(Family.THRONE, resolveSongSpec(seed, emptyList(), brawl = true, throne = true).family)
            assertEquals(Family.BRAWL, resolveSongSpec(seed, emptyList(), brawl = true, throne = false).family)
            val normal = resolveSongSpec(seed, emptyList()).family
            assertTrue("BRAWL/THRONE must never appear in rotation", normal != Family.BRAWL && normal != Family.THRONE)
        }
    }

    @Test
    fun brawlThemeIsSpeedMetalShaped() {
        for (seed in 1L..10L) {
            val spec = resolveSongSpec(seed, emptyList(), brawl = true)
            assertTrue("brawl bpm ${spec.bpm}", spec.bpm in 168..184)
            assertEquals(4, spec.beatsPerBar)
            assertEquals(8, spec.ground.size)
            assertTrue("minor mode wanted, got ${spec.mode}", spec.mode == Mode.AEOLIAN || spec.mode == Mode.PHRYGIAN)
        }
    }

    @Test
    fun throneThemeIsSlowAndDark() {
        for (seed in 1L..10L) {
            val spec = resolveSongSpec(seed, emptyList(), throne = true)
            assertTrue("throne bpm ${spec.bpm}", spec.bpm in 66..76)
            assertTrue(spec.mode == Mode.DORIAN || spec.mode == Mode.AEOLIAN)
            assertEquals(8, spec.ground.size)
            // Descending tetrachord head: i - bVII - bVI - V
            assertEquals(listOf(0, 6, 5, 4), spec.ground.take(4).map { it.bassDegree })
        }
    }

    @Test
    fun moodsDoNotBendThemedFamilies() {
        val spec = resolveSongSpec(7L, listOf("More Tempo", "More Tempo", "Merrier"), brawl = true)
        assertTrue("moods must not push brawl off 168-184, got ${spec.bpm}", spec.bpm in 168..184)
        assertTrue(spec.mode == Mode.AEOLIAN || spec.mode == Mode.PHRYGIAN)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: compilation FAILS (`unresolved reference: BRAWL` / no value passed for parameter mismatch) — that counts as the failing state.

- [ ] **Step 3: Implement in MusicTheory.kt**

Replace the `Family` and `Mode` declarations:

```kotlin
enum class Family { GREENSLEEVES, MINUET, TINTAGEL, ESTAMPIE, BRAWL, THRONE }

enum class Mode(val steps: IntArray) {
    DORIAN(intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    IONIAN(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10)),
    PHRYGIAN(intArrayOf(0, 1, 3, 5, 7, 8, 10))   // the flat-2 sting for BRAWL cadences
}
```

Below `ESTAMPIE_GROUNDS`, add:

```kotlin
private val BRAWL_GROUNDS = listOf(
    listOf(0, 6, 0, 4, 0, 6, 0, 4),   // i bVII i V riff, twice round
    listOf(0, 6, 0, 6, 0, 6, 4, 0),   // double-tonic hammer with a V turn
    listOf(0, 4, 0, 4, 0, 6, 4, 0)    // i V i V power alternation
)
private val THRONE_GROUNDS = listOf(
    listOf(0, 6, 5, 4, 0, 6, 5, 4),   // descending tetrachord i bVII bVI V (lament bass)
    listOf(0, 6, 5, 4, 0, 5, 3, 4)    // tetrachord answered by bVI iv V
)

// Normal rotation only — BRAWL and THRONE are trigger-selected, never rolled.
private val ROTATION = listOf(Family.GREENSLEEVES, Family.MINUET, Family.TINTAGEL, Family.ESTAMPIE)
```

Rewrite `resolveSongSpec` (signature + family pick + new branches + gated moods + caps). Full replacement:

```kotlin
fun resolveSongSpec(seed: Long, moods: List<String>, brawl: Boolean = false, throne: Boolean = false): SongSpec {
    val rng = Random(seed * 31L + 7L)   // spec stream, separate from melody/orch
    // Throne wins over fists: the lord isn't punching.
    val family = when {
        throne -> Family.THRONE
        brawl -> Family.BRAWL
        else -> ROTATION[rng.nextInt(ROTATION.size)]
    }

    var mode: Mode
    var bpm: Int
    val beatsPerBar: Int
    val totalBars: Int
    val groundDegrees: List<Int>
    when (family) {
        Family.GREENSLEEVES -> {
            mode = Mode.DORIAN
            bpm = 56 + rng.nextInt(17)           // 56-72 (dotted crotchet)
            beatsPerBar = 6; totalBars = 32
            groundDegrees = GREENSLEEVES_GROUNDS[rng.nextInt(GREENSLEEVES_GROUNDS.size)]
        }
        Family.MINUET -> {
            mode = if (rng.nextBoolean()) Mode.IONIAN else Mode.MIXOLYDIAN
            bpm = 92 + rng.nextInt(21)           // 92-112
            beatsPerBar = 3; totalBars = 32
            groundDegrees = MINUET_GROUNDS[rng.nextInt(MINUET_GROUNDS.size)]
        }
        Family.TINTAGEL -> {
            mode = if (rng.nextBoolean()) Mode.AEOLIAN else Mode.DORIAN
            bpm = 60 + rng.nextInt(17)           // 60-76
            beatsPerBar = 4; totalBars = 16
            groundDegrees = TINTAGEL_GROUNDS[rng.nextInt(TINTAGEL_GROUNDS.size)]
        }
        Family.ESTAMPIE -> {                     // fast duple dance — the consort's up-tempo leg
            mode = if (rng.nextBoolean()) Mode.DORIAN else Mode.MIXOLYDIAN
            bpm = 112 + rng.nextInt(25)          // 112-136
            beatsPerBar = 4; totalBars = 32
            groundDegrees = ESTAMPIE_GROUNDS[rng.nextInt(ESTAMPIE_GROUNDS.size)]
        }
        Family.BRAWL -> {                        // medieval speed metal — fists only
            mode = if (rng.nextInt(10) < 3) Mode.PHRYGIAN else Mode.AEOLIAN
            bpm = 168 + rng.nextInt(17)          // 168-184
            beatsPerBar = 4; totalBars = 16
            groundDegrees = BRAWL_GROUNDS[rng.nextInt(BRAWL_GROUNDS.size)]
        }
        Family.THRONE -> {                       // epic cinematic thriller — throne mode only
            mode = if (rng.nextBoolean()) Mode.DORIAN else Mode.AEOLIAN
            bpm = 66 + rng.nextInt(11)           // 66-76
            beatsPerBar = 4; totalBars = 16
            groundDegrees = THRONE_GROUNDS[rng.nextInt(THRONE_GROUNDS.size)]
        }
    }
    var finalMidi = 45 + rng.nextInt(8)          // A2-G#3
    if (family == Family.THRONE) finalMidi -= 4  // the court sits deep
    var ornament = when (family) {
        Family.MINUET -> 0.6f; Family.GREENSLEEVES -> 0.4f; Family.TINTAGEL -> 0.25f
        Family.ESTAMPIE -> 0.5f
        Family.BRAWL -> 0.3f                     // already fast; divisions would smear
        Family.THRONE -> 0.15f                   // sparse, ominous
    }

    // Mood deltas (applied in list order, stackable). Themed families ignore moods:
    // BRAWL and THRONE are fixed-character replacements, not flavours of the run tune.
    if (family != Family.BRAWL && family != Family.THRONE) {
        for (m in moods) when (m) {
            "More Tempo"  -> bpm = (bpm * 1.25).toInt()
            "Merrier"     -> { mode = brighten(mode); ornament = (ornament + 0.35f).coerceAtMost(1.5f) }
            "More Solemn" -> { mode = darken(mode); bpm = (bpm * 0.85).toInt(); ornament = (ornament - 0.2f).coerceAtLeast(0.05f) }
            "Nobler"      -> { bpm = (bpm * 0.90).toInt(); finalMidi -= 3 }
            "Wilder"      -> { bpm = (bpm * 1.10).toInt(); ornament = (ornament + 0.4f).coerceAtMost(2.0f) }
        }
    }
    // Stacked tempo moods multiply without bound; past these caps the note tails smear across
    // chord changes and everything reads as discord. Nobler stacks -3 each pick; below MIDI 39
    // the counter-voice falls out of its playable register.
    bpm = bpm.coerceIn(40, when {
        family == Family.BRAWL -> 184
        family == Family.THRONE -> 76
        beatsPerBar == 6 -> 84
        else -> 150
    })
    finalMidi = finalMidi.coerceIn(39, 52)

    val spb = if (beatsPerBar == 6) 60f / bpm / 3f else 60f / bpm
    return SongSpec(
        seed = seed, family = family, mode = mode, finalMidi = finalMidi,
        beatsPerBar = beatsPerBar, secondsPerBeat = spb, bpm = bpm,
        ground = groundDegrees.map { GroundBar(it) }, totalBars = totalBars,
        ornamentDensity = ornament, moods = moods
    )
}
```

Note: the deleted `"Brawl"` mood branch (old line 104) is gone for good.

- [ ] **Step 4: Keep Orchestrator.kt compiling**

Three touch-ups (behaviour for BRAWL/THRONE becomes bespoke in Tasks 3/5; this just keeps the exhaustive `when`s legal and retargets the brawl flag):

At line 35, replace `val brawl = "Brawl" in moods` with:

```kotlin
    val brawl = spec.family == Family.BRAWL
```

In the `soloist` pick (`when (spec.family)` at ~line 54), add before the closing brace:

```kotlin
        else -> listOf(Voice.HARP to 0.6f, Voice.LUTE to 0.4f)   // BRAWL/THRONE take bespoke plans (Tasks 3/5)
```

In the `second` pick (`when (spec.family)` at ~line 66), add before the closing brace:

```kotlin
        else -> listOf(Voice.VIELLE to 0.6f, Voice.RECORDER to 0.4f)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL, all SoundTest tests pass. Note `throneAndBrawlSelectionOrder` passes even though `compose` isn't plumbed yet — it only exercises `resolveSongSpec`.

- [ ] **Step 6: Run the full unit suite (Family/Mode are widely visible)**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. If `MainActivity`'s `+ "Brawl"` still compiles (it's just a string) that's fine — it's now inert and removed in Task 7.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/game/MusicTheory.kt app/src/main/java/com/example/game/Orchestrator.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: BRAWL and THRONE first-class music families, Brawl mood deleted"
```

---

### Task 2: Plumb brawl/throne flags through the composer

**Files:**
- Modify: `app/src/main/java/com/example/game/ProceduralMedievalComposer.kt:16-31`
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: `resolveSongSpec(seed, moods, brawl, throne)` from Task 1.
- Produces: `fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList(), brawl: Boolean = false, throne: Boolean = false): ShortArray` — Task 7's player calls exactly this.

- [ ] **Step 1: Write the failing test**

Add to `SoundTest.kt`:

```kotlin
    @Test
    fun brawlComposeIsAudible() {
        val buf = ProceduralMedievalComposer.compose(7L, 1, false, 22050, emptyList(), brawl = true)
        assertTrue(buf.any { it != 0.toShort() })
    }
```

(The THRONE render test waits for Task 5 — its plan needs `Voice.CHOIR`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: compilation FAILS — `compose` has no `brawl` parameter.

- [ ] **Step 3: Implement**

In `ProceduralMedievalComposer.kt` change the `compose` signature and the two lines that consume the flags:

```kotlin
    fun compose(seed: Long, level: Int, hasTrumpeter: Boolean, sampleRate: Int, moods: List<String> = emptyList(), brawl: Boolean = false, throne: Boolean = false): ShortArray {
```

```kotlin
        val spec = resolveSongSpec(seed, moods, brawl, throne)
```

and replace `val wilder = "Wilder" in moods || "Brawl" in moods` with:

```kotlin
        // Brawl keeps the wilder double-hit drum floor; it's a family now, not a mood.
        val wilder = "Wilder" in moods || spec.family == Family.BRAWL
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL, all pass (BRAWL renders through the generic plan for now — bespoke profile lands in Task 3).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/game/ProceduralMedievalComposer.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: compose() takes brawl/throne flags"
```

---

### Task 3: BRAWL orchestration profile + nakers gallop

**Files:**
- Modify: `app/src/main/java/com/example/game/Orchestrator.kt` (new `planBrawlOrchestration`, early dispatch, delete old brawl branches, NAKERS gallop in `percussionEvents`)
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: `Family.BRAWL`, `SongSpec`, existing `VoiceAssignment`/`LineRef`/`OrchestrationPlan`.
- Produces: `planOrchestration` returns an all-in-by-level-1 BRAWL plan with `destinyFanfare = false`; `percussionEvents` emits a per-bar NAKERS gallop when `spec.family == Family.BRAWL`.

- [ ] **Step 1: Write the failing tests**

Add to `SoundTest.kt`:

```kotlin
    @Test
    fun brawlPlanIsAllInByLevelOne() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(7L))
        val voices = plan.assignments.map { it.voice }.toSet()
        assertTrue("no harp in the pit", Voice.HARP !in voices)
        assertTrue("no sparkle bells/psaltery", plan.assignments.none { (it.voice == Voice.BELLS || it.voice == Voice.PSALTERY) })
        assertTrue("no destiny fanfare", !plan.destinyFanfare)
        val l1 = activeAssignments(plan, 1).map { it.voice }
        assertTrue("drums+lead from the first bell: $l1",
            Voice.NAKERS in l1 && Voice.TABOR in l1 && Voice.SHAWM in l1 && Voice.GURDY in l1)
    }

    @Test
    fun brawlNakersGallopEveryBar() {
        val spec = resolveSongSpec(7L, emptyList(), brawl = true)
        val events = percussionEvents(spec, Voice.NAKERS, wilder = true)
        // 8 eighth-note hits per 4/4 bar, every bar — the engine of the theme.
        assertEquals(8 * spec.totalBars, events.size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: FAIL — `brawlPlanIsAllInByLevelOne` (generic plan includes HARP/soloist, fanfare may roll true), `brawlNakersGallopEveryBar` (generic NAKERS fires every 4th bar only).

- [ ] **Step 3: Implement**

In `Orchestrator.kt`, at the top of `planOrchestration` (right after `val a = mutableListOf<VoiceAssignment>()` — or before it; the early returns don't use the generic locals), add:

```kotlin
    when (spec.family) {
        Family.BRAWL -> return planBrawlOrchestration(spec)
        Family.THRONE -> return planThroneOrchestration(spec, hasTrumpeter)   // Task 5
        else -> {}
    }
```

(Until Task 5 exists, add a temporary `planThroneOrchestration` that returns the choirless bones — see Step 3b.)

Then **delete the old brawl mood hacks** from the generic path (they're dead now that BRAWL early-returns):
- `val brawl = spec.family == Family.BRAWL` → delete the val; replace its three uses:
  - `val percLevel = if (brawl) 1 else 4` → `val percLevel = 4`
  - `gPerc1 * (if (brawl) 1.3f else 1f)` → `gPerc1`
  - nakers condition: `if (spec.family == Family.TINTAGEL || spec.family == Family.ESTAMPIE)` (drop `|| brawl`)
  - `val droneLevel = if (brawl) 1 else 5` → `val droneLevel = 5`
  - delete `if (brawl) gDrone *= 1.5f   // the root+fifth drone IS the power chord`
  - `val wilder = wildCount > 0 || brawl` → `val wilder = wildCount > 0`
  - `val shawmLevel = if (brawl) 2 else if (wilder) 7 else 9` → `val shawmLevel = if (wilder) 7 else 9`

Add the bespoke profile after `planOrchestration`:

```kotlin
/** BRAWL: medieval speed metal. Entrance ladder flattened — everything is in by bar 2,
 *  because the pit crew doesn't do entrances. No harp, no sparkle, no fanfare. */
private fun planBrawlOrchestration(spec: SongSpec): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.NAKERS, LineRef.PERC, 1, 99, 0.50f, 0.4f)         // the driver: gallop
    a += VoiceAssignment(Voice.TABOR, LineRef.PERC, 1, 99, 0.40f, -0.45f)        // backbeat snare
    a += VoiceAssignment(Voice.GURDY, LineRef.DRONE, 1, 99, 0.40f, -0.6f)        // root+fifth power chord
    a += VoiceAssignment(Voice.GURDY, LineRef.SPARKLE, 1, 99, 0.55f, -0.6f)      // trompette buzz = the distortion
    a += VoiceAssignment(Voice.SHAWM, LineRef.MELODY, 1, 99, 0.48f, 0f)          // fast short aggressive lead
    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 2, 99, 0.36f, -0.25f)
    a += VoiceAssignment(Voice.SACKBUT, LineRef.PADS_ROOT, 3, 99, 0.16f, -0.15f)
    a += VoiceAssignment(Voice.HORN, LineRef.PADS_FIFTH, 3, 99, 0.13f, 0.15f)
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 5, 99, 0.36f, 0f)
    a += VoiceAssignment(Voice.SHAWM, LineRef.MELODY_ORN, 7, 99, 0.30f, 0.3f, octave = 1)
    a += VoiceAssignment(Voice.FIDDLE2, LineRef.COUNTER, 9, 99, 0.20f, 0.5f)
    return OrchestrationPlan(a, destinyFanfare = false)
}
```

**Step 3b (temporary stub, replaced in Task 5):**

```kotlin
private fun planThroneOrchestration(spec: SongSpec, hasTrumpeter: Boolean): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 1, 99, 0.34f, -0.3f)
    a += VoiceAssignment(Voice.GURDY, LineRef.DRONE, 1, 99, 0.26f, -0.7f)
    return OrchestrationPlan(a, destinyFanfare = false)
}
```

In `percussionEvents`, replace the NAKERS arm:

```kotlin
            Voice.NAKERS -> if (spec.family == Family.BRAWL) {
                // Double-time gallop: the wilder double-hit is the FLOOR here, not the ceiling.
                var b = 0f
                while (b < bpb - 1e-3f) {
                    val accent = b % 2f == 0f
                    out += NoteEvent(base + b, 0.25f, if (accent) 56 else 57, if (accent) 1f else 0.6f)
                    b += 0.5f
                }
            } else if (bar % 4 == 0) {
                out += NoteEvent(base, 0.4f, 56, 0.9f); out += NoteEvent(base + 0.5f, 0.3f, 57, 0.6f)
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL, all pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/game/Orchestrator.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: bespoke BRAWL orchestration - flattened ladder, nakers gallop, no sparkle"
```

---

### Task 4: CHOIR voice

**Files:**
- Modify: `app/src/main/java/com/example/game/InstrumentsPluck.kt:5,20-21` (enum + dispatch)
- Modify: `app/src/main/java/com/example/game/InstrumentsBowBrass.kt` (renderer + `choir()`)
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: `Biquad.bandpass(sr: Int, fc: Float, q: Float)`, `midiHz`, `normalise` (all already imported/visible in InstrumentsBowBrass.kt — same package, used by `bowed()`).
- Produces: `Voice.CHOIR` renderable through `renderNote` for any midi/duration. Task 5 assigns it.

- [ ] **Step 1: Write the failing test**

Add to `SoundTest.kt` (the `bandRms` helper is reused by Task 6 — put it at the bottom of the class):

```kotlin
    @Test
    fun choirEnergyLivesInTheFormantBand() {
        val buf = renderNote(Voice.CHOIR, 57, 1.5f, 1f, 44100, kotlin.random.Random(5))
        val formant = bandRms(buf, 44100, 400f, 3000f)
        val sub = bandRms(buf, 44100, 20f, 300f)
        assertTrue("formant band $formant should dominate sub band $sub", formant > sub)
    }

    /** RMS of x after a 2nd-order highpass at lo and lowpass at hi (crude band meter). */
    private fun bandRms(x: FloatArray, sr: Int, lo: Float, hi: Float): Double {
        val hp = Biquad.highpass(sr, lo, 0.707f)
        val lp = Biquad.lowpass(sr, hi, 0.707f)
        var acc = 0.0
        for (v in x) { val y = lp.process(hp.process(v)); acc += y * y }
        return Math.sqrt(acc / x.size)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: compilation FAILS — `unresolved reference: CHOIR`.

- [ ] **Step 3: Implement**

`InstrumentsPluck.kt` line 5 — add CHOIR after ORGAN (keep perc voices contiguous at the end):

```kotlin
enum class Voice { HARP, LUTE, PSALTERY, VIELLE, VIOLA, FIDDLE2, RECORDER, PANPIPES, SHAWM, SACKBUT, HORN, GURDY, ORGAN, CHOIR, BELLS, NAKERS, TIMPANI, BODHRAN, TABOR, TAMBOURINE }
```

`InstrumentsPluck.kt` line 20 — add CHOIR to the sustained group:

```kotlin
        Voice.VIELLE, Voice.VIOLA, Voice.FIDDLE2, Voice.SHAWM, Voice.SACKBUT, Voice.HORN, Voice.GURDY, Voice.ORGAN, Voice.CHOIR ->
            renderBowBrass(voice, midi, durSec, velocity, sr, rng)
```

`InstrumentsBowBrass.kt` — add to the `renderBowBrass` dispatch:

```kotlin
    Voice.CHOIR   -> choir(midi, durSec, sr, rng)
```

and add the synth at the end of the file:

```kotlin
/** Sustained vocal ensemble: three detuned soft-saw "glottal" layers pushed through vowel
 *  formant bandpasses. The formants (450-2830 Hz) sit squarely in the phone-speaker band,
 *  which is the whole point — the fundamental barely matters on a handset. */
private fun choir(midi: Int, durSec: Float, sr: Int, rng: Random): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val f0 = midiHz(midi)
    // Two vowels, alternated per note so held pads breathe instead of droning.
    val ah = rng.nextBoolean()
    val formants = if (ah) floatArrayOf(650f, 1080f, 2650f) else floatArrayOf(450f, 800f, 2830f)
    val fAmps = if (ah) floatArrayOf(1.0f, 0.50f, 0.18f) else floatArrayOf(1.0f, 0.35f, 0.10f)
    val filters = Array(3) { Biquad.bandpass(sr, formants[it], 8f) }
    val detune = doubleArrayOf(0.0, -7.0, 6.0)   // cents — unison ensemble spread
    val phases = DoubleArray(3) { rng.nextDouble() * 2.0 * Math.PI }
    val dt = 1.0 / sr
    val attack = minOf(0.35f, durSec * 0.3f).coerceAtLeast(0.01f)
    val release = minOf(0.5f, durSec * 0.3f).coerceAtLeast(0.01f)
    for (i in 0 until n) {
        val t = i * dt
        // Delayed-onset vibrato: straight tone at first, ~5 Hz sway blooming after half a second.
        val vibDepth = ((t - 0.5) / 0.7).coerceIn(0.0, 1.0) * 0.007
        val vib = 1.0 + vibDepth * Math.sin(2.0 * Math.PI * 5.1 * t)
        var s = 0.0
        for (v in 0 until 3) {
            val f = f0 * Math.pow(2.0, detune[v] / 1200.0) * vib
            phases[v] += 2.0 * Math.PI * f * dt
            var layer = 0.0
            for (k in 1..5) layer += Math.sin(k * phases[v]) / k   // soft saw: 5 harmonics, 1/k rolloff
            s += layer
        }
        var y = 0f
        for (b in 0 until 3) y += filters[b].process((s * 0.33).toFloat()) * fAmps[b]
        y += (rng.nextFloat() * 2f - 1f) * 0.015f                  // breath keeps the top formant alive
        val tf = t.toFloat()
        val env = (tf / attack).coerceAtMost(1f) * ((durSec - tf) / release).coerceIn(0f, 1f)
        out[i] = y * env
    }
    normalise(out, 0.85f)
    return out
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL. `everyVoiceRendersAudibleSound` now exercises CHOIR automatically (peak > 0.05, no clipping) alongside the new formant test.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/game/InstrumentsPluck.kt app/src/main/java/com/example/game/InstrumentsBowBrass.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: CHOIR voice - formant-filtered detuned ensemble"
```

---

### Task 5: THRONE orchestration profile + doom timpani

**Files:**
- Modify: `app/src/main/java/com/example/game/Orchestrator.kt` (real `planThroneOrchestration`, TIMPANI throne pattern)
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: `Voice.CHOIR` (Task 4), `Family.THRONE` (Task 1), `bellCadenceEvents` mapping (BELLS+SPARKLE tolls every 8 bars — already in ProceduralMedievalComposer).
- Produces: choir-led THRONE plan; `percussionEvents` TIMPANI arm emits one enormous stroke every 2 bars when `spec.family == Family.THRONE`.

- [ ] **Step 1: Write the failing tests**

Add to `SoundTest.kt`:

```kotlin
    @Test
    fun thronePlanIsChoirLedAndFanfareFree() {
        val spec = resolveSongSpec(7L, emptyList(), throne = true)
        val plan = planOrchestration(spec, hasTrumpeter = false, rng = orchRng(7L))
        val l1 = activeAssignments(plan, 1).map { it.voice }
        assertTrue("choir pads from level 1: $l1", Voice.CHOIR in l1)
        assertTrue("timpani + bells from level 1", Voice.TIMPANI in l1 && Voice.BELLS in l1)
        assertTrue("melody enters late", Voice.VIELLE !in l1)
        assertTrue(!plan.destinyFanfare)
        assertTrue("no harp/psaltery sparkle", plan.assignments.none { it.voice == Voice.HARP || it.voice == Voice.PSALTERY })
    }

    @Test
    fun throneTimpaniIsSparseButEnormous() {
        val spec = resolveSongSpec(7L, emptyList(), throne = true)
        val events = percussionEvents(spec, Voice.TIMPANI, wilder = false)
        val downbeats = events.filter { it.velocity >= 1f }
        // One full-weight stroke every 2 bars, not a groove.
        assertEquals(spec.totalBars / 2, downbeats.size)
    }

    @Test
    fun throneComposeIsAudible() {
        val buf = ProceduralMedievalComposer.compose(7L, 1, false, 22050, emptyList(), throne = true)
        assertTrue(buf.any { it != 0.toShort() })
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: FAIL — the Task 3 stub plan has no CHOIR/TIMPANI/BELLS; timpani still uses the bar%8 groove.

- [ ] **Step 3: Implement**

Replace the Task 3 stub in `Orchestrator.kt`:

```kotlin
/** THRONE: epic cinematic medieval thriller. Choir pads are the signature layer; bells toll
 *  at phrase boundaries; timpani sparse but enormous; melody enters late, high, sparse.
 *  No fanfare, no harp, no psaltery sparkle. */
private fun planThroneOrchestration(spec: SongSpec, hasTrumpeter: Boolean): OrchestrationPlan {
    val a = mutableListOf<VoiceAssignment>()
    a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_ROOT, 1, 99, 0.30f, -0.2f)    // the signature layer
    a += VoiceAssignment(Voice.CHOIR, LineRef.PADS_FIFTH, 1, 99, 0.24f, 0.2f)
    a += VoiceAssignment(Voice.GURDY, LineRef.DRONE, 1, 99, 0.26f, -0.7f)        // long low root+fifth bed
    a += VoiceAssignment(Voice.VIOLA, LineRef.BASS, 1, 99, 0.34f, -0.3f)         // low sustained bowed line
    a += VoiceAssignment(Voice.TIMPANI, LineRef.PERC, 1, 99, 0.44f, 0f)          // sparse but enormous
    a += VoiceAssignment(Voice.BELLS, LineRef.SPARKLE, 1, 99, 0.26f, 0.6f)       // tolls at phrase ends
    a += VoiceAssignment(Voice.VIELLE, LineRef.MELODY, 4, 99, 0.34f, 0.1f, octave = 1)  // late, high, sparse
    a += VoiceAssignment(Voice.CHOIR, LineRef.COUNTER, 7, 99, 0.18f, 0.35f)
    a += VoiceAssignment(Voice.ORGAN, LineRef.PADS_FULL, 10, 99, 0.12f, 0f)
    if (hasTrumpeter) a += VoiceAssignment(Voice.HORN, LineRef.TRUMPETER, 2, 99, 0.20f, 0.3f)
    return OrchestrationPlan(a, destinyFanfare = false)
}
```

In `percussionEvents`, replace the TIMPANI arm:

```kotlin
            Voice.TIMPANI -> if (spec.family == Family.THRONE) {
                // A doom bell, not a groove: one enormous stroke every 2 bars,
                // answered by a darker fifth-below at the phrase turn.
                if (bar % 2 == 0) out += NoteEvent(base, 2.0f, spec.finalMidi, 1f)
                if (bar % 8 == 7) out += NoteEvent(base + bpb - 1f, 1.0f, spec.finalMidi - 5, 0.8f)
            } else {
                if (bar % 8 == 0) out += NoteEvent(base, 1.2f, spec.finalMidi, 1f)
                if (bar % 8 == 7) for (r in 0 until 6) out += NoteEvent(base + bpb - 1f + r * (1f / 6f), 0.15f, spec.finalMidi, 0.4f + 0.08f * r)
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL, all pass. (`throneTimpaniIsSparseButEnormous`: 16 bars → 8 downbeat strokes at velocity 1f; the bar%8==7 answer is 0.8f so it doesn't count.)

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/game/Orchestrator.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: THRONE orchestration - choir pads, tolling bells, doom timpani"
```

---

### Task 6: Phone-first drum re-voicing

**Files:**
- Modify: `app/src/main/java/com/example/game/InstrumentsPerc.kt:5-13,42-67`
- Test: `app/src/test/java/com/example/game/SoundTest.kt`

**Interfaces:**
- Consumes: `bandRms` helper from Task 4's test step.
- Produces: `membrane(...)` gains a `bodyAmp: Float = 0.9f` parameter; NAKERS/TIMPANI/BODHRAN call sites re-voiced. TABOR untouched (already snare-centric).

- [ ] **Step 1: Write the failing tests**

Add to `SoundTest.kt`:

```kotlin
    @Test
    fun drumsSpeakInThePhoneBand() {
        val sr = 44100
        for (voice in listOf(Voice.NAKERS, Voice.TIMPANI, Voice.BODHRAN)) {
            val buf = renderNote(voice, 45, 0.5f, 1f, sr, kotlin.random.Random(3))
            val mid = bandRms(buf, sr, 300f, 2000f)
            val low = bandRms(buf, sr, 20f, 300f)
            assertTrue("$voice mid band $mid must dominate sub-300 band $low", mid > low)
        }
        // Tabor is snare-centric by design — check its voice band instead.
        val tabor = renderNote(Voice.TABOR, 45, 0.5f, 1f, sr, kotlin.random.Random(3))
        assertTrue("tabor snare band must dominate", bandRms(tabor, sr, 500f, 4000f) > bandRms(tabor, sr, 20f, 300f))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: FAIL for NAKERS/TIMPANI/BODHRAN (fundamental modal stack dominates today). If TABOR's assertion also fails, note the measured numbers — its fix is a level trim at the call site, not a re-voice.

- [ ] **Step 3: Implement**

Replace `membrane` in `InstrumentsPerc.kt`:

```kotlin
/** Drum membrane: modal damped sines + skin noise; pitch drop is phase-integrated (no v1 chirp bug).
 *  PHONE-FIRST VOICING: the sub-100 Hz modal stack is a supporting role — handsets can't reproduce
 *  it and the 70 Hz master HPF eats half of it anyway. The audible identity is the strike: a
 *  mid-frequency knock partial (slapAmp), a companion BODY resonance (~300-520 Hz, decays slower
 *  so the hit reads as a TONE, not a click), and a broadband skin slap. */
private fun membrane(f0: Double, durSec: Float, sr: Int, rng: Random, t60: Float, drop: Double, noiseAmp: Float, slapAmp: Float, bodyAmp: Float = 0.9f): FloatArray {
    val n = (sr * durSec).toInt(); val out = FloatArray(n); if (n == 0) return out
    val modes = doubleArrayOf(1.0, 1.5, 1.98, 2.44)
    val amps = floatArrayOf(0.5f, 0.22f, 0.14f, 0.07f)   // demoted from 1.0/0.4/0.25/0.12
    val dt = 1.0 / sr
    val phases = DoubleArray(modes.size)
    val skin = Biquad.bandpass(sr, 2400f, 1.2f)
    var knock = 0.0; var body = 0.0
    val knockHz = (f0 * 3.7).coerceIn(220.0, 900.0)   // stays in the small-speaker sweet spot
    val bodyHz = (f0 * 4.4).coerceIn(300.0, 520.0)
    val bodyT60 = (t60 * 0.55f).coerceIn(0.08f, 0.5f)
    for (i in 0 until n) {
        val t = i * dt
        val bend = 1.0 + drop * Math.exp(-t / 0.06)      // starts sharp, settles - integrated below
        var s = 0.0
        for (m in modes.indices) {
            phases[m] += 2.0 * Math.PI * f0 * modes[m] * bend * dt
            s += amps[m] * Math.exp(-6.907755 * t / (t60 * (1.0 - 0.15 * m))) * Math.sin(phases[m])
        }
        knock += 2.0 * Math.PI * knockHz * bend * dt
        s += slapAmp * Math.exp(-6.907755 * t / 0.05) * Math.sin(knock)
        body += 2.0 * Math.PI * bodyHz * bend * dt
        s += bodyAmp * Math.exp(-6.907755 * t / bodyT60) * Math.sin(body)
        var v = (s * 0.5).toFloat()
        if (t < 0.025) v += skin.process(rng.nextFloat() * 2f - 1f) * noiseAmp * (1f - (t / 0.025f).toFloat())
        out[i] = v
    }
    normalise(out, 0.9f)
    return out
}
```

Re-voice the call sites in `renderPerc` (slap/noise roughly doubled, per-drum identity):

```kotlin
    // NAKERS: tight high crack — BRAWL's driver.
    Voice.NAKERS     -> membrane(if (midi % 2 == 0) 150.0 else 200.0, durSec, sr, rng, t60 = 0.18f, drop = 0.03, noiseAmp = 0.95f, slapAmp = 1.3f, bodyAmp = 0.7f)
    // TIMPANI: 300-520 Hz boom + mallet thump — THRONE's big hit.
    Voice.TIMPANI    -> membrane(midiHz(midi).coerceIn(80.0, 120.0), durSec, sr, rng, t60 = 1.1f, drop = 0.04, noiseAmp = 0.55f, slapAmp = 0.7f, bodyAmp = 1.1f)
    // BODHRAN: mid knock.
    Voice.BODHRAN    -> membrane(72.0, durSec, sr, rng, t60 = 0.13f, drop = 0.05, noiseAmp = 0.85f, slapAmp = 1.2f, bodyAmp = 0.9f)
```

TABOR: only if its Step 2 assertion failed, raise the snare-band mix in `tabor()` from `0.45f`/`0.30f` to `0.60f`/`0.40f` and re-run; otherwise leave it alone.

- [ ] **Step 4: Run the full SoundTest suite**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.game.SoundTest"`
Expected: BUILD SUCCESSFUL — including `everyVoiceRendersAudibleSound` (no clipping from the new body partial; `normalise(out, 0.9f)` guards the ceiling).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/game/InstrumentsPerc.kt app/src/test/java/com/example/game/SoundTest.kt
git commit -m "feat: phone-first drum voicing - body resonance, hotter slap, demoted sub-bass"
```

---

### Task 7: Player + UI plumbing, mood-hack removal, on-device rite

**Files:**
- Modify: `app/src/main/java/com/example/game/MedievalHarpPlayer.kt:43-44,64-72`
- Modify: `app/src/main/MainActivity.kt` — sorry, exact path: `app/src/main/java/com/example/MainActivity.kt:109-121`
- Modify: `app/src/main/java/com/example/game/SimulationModels.kt:700` (comment only)
- Modify: `app/src/main/java/com/example/game/GameViewModel.kt:787-790` (comment only)
- Test: full unit suite + manual device rite

**Interfaces:**
- Consumes: `compose(..., brawl, throne)` from Task 2.
- Produces: `MedievalHarpPlayer.startMusic(level: Int = 1, hasTrumpeter: Boolean = false, moods: List<String> = emptyList(), brawl: Boolean = false, throne: Boolean = false)`.

- [ ] **Step 1: MedievalHarpPlayer**

Add two cache fields next to `currentMoods` (line ~44):

```kotlin
    private var currentBrawl = false
    private var currentThrone = false
```

Replace `startMusic`'s head and compose call:

```kotlin
    fun startMusic(level: Int = 1, hasTrumpeter: Boolean = false, moods: List<String> = emptyList(), brawl: Boolean = false, throne: Boolean = false) {
        if (isPlaying && currentLevel == level && currentTrumpeter == hasTrumpeter && currentMoods == moods && currentBrawl == brawl && currentThrone == throne) return
        currentLevel = level; currentTrumpeter = hasTrumpeter; currentMoods = moods; currentBrawl = brawl; currentThrone = throne
```

```kotlin
                val buf = ProceduralMedievalComposer.compose(gameSeed, level, hasTrumpeter, SAMPLE_RATE, moods, brawl, throne)
```

(The existing in-phase crossfade IS the song-change path — flipping throne/brawl swaps themes over 250 ms at matching loop phase; no new machinery.)

- [ ] **Step 2: MainActivity**

Delete the `appliedMusicMoods` shadowing line and its comment:

```kotlin
    // Brawl rides the existing moods pipe: the LaunchedEffect keys on this list, so flipping
    // brawlMode recomposes the same run-seed tune as its speed-metal variant (in-phase crossfade).
    val appliedMusicMoods = if (uiState.brawlMode) uiState.appliedMusicMoods + "Brawl" else uiState.appliedMusicMoods
```

Replace the LaunchedEffect:

```kotlin
    // Brawl and Throne are first-class themes, not moods: the effect keys on the two flags, so
    // flipping either swaps the theme via the player's in-phase crossfade. Throne wins over fists
    // inside resolveSongSpec.
    LaunchedEffect(musicOn, uiState.level, hasTrumpeter, uiState.appliedMusicMoods, uiState.brawlMode, uiState.isThroneMode, uiState.gameCount) {
        if (musicOn) {
            MedievalHarpPlayer.startMusic(level = uiState.level, hasTrumpeter = hasTrumpeter, moods = uiState.appliedMusicMoods,
                brawl = uiState.brawlMode, throne = uiState.isThroneMode)
        } else {
            MedievalHarpPlayer.stopMusic()
        }
    }
```

- [ ] **Step 3: Comment updates (no behaviour)**

`SimulationModels.kt:700`: change `// Drives the medieval-speed-metal music variant ("Brawl" mood).` to `// Drives the medieval-speed-metal BRAWL music theme.`

`GameViewModel.kt:788` comment block: change `flips the music to the brawl (speed-metal) variant` to `flips the music to the BRAWL (speed-metal) theme`. The latch logic itself (`brawlMode = it.brawlMode || (!it.isThroneMode && it.weaponHead.id == "head_bare")`) is **unchanged** — stolen weapons mid-battle don't touch it; run reset already clears it at line 1558.

- [ ] **Step 4: Verify nothing references the "Brawl" mood string anymore**

Run: `git grep -n "\"Brawl\"" -- app/src`
Expected: **no matches** in music code (`FlavourText.kt`'s battle-name list "Brawl" is unrelated and stays).

- [ ] **Step 5: Full unit suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, everything green.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/game/MedievalHarpPlayer.kt app/src/main/java/com/example/MainActivity.kt app/src/main/java/com/example/game/SimulationModels.kt app/src/main/java/com/example/game/GameViewModel.kt
git commit -m "feat: wire brawl/throne theme flags through player and UI, retire Brawl mood"
```

- [ ] **Step 7: On-device rite (manual, human-in-the-loop)**

Install a debug build on a phone and verify on the **built-in speaker**:
1. Start a battle with fists equipped → speed-metal BRAWL hits from the first bar (drums + shawm immediately, no slow ladder).
2. Steal a weapon mid-battle → music does not change.
3. Take the throne → thriller THRONE replaces whatever was playing (crossfade); choir pads audible; leave throne → previous behaviour returns.
4. Drums are clearly audible as drums (tone, not clicks) from the phone speaker at arm's length.
5. Only if step 4 fails: revisit `MixMaster` drum bus gain (deliberately deferred).

---

## Self-Review Notes (done at write time)

- **Spec coverage:** §1 selection/plumbing → Tasks 1, 2, 7. §2 BRAWL → Tasks 1, 3; THRONE → Tasks 1, 5. §3 CHOIR → Task 4. §4 drums → Task 6 (MixMaster deferral is explicit in spec §4 and Task 7 step 7). §5 verification → band-RMS tests (Task 4/6), selection unit tests (Task 1), on-device rite (Task 7). Spec's "chromatic neighbour tones in the bass" is delivered as the half-step-rich lament tetrachord + PHRYGIAN colour — the degree-based engine has no chromatic path and building one fails YAGNI; noted as an accepted simplification.
- **Type consistency:** `resolveSongSpec(seed, moods, brawl, throne)` and `compose(..., brawl, throne)` used identically in Tasks 1/2/5/7; `bandRms(x, sr, lo, hi)` defined Task 4, reused Task 6; `planThroneOrchestration(spec, hasTrumpeter)` stubbed Task 3, replaced Task 5 with same signature.
- **Sequencing:** every task compiles and passes the full suite on its own; BRAWL renders via generic plan between Tasks 2 and 3 (tests only assert what's built so far).
