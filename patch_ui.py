import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Replace the scrollable Column in LevelUpScreen with a LazyVerticalGrid
old_grid = '''            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.pendingLevelUpChoices.forEach { choice ->
                    // Choose colors and tags based on upgrade types
                    val (bannerColor, titleColor, tagLabel) = when (choice.type) {
                        "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "Entourage")
                        "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "Weapon Head")
                        "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Haft Upgrade")
                        "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "Layered Armor")
                        else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onSelectChoice(choice.id) },
                        colors = CardDefaults.cardColors(containerColor = bannerColor),
                        border = BorderStroke(1.5.dp, titleColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
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
                                )
                            }
                            
                            // Beautiful right arrow for M3 interaction
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(titleColor, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚔", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }'''

new_grid = '''            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
            ) {
                items(uiState.pendingLevelUpChoices.size) { index ->
                    val choice = uiState.pendingLevelUpChoices[index]
                    val (bannerColor, titleColor, tagLabel) = when (choice.type) {
                        "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "Entourage")
                        "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "Weapon Head")
                        "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Haft Upgrade")
                        "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "Layered Armor")
                        else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // MAKE IT SQUARE!
                            .clickable { onSelectChoice(choice.id) },
                        colors = CardDefaults.cardColors(containerColor = bannerColor),
                        border = BorderStroke(1.5.dp, titleColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                tagLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(3.dp))
                                    .border(0.5.dp, titleColor, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            
                            Text(
                                choice.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TapestryDark,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            Text(
                                choice.description,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = TapestryDark.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(titleColor, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }'''

code = code.replace(old_grid, new_grid)

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity patched for squares!")
