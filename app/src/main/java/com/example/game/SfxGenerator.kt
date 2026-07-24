package com.example.game

import kotlin.random.Random

enum class SoundType {
    CLANG,   // Metallic ping (helm knocks, weapon steals)
    THWACK,  // Blunt impact on wood/buildings
    SWOOSH,  // Weapon swing or flying projectile
    OUCH,    // Comedic pain screech
    DRUM_ROLL,  // War drum roll
    CRUNCH,  // Bone breaking / heavy smash
    VICTORY_FANFARE, // 1.5s victory sting
    // The three impact voices below prefer the player's own recordings when the matching assets
    // folder (armour/, shield/, flesh/) has files; the synth here is only the fallback.
    ARMOUR_HIT,   // Blow landing on mail/scale/plate
    SHIELD_BLOCK, // Blow caught on a shield
    FLESH         // Meaty thud on an unarmoured (or cloth/leather-clad) body
}

object SfxGenerator {
    fun generate(type: SoundType, sampleRate: Int, rng: Random): ShortArray {
        val pitchMul = Math.pow(2.0, (rng.nextDouble() * 4.0 - 2.0) / 12.0)  // +/-2 semitones
        val decayMul = 0.85 + rng.nextDouble() * 0.35
        return when (type) {
            SoundType.CLANG -> clang(sampleRate, rng, pitchMul)
            SoundType.THWACK -> thwack(sampleRate, rng, pitchMul, decayMul)
            SoundType.CRUNCH -> crunch(sampleRate, rng, pitchMul, decayMul)
            SoundType.SWOOSH -> swoosh(sampleRate, rng, decayMul)
            SoundType.OUCH -> ouch(sampleRate, rng, pitchMul)
            SoundType.DRUM_ROLL -> drumRoll(sampleRate, rng)
            SoundType.VICTORY_FANFARE -> victory(sampleRate, rng)
            // Fallbacks only — the assets folders override these when recordings exist
            SoundType.ARMOUR_HIT -> clang(sampleRate, rng, pitchMul * 0.7)  // duller ring than a helm ping
            SoundType.SHIELD_BLOCK -> clang(sampleRate, rng, pitchMul)
            SoundType.FLESH -> flesh(sampleRate, rng, pitchMul, decayMul)
        }
    }

    /**
     * Body "whump": a punch sinking into padding and muscle. What sells it is the shape, not the
     * pitch — a cushioned ~8ms attack (an instant attack reads as wood), noise through a lowpass
     * whose cutoff FALLS as the blow lands (the air being pushed out), and a soft sub swell.
     * No harmonic tone, no resonance, or it turns into a drum again.
     */
    fun flesh(sr: Int, rng: Random, pitchMul: Double, decayMul: Double): ShortArray {
        val dur = (0.16f * decayMul).toFloat(); val n = (sr * dur).toInt(); val out = ShortArray(n)
        val dt = 1.0 / sr
        var y1 = 0.0; var y2 = 0.0; var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val attack = if (t < 0.008) t / 0.008 else 1.0
            val env = attack * Math.exp(-26.0 * t / decayMul)
            // Time-varying one-pole pair: the whump sweeps from a soft smack down into pure weight
            val cutoff = (380.0 - 260.0 * (t / dur)) * pitchMul
            val a = 1.0 - Math.exp(-2.0 * Math.PI * cutoff * dt)
            val x = rng.nextDouble() * 2 - 1
            y1 += a * (x - y1); y2 += a * (y1 - y2)
            phase += 2 * Math.PI * 52.0 * pitchMul * dt
            val sub = Math.sin(phase) * 0.25 * Math.exp(-30.0 * t)
            val soft = Math.tanh((y2 * 3.4 + sub) * env * 1.5)
            out[i] = (soft * 26000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun clang(sr: Int, rng: Random, pitchMul: Double): ShortArray {
        val dur = 0.25f; val n = (sr * dur).toInt(); val out = ShortArray(n)
        val f = (700 + rng.nextDouble() * 400) * pitchMul
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = (1.0 - t / dur) * (1.0 - t / dur)
            val s = Math.sin(2 * Math.PI * f * t) + 0.6 * Math.sin(2 * Math.PI * f * 1.5 * t) + 0.4 * Math.sin(2 * Math.PI * f * 2.0 * t)
            val noise = (rng.nextDouble() * 2 - 1) * 0.15
            out[i] = ((s + noise) / 1.75 * env * 28000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun thwack(sr: Int, rng: Random, pitchMul: Double, decayMul: Double): ShortArray {
        val dur = (0.18f * decayMul).toFloat(); val n = (sr * dur).toInt(); val out = ShortArray(n)
        var phase = 0.0; var bodyPhase = 0.0; val dt = 1.0 / sr
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = 1.0 - t / dur
            val freq = (160.0 - 100.0 * (t / dur)) * pitchMul     // integrated: no reflected chirp
            phase += 2 * Math.PI * freq * dt
            bodyPhase += 2 * Math.PI * 520.0 * pitchMul * dt
            val s = Math.sin(phase) * 0.55
            val body = Math.sin(bodyPhase) * Math.exp(-18.0 * t) * 0.65
            val noise = (rng.nextDouble() * 2 - 1) * 0.1
            out[i] = ((s + body + noise) / 1.3 * env * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun crunch(sr: Int, rng: Random, pitchMul: Double, decayMul: Double): ShortArray {
        val dur = 0.25f; val n = (sr * dur).toInt(); val out = ShortArray(n)
        var phase = 0.0; val dt = 1.0 / sr
        val crackMid = Biquad.bandpass(sr, 750f, 1.1f)
        val crackHigh = Biquad.bandpass(sr, 1500f, 1.4f)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = Math.exp(-15.0 * t / decayMul)
            val freq = Math.max(34.0, 80.0 * pitchMul * Math.exp(-5.0 * t))  // integrated + subsonic floor
            phase += 2 * Math.PI * freq * dt
            val s = Math.sin(phase)
            var noise = (rng.nextDouble() * 2 - 1) * 0.6
            noise = 1.5 * noise - 0.5 * noise * noise * noise
            val snap = if (t < 0.05) (rng.nextDouble() * 2 - 1) * 0.4 else 0.0
            val presence = crackMid.process(noise.toFloat()) * 0.75 +
                crackHigh.process(noise.toFloat()) * 0.45
            // Soft saturation bounds the onset transient (raw peaks measured ~2x full scale);
            // the tanh drive keeps the aggressive bone-crunch character without hard clipping.
            val soft = Math.tanh((s * 0.5 + noise * 0.65 + snap + presence) * env * 1.2)
            out[i] = (soft * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun swoosh(sr: Int, rng: Random, decayMul: Double): ShortArray {
        val dur = (0.2 * decayMul).toFloat(); val n = (sr * dur).toInt(); val out = ShortArray(n)
        var last = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = Math.sin(Math.PI * (t / dur))
            val cur = 0.2 * (rng.nextDouble() * 2 - 1) + 0.8 * last
            last = cur
            out[i] = (cur * env * 18000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun ouch(sr: Int, rng: Random, pitchMul: Double): ShortArray {
        val dur = 0.3f; val n = (sr * dur).toInt(); val out = ShortArray(n)
        var phase = 0.0; val dt = 1.0 / sr
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = Math.sin(Math.PI * (t / dur)) * (1.0 - t / dur)
            val f = (150.0 - 60.0 * (t / dur)) * pitchMul
            phase += f * dt                                      // integrated square phase
            val wave = if (phase % 1.0 < 0.5) 1.0 else -1.0
            val noise = (rng.nextDouble() * 2 - 1) * 0.4
            val formant = Math.sin(2 * Math.PI * 600 * t) * 0.5
            out[i] = ((wave + noise + formant) * env * 18000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun drumRoll(sr: Int, rng: Random): ShortArray {
        val dur = 2.0f; val n = (sr * dur).toInt(); val out = ShortArray(n)
        val root = ProceduralMedievalComposer.currentRootMidi - 12f // Low timpani root
        val fLow = ProceduralMedievalComposer.midiToFreq(root).toDouble()
        val fHigh = ProceduralMedievalComposer.midiToFreq(root + 7f).toDouble() // Perfect fifth!
        val hitsPerSec = 2.0 // Slow boom bam boom
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val hitIndex = (t * hitsPerSec).toInt()
            val hitT = (t * hitsPerSec) % 1.0
            val f = if (hitIndex % 2 == 0) fLow else fHigh
            
            val env = if (hitT < 0.05) hitT / 0.05 else Math.exp(-3.0 * (hitT - 0.05))
            val currentF = f * (1.0 + 0.05 * Math.exp(-15.0 * hitT))
            val wave = Math.sin(2 * Math.PI * currentF * hitT) + 
                       0.5 * Math.sin(4 * Math.PI * currentF * hitT) * Math.exp(-6.0 * hitT) + 
                       0.25 * Math.sin(6 * Math.PI * currentF * hitT) * Math.exp(-12.0 * hitT)
            val noise = (rng.nextDouble() * 2 - 1) * Math.exp(-30.0 * hitT) * 0.15
            val phoneBodyHz = (f * 5.0).coerceIn(320.0, 520.0)
            val phoneBody = Math.sin(2 * Math.PI * phoneBodyHz * hitT) * Math.exp(-8.0 * hitT) * 0.7
            val skin = (rng.nextDouble() * 2 - 1) * Math.exp(-40.0 * hitT) * 0.25
            
            // Beefy war drum / noble timpani
            out[i] = ((wave * 0.55 + phoneBody + noise + skin) * env * 20000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun victory(sr: Int, rng: Random): ShortArray {
        val dur = 1.5f; val n = (sr * dur).toInt(); val out = ShortArray(n)
        val root = ProceduralMedievalComposer.currentRootMidi + 12f
        val third = ProceduralMedievalComposer.currentThirdOffset
        val notes = floatArrayOf(0f, third, 7f, 12f).map { ProceduralMedievalComposer.midiToFreq(root + it).toDouble() }
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val noteDur = dur / 4.0
            val idx = (t / noteDur).toInt().coerceIn(0, 3)
            val f = notes[idx]
            val localT = t % noteDur
            val hornEnv = when {
                localT < 0.04 -> localT / 0.04
                localT > noteDur - 0.08 -> (noteDur - localT) / 0.08
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            var horn = 0.0
            for (h in 1..5) horn += (1.0 / h) * Math.sin(2 * Math.PI * h * f * t)
            val harpEnv = if (localT < 0.003) localT / 0.003 else Math.exp(-2.5 * (localT - 0.003))
            val harp = Math.sin(2 * Math.PI * f * t) + 0.45 * Math.sin(4 * Math.PI * f * t) * Math.exp(-3.0 * t)
            val harpNoise = if (localT < 0.015) (rng.nextDouble() * 2 - 1) * 0.18 * (1 - localT / 0.015) else 0.0
            out[i] = ((horn * hornEnv * 5000.0) + ((harp + harpNoise) * harpEnv * 7000.0)).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
