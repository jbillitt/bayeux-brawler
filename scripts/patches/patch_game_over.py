import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

chaucer_quotes = """val chaucerQuotes = listOf(
    "\\"Time and tide wait for no man.\\"\\n- Geoffrey Chaucer",
    "\\"All good things must come to an end.\\"\\n- Geoffrey Chaucer",
    "\\"The greatest scholars are not usually the wisest people.\\"\\n- Geoffrey Chaucer",
    "\\"Patience is a conquering virtue.\\"\\n- Geoffrey Chaucer",
    "\\"Nothing ventured, nothing gained.\\"\\n- Geoffrey Chaucer"
)"""

target = """                    Text(
                        text = if (isWin) {
                            "Thy valiant Norman Knight hath vanquished Harold's Anglo-Saxon defenders! Thy gear score multiplier of x%.1f earned thee massive points.".format(uiState.scoreMultiplier)
                        } else {
                            "Thy knight hath collapsed in battle! The Saxon defenders stand victorious. Strip thy gear further to gain points, or steel thyself with heavier armor."
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        color = TapestryDark,
                        textAlign = TextAlign.Center
                    )"""

replacement = """                    
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
                            "\\"Time and tide wait for no man.\\"\\n- Geoffrey Chaucer",
                            "\\"All good things must come to an end.\\"\\n- Geoffrey Chaucer",
                            "\\"The greatest scholars are not usually the wisest people.\\"\\n- Geoffrey Chaucer",
                            "\\"Patience is a conquering virtue.\\"\\n- Geoffrey Chaucer",
                            "\\"Nothing ventured, nothing gained.\\"\\n- Geoffrey Chaucer"
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
                            Box(modifier = Modifier.size(60.dp).background(Color(0xFFE5D3B3), RoundedCornerShape(4.dp)).border(2.dp, TapestryDark, RoundedCornerShape(4.dp))) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sc = size.width / 40f
                                    withTransform({
                                        scale(sc, sc, pivot = Offset.Zero)
                                        translate(20f, 35f)
                                    }) {
                                        if (player != null) {
                                            // Draw just head without helmet
                                            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" })
                                            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Name: ${uiState.playerName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Weapon: ${player?.weaponHead?.name ?: "None"}", fontSize = 10.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                val anc = uiState.unlockedAncillaries.mapNotNull { id -> com.example.game.GameData.ANCILLARIES.find { it.id == id }?.name }.joinToString(", ")
                                Text("Ancillaries: ${if (anc.isEmpty()) "None" else anc}", fontSize = 10.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Kills: ${uiState.totalKills}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
                            }
                        }
                    }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

