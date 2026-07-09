import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

code = code.replace('player.weaponAttachments', 'player.extraAttachments')

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched for extraAttachments!")
