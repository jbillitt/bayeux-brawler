import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Fix LevelUpScreen Grid -> Row
old_grid = '''            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
            ) {
                items(uiState.pendingLevelUpChoices.size) { index ->'''
new_grid = '''            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in uiState.pendingLevelUpChoices.indices) {'''
code = code.replace(old_grid, new_grid)

# Adjust the Card modifier in the LevelUpScreen Row to use weight
old_card_mod = '''                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // MAKE IT SQUARE!
                            .clickable { onSelectChoice(choice.id) },'''
new_card_mod = '''                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f) // MAKE IT SQUARE!
                            .clickable { onSelectChoice(choice.id) },'''
code = code.replace(old_card_mod, new_card_mod)

# Don't forget to close the for-loop correctly instead of the items block.
# Actually, replacing the items() block end might be tricky with simple replace.
# Let's do a regex for the end of the items block.
old_items_end = '''                        }
                    }
                }
            }
        }
    }
}'''
new_items_end = '''                        }
                    }
                }
            }
        }
    }
}'''
# Oh wait, replacing just the top part of the block will leave an extra closing brace because items() takes a lambda.
# Let's do a more precise replacement for LevelUpScreen
level_up_pattern = r'androidx\.compose\.foundation\.lazy\.grid\.LazyVerticalGrid\(.*?\{.*?items\(uiState\.pendingLevelUpChoices\.size\) \{ index ->(.*?)\}\s*\}\s*\}\s*\}\s*\}'
def level_up_repl(m):
    inner = m.group(1)
    inner = inner.replace('modifier = Modifier\n                            .fillMaxWidth()', 'modifier = Modifier\n                            .weight(1f)')
    return '''Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in uiState.pendingLevelUpChoices.indices) {''' + inner + '''}
            }
        }
    }
}'''

code = re.sub(level_up_pattern, level_up_repl, code, flags=re.DOTALL)

# 2. Fix WinLossScreen portrait Box size and translation, and buttons
old_winloss = '''                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(120.dp).background(Color(0xFFE5D3B3), RoundedCornerShape(4.dp)).border(2.dp, TapestryDark, RoundedCornerShape(4.dp)).clipToBounds()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sc = size.width / 40f
                                    withTransform({
                                        scale(sc, sc, pivot = Offset.Zero)
                                        translate(15f, -65f)
                                    }) {
                                        if (player != null) {
                                            // Draw just head without helmet
                                            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }, posX = 0f, animFrame = 0f, isDead = false, isDying = false)
                                            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Name: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Weapon: ", fontSize = 10.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                val anc = uiState.unlockedAncillaries.mapNotNull { id -> com.example.game.GameData.ANCILLARIES.find { it.id == id }?.name }.joinToString(", ")
                                Text("Ancillaries: ", fontSize = 10.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Kills: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWin) TapestryGreen else TapestryRed
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("dismiss_result_btn")
                    ) {
                        Text(
                            text = if (isWin) "To the Armory! (Proceed)" else "Mend thy wounds & Try Again",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = TapestryLight
                        )
                    }'''

new_winloss = '''                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(70.dp).background(Color(0xFFE5D3B3), RoundedCornerShape(4.dp)).border(2.dp, TapestryDark, RoundedCornerShape(4.dp)).clipToBounds()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sc = size.width / 80f
                                    withTransform({
                                        scale(sc, sc, pivot = Offset.Zero)
                                        translate(40f, -140f) // shift up to where cy=200 is
                                    }) {
                                        if (player != null) {
                                            // Draw just head without helmet
                                            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }, posX = 0f, animFrame = 0f, isDead = false, isDying = false)
                                            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Name: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Weapon: ", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                val anc = uiState.unlockedAncillaries.mapNotNull { id -> com.example.game.GameData.ANCILLARIES.find { it.id == id }?.name }.joinToString(", ")
                                Text("Ancillaries: ", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Kills: ", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
                            }
                        }
                    }
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWin) TapestryGreen else TapestryRed
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("dismiss_result_btn")
                        ) {
                            Text(
                                text = if (isWin) "To the Armory!" else "Try Again",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = TapestryLight
                            )
                        }
                        
                        Button(
                            onClick = {
                                val uri = generateShareImage(context, player, uiState.scoreMultiplier, uiState.totalKills, isWin)
                                if (uri != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Tale"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TapestryBlue),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Share Tale", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryLight)
                        }
                    }'''

if old_winloss in code:
    code = code.replace(old_winloss, new_winloss)
else:
    print("WARNING: Could not find old_winloss block to replace.")

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity UI patched!")
