import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                                Text(
                                    choice.title.replace("🛡️", "").replace("⚔️", "").replace("🪖", "").replace("🧍", "").trim(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    choice.description,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark.copy(alpha = 0.8f)
                                )"""

replacement = """                                Text(
                                    choice.title.replace("🛡️", "").replace("⚔️", "").replace("🪖", "").replace("🧍", "").trim(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    choice.description,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark.copy(alpha = 0.8f)
                                )"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Replaced!")
else:
    print("Target not found")
