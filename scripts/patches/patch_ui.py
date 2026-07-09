import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Fix COMMENCE YE FIGHT padding
content = content.replace("contentPadding = PaddingValues(0.dp)", "contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)")

# 2. Update HeaderBar
target_header = """        Column {
            Text(
                text = "YE HASTINGS MELEE",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = TapestryDark,
                modifier = Modifier.drawBehind {
                    // Stitched underline
                    val y = size.height + 4f
                    drawLine(TapestryDark, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                }
            )
            Text(
                text = "Level ${uiState.level}: The shoreline scuffle",
                fontSize = 10.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }"""
        
replacement_header = """        Column {
            if (uiState.showLevelUpScreen || uiState.pendingLevelUpChoices.isNotEmpty()) {
                Text(
                    text = "VICTORY!",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TapestryRed,
                    modifier = Modifier.drawBehind {
                        val y = size.height + 4f
                        drawLine(TapestryDark, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                    }
                )
            } else {
                Text(
                    text = "LEVEL ${uiState.level}",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TapestryDark,
                    modifier = Modifier.drawBehind {
                        val y = size.height + 4f
                        drawLine(TapestryDark, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                    }
                )
            }
            Text(
                text = "The shoreline scuffle",
                fontSize = 10.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }"""
        
content = content.replace(target_header, replacement_header)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

