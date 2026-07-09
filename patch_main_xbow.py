import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Make ballista bolts shorter but still thick
old_arrow = '''                    } else if (proj.type == "arrow") {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val strokeW = if (proj.isBallista) 12f else 7f
                        val length = if (proj.isBallista) 110f else 65f'''
new_arrow = '''                    } else if (proj.type == "arrow") {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val strokeW = if (proj.isBallista) 10f else 7f
                        val length = if (proj.isBallista) 45f else 65f'''
code = code.replace(old_arrow, new_arrow)

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched for crossbow sizes!")
