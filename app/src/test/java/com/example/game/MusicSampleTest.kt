package com.example.game

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders real composer output to WAVs in `app/build/music_samples/` so the music can be listened
 * to instead of argued about. Nothing is asserted here beyond "it produced audio" — the point is
 * the files. Filenames carry the seed, family, mode, tempo and strum habit, so a complaint about
 * one sample maps straight back to the spec that made it.
 */
class MusicSampleTest {
    private val outDir = File("build/music_samples")

    @Test
    fun renderSamplesToListenTo() {
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }

        val sr = 22050
        // Level 8 so the full palette is playing, including the harp strum where the seed rolled it.
        val jobs = (1L..12L).map { Triple(it, false, false) } +
            listOf(3L to true, 7L to true).map { Triple(it.first, true, false) } +
            listOf(3L to true, 7L to true).map { Triple(it.first, false, true) }

        for ((seed, brawl, throne) in jobs) {
            val spec = resolveSongSpec(seed, emptyList(), brawl, throne)
            val plan = planOrchestration(spec, hasTrumpeter = true, rng = orchRng(seed))
            val strums = plan.assignments.any { it.line == LineRef.STRUM }
            val pcm = ProceduralMedievalComposer.compose(
                seed = seed, level = 8, hasTrumpeter = true, sampleRate = sr,
                brawl = brawl, throne = throne
            )
            // Name it after the PIECE when it drew one — the family is only who is playing, and a
            // Cantiga filed under "ESTAMPIE" is unidentifiable when you are listening through
            // sixteen of these trying to work out which one sounded wrong.
            val what = spec.piece?.name ?: spec.family.name
            val name = "seed%02d_%s_%s_%dbpm_%s%s%s.wav".format(
                seed, what, spec.mode, spec.bpm,
                if (strums) "strum-" + strumPlacementFor(spec) else "nostrum",
                if (spec.piece?.vocal == true) "_SUNG" else "",
                if (spec.beatsPerBar == 6) "_6-8" else ""
            )
            writeStereoWav(File(outDir, name), pcm, sr)
            println("wrote $name  (${pcm.size / 2 / sr}s)")
        }
        println("samples in ${outDir.absolutePath}")
    }

    /**
     * Trailer hunting: a batch of fists-only (BRAWL) tracks, which is the only family that gets the
     * nakers gallop. Rendered at 44.1k rather than the game's 22.05k — this output is for a video
     * editor, not for SoundPool.
     */
    @Test
    fun renderBrawlSamplesForTrailer() {
        val dir = File("build/music_samples_brawl")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val sr = 44100
        var leaps = 0
        var steps = 0
        var widest = 0
        var onDownbeat = 0
        for (seed in 1L..12L) {
            val spec = resolveSongSpec(seed, emptyList(), brawl = true)

            // "Scattered" is a claim about melodic intervals, so measure them rather than argue.
            // Split by WHERE the hop lands: the motif only governs hops inside a bar. A hop onto a
            // downbeat is the arch re-anchoring, which is a different mechanism and a different fix.
            val mel = generateSong(spec, melodyRng(seed)).melody
            val bpb = spec.beatsPerBar.toFloat()
            for ((a, b) in mel.zipWithNext()) {
                val hop = Math.abs(b.midi - a.midi)
                val ontoDownbeat = (b.startBeat % bpb) < 0.02f
                if (hop > 4) {
                    leaps++
                    if (ontoDownbeat) onDownbeat++
                }
                steps++
                widest = maxOf(widest, hop)
            }

            val pcm = ProceduralMedievalComposer.compose(
                seed = seed, level = 8, hasTrumpeter = true, sampleRate = sr, brawl = true
            )
            val name = "brawl_seed%02d_%s_%dbpm_%dbars.wav".format(
                seed, spec.mode, spec.bpm, spec.totalBars
            )
            writeStereoWav(File(dir, name), pcm, sr)
            println("wrote $name  (${pcm.size / 2 / sr}s)")
        }
        // Control group: the same measurement on the ordinary families, which nobody has complained
        // about. Without it the BRAWL percentage is a number with nothing to be better or worse than.
        var cLeaps = 0; var cSteps = 0; var cWidest = 0; var cDown = 0
        for (seed in 1L..12L) {
            val spec = resolveSongSpec(seed, emptyList(), brawl = false)
            val bpb = spec.beatsPerBar.toFloat()
            for ((a, b) in generateSong(spec, melodyRng(seed)).melody.zipWithNext()) {
                val hop = Math.abs(b.midi - a.midi)
                if (hop > 4) { cLeaps++; if ((b.startBeat % bpb) < 0.02f) cDown++ }
                cSteps++
                cWidest = maxOf(cWidest, hop)
            }
        }

        val stats = ("BRAWL   : %d/%d hops wider than a major third (%.1f%%), widest %d st, %.0f%% on a downbeat%n" +
            "ORDINARY: %d/%d hops wider than a major third (%.1f%%), widest %d st, %.0f%% on a downbeat")
            .format(leaps, steps, 100f * leaps / steps, widest, 100f * onDownbeat / leaps,
                cLeaps, cSteps, 100f * cLeaps / cSteps, cWidest, 100f * cDown / cLeaps)
        // Gradle swallows test stdout, and this number is the whole point of the run.
        File(dir, "_melody_stats.txt").writeText(stats)
        println(stats)
        println("samples in ${dir.absolutePath}")
    }

    /**
     * Diagnostics for the "the strum sounds slightly discordant" complaint. Prints, never asserts —
     * the point is to get real numbers in front of a decision instead of a plausible theory.
     */
    @Test
    fun strumDiagnostics() {
        for (seed in 1L..24L) {
            val spec = resolveSongSpec(seed, emptyList())
            val events = harpStrumEvents(spec)
            if (events.isEmpty()) continue
            val bpb = spec.beatsPerBar.toFloat()

            // Roll speed, in real milliseconds between strings.
            val stacks = events.groupBy { (it.startBeat / bpb).toInt() }
            val firstStack = stacks.values.first()
            val gapMs = if (firstStack.size < 2) 0f
                else (firstStack[1].startBeat - firstStack[0].startBeat) * spec.secondsPerBeat * 1000f
            val spreadMs = if (firstStack.size < 2) 0f
                else (firstStack.last().startBeat - firstStack.first().startBeat) * spec.secondsPerBeat * 1000f

            // How far each stack rings past its own bar, and whether the bar it rings into is a
            // different chord. Harp tails are added again at render time (releaseTail), so this is
            // the floor, not the total.
            var worstBleed = 0f
            var bleedIntoOtherChord = 0
            for ((bar, stack) in stacks) {
                val barEnd = (bar + 1) * bpb
                val ringEnd = stack.maxOf { it.startBeat + it.durBeats }
                val bleed = ringEnd - barEnd
                if (bleed > 0f) {
                    worstBleed = maxOf(worstBleed, bleed)
                    if (spec.ground[bar % 8].bassDegree != spec.ground[(bar + 1) % 8].bassDegree) {
                        bleedIntoOtherChord++
                    }
                }
            }

            // Intervals inside one rolled stack, in semitones above its lowest string. A semitone
            // or a tritone in here is an actual wrong note; anything else is just a chord.
            val stackIntervals = stacks.values.map { s ->
                val lo = s.minOf { it.midi }
                s.map { it.midi - lo }.sorted().distinct()
            }.distinct()
            val sourStacks = stackIntervals.count { iv ->
                iv.any { a -> iv.any { b -> Math.abs(a - b) % 12 == 1 || Math.abs(a - b) % 12 == 6 } }
            }

            // What the melody is doing while the stack rings — the other half of the harmony.
            val song = generateSong(spec, melodyRng(seed))
            var melodyClashes = 0
            var melodyOverStack = 0
            for (stack in stacks.values) {
                val from = stack.first().startBeat
                val to = stack.maxOf { it.startBeat + it.durBeats }
                val pcs = stack.map { Math.floorMod(it.midi, 12) }.toSet()
                for (n in song.melody.filter { it.startBeat >= from && it.startBeat < to }) {
                    melodyOverStack++
                    val pc = Math.floorMod(n.midi, 12)
                    if (pcs.any { Math.floorMod(pc - it, 12) == 1 || Math.floorMod(pc - it, 12) == 11 ||
                            Math.floorMod(pc - it, 12) == 6 }) melodyClashes++
                }
            }

            println(
                ("seed %2d %-12s %-10s %3dbpm %-12s | roll %.0fms/string spread %.0fms | " +
                    "bleed %.2f beats, %d/%d stacks bleed into a NEW chord | sour stacks %d/%d | " +
                    "melody clashes %d/%d").format(
                    seed, spec.family, spec.mode, spec.bpm, strumPlacementFor(spec),
                    gapMs, spreadMs, worstBleed, bleedIntoOtherChord, stacks.size,
                    sourStacks, stackIntervals.size, melodyClashes, melodyOverStack
                )
            )
        }
    }

    /** 16-bit interleaved-stereo WAV. compose() already returns L,R,L,R. */
    private fun writeStereoWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataBytes = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()); buf.putInt(36 + dataBytes); buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()); buf.putInt(16); buf.putShort(1); buf.putShort(2)
        buf.putInt(sampleRate); buf.putInt(sampleRate * 4); buf.putShort(4); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(dataBytes)
        for (s in pcm) buf.putShort(s)
        file.writeBytes(buf.array())
    }
}
