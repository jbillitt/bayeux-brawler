# Design: BRAWL + THRONE music themes, choir voice, phone-first drums

Date: 2026-07-17
Sub-project A of 4 (A: music, B: bug fixes, C: performance, D: new content — each gets its own spec/plan cycle).

## Goal

1. Promote the "brawl" music from a mood modifier to a first-class theme: medieval speed metal wrestling music, fists-only, starting immediately at battle start.
2. Add a second new theme: epic cinematic medieval thriller (Dark Souls-esque), played only in throne mode.
3. Add a real vocal/choir synth voice (the old TTS `MedievalVocalizer` was removed in `2d1197b`; no vocal voice exists in the consort).
4. Rework drum synthesis so drums are actually audible on phone speakers and sound like drums.

Same medieval instrument palette as existing tracks throughout — only writing, voicing and mix differ.

## 1. Theme selection & plumbing

- `Family` enum (`MusicTheory.kt`) gains `BRAWL` and `THRONE`, each with its own grounds table (`BRAWL_GROUNDS`, `THRONE_GROUNDS`), tempo, metre and form — same shape as GREENSLEEVES / MINUET / TINTAGEL / ESTAMPIE.
- `resolveSongSpec` selection order: `isThroneMode` → THRONE (always; full replace of rotation); else `brawlMode` → BRAWL; else existing random rotation. Neither new family ever appears in normal rotation.
- Brawl trigger: **latch at battle start** — existing `GameViewModel.kt:788` logic (`weaponHead.id == "head_bare"` when battle begins, gated on `!isThroneMode`) is kept; it now selects the BRAWL family instead of injecting a "Brawl" mood. Stolen weapons mid-battle do not change the music. Clears on run reset as today.
- Throne trigger: when `isThroneMode` flips, song changes via the existing song-change path (next bar/phrase boundary). Throne wins over fists.
- The old "Brawl" mood hack is **deleted**: `MusicTheory.kt:104` (bpm × 1.5 AEOLIAN override) and the brawl branches in `Orchestrator.kt` (perc level, drone boost, shawm level). Its good ideas move into BRAWL's own orchestration profile.

## 2. Musical character

### BRAWL — medieval speed metal wrestling music
- Mode: AEOLIAN with occasional flattened-2nd (Phrygian sting) at cadences.
- Tempo 168–184 bpm, fixed 4/4 (no 6/8 variant). Short 2-bar grounds — riff-shaped progressions (i–bVII–i–V family), not stately sequences.
- Orchestration profile: percussion from beat one (nakers + tabor, double-time "wilder" double-hit patterns as the baseline), root+fifth drone boosted as the power chord, shawm carrying fast short aggressive phrases, hurdy-gurdy buzz as the distortion stand-in. No harp, no sparkle. Entrance ladder flattened — everything in by bar 2.

### THRONE — epic cinematic medieval thriller (Dark Souls-esque)
- Mode: DORIAN/AEOLIAN mix, chromatic neighbour tones in the bass. Slow 3/2 or 4/4 at 66–76 bpm.
- 4-bar grounds on the descending tetrachord (i–bVII–bVI–V). Long low drone a fifth deep.
- Orchestration: CHOIR pads (new voice, below) as the signature layer; bells tolling at phrase boundaries; timpani sparse but enormous (every 2 bars, not a groove); low sustained bowed strings; melody enters late, high, sparse. Fanfare/sparkle suppressed. Weather flourish machinery may stay.

## 3. CHOIR voice

- Add `Voice.CHOIR` to the enum (`InstrumentsPluck.kt` declaration); rendered in `InstrumentsBowBrass.kt` (sustained voice).
- Synthesis: soft sawtooth-ish glottal source, delayed-onset ~5 Hz vibrato, through **3 formant bandpass filters** (reuse `Biquad.bandpass` from `DspCore`) tuned to "ah"/"oh" vowels (~650/1080/2650 Hz for "ah"); 3 detuned unison layers for ensemble; slow attack/release. Vowel alternates per phrase so pads don't sound static.
- Formant band 650–2650 Hz sits in the phone-speaker sweet spot.
- Used only by THRONE for now (YAGNI; other moods can adopt it later).

## 4. Drums — phone-first single render

Phone speakers reproduce roughly 400 Hz–8 kHz; current drum fundamentals sit at 72–200 Hz and the 70 Hz master HPF strips the rest — hits read as faint clicks. Accepted trade-off: slightly boxier/punchier drums on headphones.

- `membrane()` (`InstrumentsPerc.kt`) re-voiced so 300 Hz–2 kHz content IS the drum: knock partial gains a companion body resonance (~300–500 Hz, longer decay — reads as tone, not click); slap/noise roughly doubled; sub-100 Hz modal stack demoted to a supporting role.
- Per-drum identity: TIMPANI = 250–400 Hz boom + mallet thump (THRONE's big hit); NAKERS = tight high crack (BRAWL's driver); BODHRAN = mid knock; TABOR already snare-centric at 2 kHz — level check only.
- `MixMaster` drum bus gain lifted only if re-voicing alone doesn't carry. Measure first.

## 5. Verification

- Desktop render (`AuditionMain`/`MidiTest`) writes each drum voice to WAV; assert per-hit RMS in the 300 Hz–2 kHz band dominates the sub-300 Hz band.
- Unit check: `resolveSongSpec` returns THRONE when `isThroneMode`, BRAWL when `brawlMode` (and not throne), and neither in normal rotation.
- On-device rite: fists battle (brawl kicks in at battle start), toggle throne mode (thriller replaces rotation), drums audible from the phone speaker.

## Out of scope (later sub-projects)

- B: bug fixes — backpack boy (no projectiles / grey mullet triangle / still hits head), follower keep-up speed (maude, greased-up deaf guys, running man), enemies jumping up to the Raven, hag green-tint delay, flail heads spinning like angle grinders, triple-follower too common (should hit a random existing follower or new follower; twins rare), pallbearer weapons not visually reflecting weapon upgrades in throne mode.
- C: performance — large-character lag, ~30-follower lag, heat/battery, general Play Store optimisation flags.
- D: new content — post-level-20 beast enemies + heal priests, hill/swamp terrain with camera tracking, siege mode using procedural buildings, bosses.
