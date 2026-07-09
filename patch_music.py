import re

with open("app/src/main/java/com/example/game/SoundSynth.kt", "r") as f:
    content = f.read()

target = """    private val DORIAN_INTERVALS    = intArrayOf(0, 2, 3, 5, 7, 9, 10, 12, 14, 15, 17, 19, 21, 22, 24)
    private val MIXOLYDIAN_INTERVALS= intArrayOf(0, 2, 4, 5, 7, 9, 10, 12, 14, 16, 17, 19, 21, 22, 24)"""

replacement = """    private val DORIAN_INTERVALS    = intArrayOf(0, 2, 3, 5, 7, 9, 10, 12, 14, 15, 17, 19, 21, 22, 24)
    private val MIXOLYDIAN_INTERVALS= intArrayOf(0, 2, 4, 5, 7, 9, 10, 12, 14, 16, 17, 19, 21, 22, 24)
    private val AEOLIAN_INTERVALS   = intArrayOf(0, 2, 3, 5, 7, 8, 10, 12, 14, 15, 17, 19, 20, 22, 24)
    private val IONIAN_INTERVALS    = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23, 24)"""

content = content.replace(target, replacement)

target2 = """        // Choose mode: low levels prefer Dorian (darker); high Mixolydian can creep in
        val useDorian = level < 4 || rng.nextFloat() < 0.6f
        val (modeRoot, modeIntervals) = if (useDorian)
            Pair(50, DORIAN_INTERVALS)    // D3 Dorian
        else
            Pair(55, MIXOLYDIAN_INTERVALS) // G3 Mixolydian"""
            
replacement2 = """        // Choose mode: low levels prefer Dorian (darker); high Mixolydian can creep in
        val modeType = rng.nextInt(4)
        val (modeRoot, modeIntervals) = when (modeType) {
            0 -> Pair(50, DORIAN_INTERVALS)    // D3 Dorian
            1 -> Pair(55, MIXOLYDIAN_INTERVALS) // G3 Mixolydian
            2 -> Pair(57, AEOLIAN_INTERVALS)    // A3 Aeolian
            else -> Pair(48, IONIAN_INTERVALS)   // C3 Ionian
        }"""
        
content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/game/SoundSynth.kt", "w") as f:
    f.write(content)

