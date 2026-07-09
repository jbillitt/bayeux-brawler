import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """        // Distinct, grand level up callout
        Text(
            "VICTORY & ASCENSION!",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TapestryRed,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Select thy spoils of war to forge thy legendary saga:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TapestryDark.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 20.dp)
        )"""

replacement = """        // Distinct, grand level up callout
        Text(
            "Victory!",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TapestryRed,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Select thy spoils of war:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TapestryDark.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 12.dp)
        )"""

if target in content:
    content = content.replace(target, replacement)
    
target2 = """                    // Choose colors and tags based on upgrade types
                    val (bannerColor, titleColor, tagLabel) = when (choice.type) {
                        "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "ENTOURAGE")
                        "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "WEAPON HEAD")
                        "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "HAFT UPGRADE")
                        "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "LAYERED ARMOR")
                        else -> Triple(Color(0xFFF5F5F5), TapestryDark, "UPGRADE")
                    }"""

replacement2 = """                    // Choose colors and tags based on upgrade types
                    val (bannerColor, titleColor, tagLabel) = when (choice.type) {
                        "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "Entourage")
                        "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "Weapon Head")
                        "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Haft Upgrade")
                        "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "Layered Armor")
                        else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
                    }"""

if target2 in content:
    content = content.replace(target2, replacement2)

target3 = """                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    choice.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    choice.description,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = TapestryDark.copy(alpha = 0.8f)
                                )"""

replacement3 = """                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
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

if target3 in content:
    content = content.replace(target3, replacement3)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Replaced successfully")
