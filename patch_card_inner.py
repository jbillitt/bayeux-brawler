import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    tagLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(3.dp))
                                        .border(0.5.dp, titleColor, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
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
                                )
                            }"""

replacement = """                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    tagLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(3.dp))
                                        .border(0.5.dp, titleColor, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
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
                                )
                            }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Replaced inner Card successfully")
else:
    print("Inner Card Target not found.")
