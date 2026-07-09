import re

with open("app/src/main/java/com/example/game/GameViewModel.kt", "r") as f:
    content = f.read()

target1 = """                    // Still take minimal blunt impact damage
                    val blockDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                    applyFlatDamage(blockDamage, currTarget)
                } else {
                    // Full hit!"""

replacement1 = """                    // Still take minimal blunt impact damage
                    val blockDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                    if (blockDamage > 5f && kotlin.random.Random.nextBoolean()) MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    applyFlatDamage(blockDamage, currTarget)
                } else {
                    // Full hit!"""

if target1 in content:
    content = content.replace(target1, replacement1)

target2 = """                        MedievalAudioSynth.playSound(SoundType.THWACK)
                        val strikeType = if (pierce > slash && pierce > blunt) "pierce" else if (slash > blunt) "slash" else "blunt"
                        val hitShout = LatinShouts.getRandomShout(SoundType.THWACK, strikeType)"""

replacement2 = """                        val isCrunch = blunt > 15f && kotlin.random.Random.nextFloat() < 0.4f
                        MedievalAudioSynth.playSound(if (isCrunch) SoundType.CRUNCH else SoundType.THWACK)
                        val strikeType = if (pierce > slash && pierce > blunt) "pierce" else if (slash > blunt) "slash" else "blunt"
                        val hitShout = LatinShouts.getRandomShout(if (isCrunch) SoundType.CRUNCH else SoundType.THWACK, strikeType)"""

if target2 in content:
    content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/GameViewModel.kt", "w") as f:
    f.write(content)
print("Replaced sounds!")
