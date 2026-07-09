import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Reduce master reverb and delay amount
old_mix = '''            // Add high-quality stereo-ish reverb (delay)
            val delaySample = delayBuffer[delayWriteIndex]
            mix = mix * 0.75f + delaySample * 0.25f
            
            // Low pass filter (less muddy)
            mix = mix * 0.6f + lastSample * 0.4f
            lastSample = mix
            
            // Anti-aliasing clip
            mix = Math.max(-1.0f, Math.min(1.0f, mix))
            
            delayBuffer[delayWriteIndex] = mix * 0.4f // lower feedback
            delayWriteIndex = (delayWriteIndex + 1) % delayLen'''
new_mix = '''            // Add tight stereo-ish reverb (delay)
            val delaySample = delayBuffer[delayWriteIndex]
            mix = mix * 0.85f + delaySample * 0.15f // reduced delay mix
            
            // Low pass filter (less muddy)
            mix = mix * 0.6f + lastSample * 0.4f
            lastSample = mix
            
            // Anti-aliasing clip
            mix = Math.max(-1.0f, Math.min(1.0f, mix))
            
            delayBuffer[delayWriteIndex] = mix * 0.25f // reduced delay feedback
            delayWriteIndex = (delayWriteIndex + 1) % delayLen'''
code = code.replace(old_mix, new_mix)

# 2. Fix the harp discordance (enforce Dorian/Aeolian scale instead of wild Markov generation)
# Wait, let's see how the melody is generated. Let's just find the generateMusic or generatePhrase function.
