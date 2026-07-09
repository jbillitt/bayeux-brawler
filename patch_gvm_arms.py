import re

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Fix player losing arms
old_arm = '''                    if (Random.nextFloat() < 0.15f && target.armor.id == "armor_none") {
                        target.missingArm = true
                    }'''
new_arm = '''                    if (Random.nextFloat() < 0.15f && target.armor.id == "armor_none" && !target.isPlayer) {
                        target.missingArm = true
                    }'''
code = code.replace(old_arm, new_arm)

with open('app/src/main/java/com/example/game/GameViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("GameViewModel patched for missing arms!")
