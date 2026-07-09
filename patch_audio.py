import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Tame the flute vibrato
old_recorder = '''            if (layerVolume[4] > 0f) {
                // Recorder: simple sine wave with vibrato for counter-melody
                if (frameIndex % (samplesPerBeat * 2) < samplesPerBeat) {
                    val env = 1f - (frameIndex % samplesPerBeat) / samplesPerBeat.toFloat()
                    val f = (freq * 1.5).toFloat() // higher octave for counter melody
                    val vibrato = 1.0 + 0.01 * Math.sin(2.0 * Math.PI * 5.0 * t)
                    mix += Math.sin(2.0 * Math.PI * f * vibrato * t).toFloat() * 0.15f * env * layerVolume[4]
                }
            }'''

new_recorder = '''            if (layerVolume[4] > 0f) {
                // Recorder: simple sine wave with vibrato for counter-melody
                if (frameIndex % (samplesPerBeat * 2) < samplesPerBeat) {
                    val env = 1f - (frameIndex % samplesPerBeat) / samplesPerBeat.toFloat()
                    val f = (freq * 1.5).toFloat() // higher octave for counter melody
                    val vibrato = 1.0 + 0.003 * Math.sin(2.0 * Math.PI * 4.0 * t)
                    mix += Math.sin(2.0 * Math.PI * f * vibrato * t).toFloat() * 0.15f * env * layerVolume[4]
                }
            }'''
code = code.replace(old_recorder, new_recorder)


# 2. Improve Horn Organum fidelity
old_horn = '''            if (layerVolume[6] > 0f) {
                val env = 1f - (frameIndex % (samplesPerBeat * 8)) / (samplesPerBeat * 8).toFloat()
                // Sawtooth approx for horn
                val f1 = (freq * 0.5f).toFloat() // octave below
                var hornWave = 0f
                for (h in 1..4) {
                    hornWave += (Math.sin(2.0 * Math.PI * f1 * h * t) / h).toFloat()
                }
                mix += hornWave * 0.1f * env * layerVolume[6]
            }'''
new_horn = '''            if (layerVolume[6] > 0f) {
                val env = 1f - (frameIndex % (samplesPerBeat * 8)) / (samplesPerBeat * 8).toFloat()
                // Sawtooth approx for horn with slight detune
                val f1 = (freq * 0.5f).toFloat() // octave below
                var hornWave = 0f
                for (h in 1..6) {
                    hornWave += (Math.sin(2.0 * Math.PI * f1 * h * t) / h).toFloat()
                    hornWave += (Math.sin(2.0 * Math.PI * (f1 + 1.5) * h * t) / h).toFloat() // chorus effect
                }
                mix += hornWave * 0.06f * env * layerVolume[6]
            }'''
code = code.replace(old_horn, new_horn)

# 3. Master Reverb/EQ polish
old_mix = '''            val delaySample = delayBuffer[delayWriteIndex]
            mix = mix * 0.7f + delaySample * 0.3f
            
            // Low pass filter
            mix = mix * 0.4f + lastSample * 0.6f
            lastSample = mix
            
            delayBuffer[delayWriteIndex] = mix
            delayWriteIndex = (delayWriteIndex + 1) % delayLen'''
new_mix = '''            // Add high-quality stereo-ish reverb (delay)
            val delaySample = delayBuffer[delayWriteIndex]
            mix = mix * 0.75f + delaySample * 0.25f
            
            // Low pass filter (less muddy)
            mix = mix * 0.6f + lastSample * 0.4f
            lastSample = mix
            
            // Anti-aliasing clip
            mix = Math.max(-1.0f, Math.min(1.0f, mix))
            
            delayBuffer[delayWriteIndex] = mix * 0.4f // lower feedback
            delayWriteIndex = (delayWriteIndex + 1) % delayLen'''
code = code.replace(old_mix, new_mix)

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("Audio Synth updated!")
