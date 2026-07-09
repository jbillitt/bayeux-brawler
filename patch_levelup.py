import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()
    
target = """    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Distinct, grand level up callout
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
        )
        
        if (uiState.pendingLevelUpChoices.isEmpty()) {
            Text(
                "Preparations Complete! Rally thy men and step forth onto the field of honor.",
                fontSize = 15.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = TapestryDark,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = { onStartBattle() },
                colors = ButtonDefaults.buttonColors(containerColor = TapestryRed),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Proceed to Next Battle", color = Color.White, fontWeight = FontWeight.Bold)
            }
                
        } else {
            Column(
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
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Select",
                                    tint = titleColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }"""
    
replacement = """    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Select thy spoils of war:",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TapestryDark,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (uiState.pendingLevelUpChoices.isEmpty()) {
            Text(
                "Preparations Complete! Rally thy men and step forth onto the field of honor.",
                fontSize = 15.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = TapestryDark,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = { onStartBattle() },
                colors = ButtonDefaults.buttonColors(containerColor = TapestryRed),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Proceed to Next Battle", color = Color.White, fontWeight = FontWeight.Bold)
            }
                
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.pendingLevelUpChoices.forEach { choice ->
                    val (bannerColor, titleColor, tagLabel) = when (choice.type) {
                        "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "Entourage")
                        "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "Weapon Head")
                        "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Haft Upgrade")
                        "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "Layered Armor")
                        else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
                    }
                    
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectChoice(choice.id) },
                        colors = CardDefaults.cardColors(containerColor = bannerColor),
                        border = BorderStroke(1.5.dp, titleColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                choice.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TapestryDark,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                choice.description,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = TapestryDark.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
