import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(".androidx.compose.ui.draw.clipToBounds()", ".clipToBounds()")

content = content.replace("androidx.compose.ui.graphics.drawscope.withTransform({", "withTransform({")

# Add import if missing
if "import androidx.compose.ui.draw.clipToBounds" not in content:
    content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clipToBounds")

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed!")
