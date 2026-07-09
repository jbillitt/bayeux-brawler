import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                                Text(
                                    choice.title.replace("🛡️", "").replace("⚔️", "").replace("🪖", "").replace("🧍", "").trim(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    choice.description,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = TapestryDark.copy(alpha = 0.85f)
                                )"""

replacement = """                                Text(
                                    choice.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    choice.description,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    color = TapestryDark.copy(alpha = 0.85f)
                                )"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")

