import re

# 1. Fix SIZE_PRESETS in MainActivity.kt
with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

old_sizes = '''listOf(0.8f to "Slight", 1.0f to "Average", 1.25f to "Hulking").forEach { (sizeVal, label) ->'''
new_sizes = '''com.example.game.SIZE_PRESETS.forEach { preset ->
                                val sizeVal = preset.size
                                val label = preset.label'''

code = code.replace(old_sizes, new_sizes)


# 2. Fix Win/Loss banner to fit landscape and include Share button
old_banner = '''                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isWin) "VICTORIA GLORIOSUS!" else "MORTIS ET DEFEAT!",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isWin) TapestryGreen else TapestryRed
                    )

                    
                    if (isWin) {
                        Text(
                            text = "Thy valiant Norman Knight hath vanquished Harold's Anglo-Saxon defenders! Thy gear score multiplier of x%.1f earned thee massive points.".format(uiState.scoreMultiplier),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            color = TapestryDark,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val player = viewModel.playerState.collectAsState().value
                        val quote = remember { listOf(
                            "\"Time and tide wait for no man.\"\n- Geoffrey Chaucer",
                            "\"All good things must come to an end.\"\n- Geoffrey Chaucer",
                            "\"The greatest scholars are not usually the wisest people.\"\n- Geoffrey Chaucer",
                            "\"Patience is a conquering virtue.\"\n- Geoffrey Chaucer",
                            "\"Nothing ventured, nothing gained.\"\n- Geoffrey Chaucer"
                        ).random() }
                        
                        Text(
                            text = quote,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFF6B4423),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        Row(
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
                    }
                }'''

new_banner = '''                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isWin) "VICTORIA GLORIOSUS!" else "MORTIS ET DEFEAT!",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isWin) TapestryGreen else TapestryRed
                    )

                    if (isWin) {
                        Text(
                            text = "Thy valiant Norman Knight hath vanquished Harold's Anglo-Saxon defenders! Thy gear score multiplier of x%.1f earned thee massive points.".format(uiState.scoreMultiplier),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            color = TapestryDark,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val player = viewModel.playerState.collectAsState().value
                        val quote = remember { listOf(
                            "\"Time and tide wait for no man.\"\n- Geoffrey Chaucer",
                            "\"All good things must come to an end.\"\n- Geoffrey Chaucer",
                            "\"The greatest scholars are not usually the wisest people.\"\n- Geoffrey Chaucer",
                            "\"Patience is a conquering virtue.\"\n- Geoffrey Chaucer",
                            "\"Nothing ventured, nothing gained.\"\n- Geoffrey Chaucer"
                        ).random() }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(90.dp).background(Color(0xFFE5D3B3), RoundedCornerShape(4.dp)).border(2.dp, TapestryDark, RoundedCornerShape(4.dp)).clipToBounds()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val targetHeadY = 130f
                                    val sc = size.width / 50f
                                    withTransform({
                                        scale(sc, sc, pivot = Offset.Zero)
                                        translate(25f, -(targetHeadY - 25f))
                                    }) {
                                        if (player != null) {
                                            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.firstOrNull { it.id == "helm_none" } ?: com.example.game.GameData.HEADGEAR_PIECES.first(), posX = 0f, animFrame = 0f, isDead = false, isDying = false)
                                            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Name: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Weapon: ", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                val wDesc = player?.weaponHead?.description ?: ""
                                Text(wDesc, fontSize = 7.sp, fontFamily = FontFamily.Serif, color = TapestryDark, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                val anc = uiState.unlockedAncillaries.mapNotNull { id -> com.example.game.GameData.ANCILLARIES.find { it.id == id }?.name }.joinToString(", ")
                                Text("Ancillaries: ", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Kills: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
                                Text("Bayeux Brawler", fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = TapestryDark, modifier = Modifier.padding(top=2.dp))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top=2.dp)) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWin) TapestryGreen else TapestryRed
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                text = if (isWin) "Proceed" else "Try Again",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = TapestryLight,
                                fontSize = 12.sp
                            )
                        }
                        if (!isWin) {
                            Button(
                                onClick = { /* Share intent not implemented yet */ },
                                colors = ButtonDefaults.buttonColors(containerColor = TapestryBlue),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(
                                    text = "Share Result",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = TapestryLight,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }'''

code = code.replace(old_banner, new_banner)

# Let's ensure the card itself fits nicely
code = code.replace('modifier = Modifier\n                    .width(360.dp)', 'modifier = Modifier\n                    .width(420.dp)')

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched!")
