import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

old_card = '''                    Card(
                        modifier = Modifier
                            .weight(1f)
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
                    }'''

new_card = '''                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectChoice(choice.id) },
                        colors = CardDefaults.cardColors(containerColor = bannerColor),
                        border = BorderStroke(2.dp, titleColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                tagLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, titleColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            
                            Text(
                                choice.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TapestryDark,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                choice.description,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = TapestryDark.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(titleColor, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚔", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }'''
code = code.replace(old_card, new_card)

with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("Reward boxes updated!")
