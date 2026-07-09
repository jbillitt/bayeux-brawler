import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Increase javelin size
old_jav = '''                    } else if (proj.type == "javelin") {
                        // Draw huge spear
                        val shaftColor = Color(0xFF6E5536) // Darker wood
                        val strokeW = 6.5f
                        val length = 60f'''
new_jav = '''                    } else if (proj.type == "javelin") {
                        // Draw huge spear
                        val shaftColor = Color(0xFF6E5536) // Darker wood
                        val strokeW = 9f
                        val length = 110f'''
code = code.replace(old_jav, new_jav)

# Increase arrow & ballista size
old_arrow = '''                    } else if (proj.type == "arrow") {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val strokeW = if (proj.isBallista) 8f else 5f
                        val length = if (proj.isBallista) 55f else 38f'''
new_arrow = '''                    } else if (proj.type == "arrow") {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val strokeW = if (proj.isBallista) 12f else 7f
                        val length = if (proj.isBallista) 110f else 65f'''
code = code.replace(old_arrow, new_arrow)

# Increase feather sizes
old_feather = '''                        // Arrow feather fletching (Embroidered texture)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy + 2f), Offset(sx - (length * arrowDir), sy + 10f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy + 2f), Offset(sx - (length * arrowDir), sy - 6f), strokeWidth = 3.5f, cap = StrokeCap.Round)'''
new_feather = '''                        // Arrow feather fletching (Embroidered texture)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy + 2f), Offset(sx - (length * arrowDir), sy + 14f), strokeWidth = 5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy - 2f), Offset(sx - (length * arrowDir), sy - 14f), strokeWidth = 5f, cap = StrokeCap.Round)'''
code = code.replace(old_feather, new_feather)

# Increase arrowhead tip
old_tip = '''                        // Arrow Iron Tip
                        val tipRadius = if (proj.isBallista) 7f else 4f'''
new_tip = '''                        // Arrow Iron Tip
                        val tipRadius = if (proj.isBallista) 11f else 6f'''
code = code.replace(old_tip, new_tip)

# Javelin Tip
old_jav_tip = '''                        // Giant Iron Spear Tip
                        val hPath = Path().apply {
                            moveTo(sx - (4f * arrowDir), sy)
                            lineTo(sx + (18f * arrowDir), sy - 5f)
                            lineTo(sx + (24f * arrowDir), sy)
                            lineTo(sx + (18f * arrowDir), sy + 5f)
                            close()
                        }'''
new_jav_tip = '''                        // Giant Iron Spear Tip
                        val hPath = Path().apply {
                            moveTo(sx - (6f * arrowDir), sy)
                            lineTo(sx + (25f * arrowDir), sy - 8f)
                            lineTo(sx + (35f * arrowDir), sy)
                            lineTo(sx + (25f * arrowDir), sy + 8f)
                            close()
                        }'''
code = code.replace(old_jav_tip, new_jav_tip)


with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched for projectile size!")
