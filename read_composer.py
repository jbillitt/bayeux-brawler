import re

with open('app/src/main/java/com/example/game/SoundSynth.kt', 'r', encoding='utf-8') as f:
    code = f.read()

print(code[code.find("object ProceduralMedievalComposer {"):code.find("object MedievalVocalizer {")])
