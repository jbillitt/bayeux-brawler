import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Remove it from above tabs
target1 = """        // Dual Wield Toggle (above tabs)
        if (uiState.shield.id == "shield_none") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { onToggleDualWield() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = uiState.isDualWielding,
                    onCheckedChange = { onToggleDualWield() },
                    modifier = Modifier.scale(0.8f)
                )
                Text("Dual Wield", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
            }
        }"""
        
# 2. Add it above LazyVerticalGrid
target2 = """                val itemsToShow = when (selectedTab) {"""
replacement2 = """                if (selectedTab == 0 && uiState.shield.id == "shield_none") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .background(TapestryLinenCard, RoundedCornerShape(4.dp))
                            .border(1.dp, TapestryDark, RoundedCornerShape(4.dp))
                            .clickable { onToggleDualWield() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = uiState.isDualWielding,
                            onCheckedChange = { onToggleDualWield() },
                            modifier = Modifier.scale(0.8f)
                        )
                        Text("Dual Wield (Copies main weapon to off-hand)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                    }
                }
                
                val itemsToShow = when (selectedTab) {"""

if target1 in content and target2 in content:
    content = content.replace(target1, "")
    content = content.replace(target2, replacement2)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found.")
