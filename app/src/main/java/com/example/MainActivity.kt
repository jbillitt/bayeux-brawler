package com.example

import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.drawscope.withTransform

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.*
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the free, offline, local vocalization engine
        com.example.game.MedievalVocalizer.init(applicationContext)
        
        // Let's set the activity orientation request to user's sensor to encourage landscape,
        // but handle layout adaptation gracefully in Compose!
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BayeuxAppContent(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        com.example.game.MedievalVocalizer.shutdown()
        super.onDestroy()
    }
}

@Composable
fun BayeuxAppContent(viewModel: GameViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var forceBypass by remember { mutableStateOf(false) }

    // Medieval Harp Background Music State
    var musicOn by remember { mutableStateOf(true) }
    val uiState by viewModel.uiState.collectAsState()
    val hasTrumpeter = uiState.unlockedAncillaries.contains("anc_trumpeter")
    val appliedMusicMoods = uiState.appliedMusicMoods

    LaunchedEffect(musicOn, uiState.level, hasTrumpeter, appliedMusicMoods) {
        if (musicOn) {
            MedievalHarpPlayer.startMusic(level = uiState.level, hasTrumpeter = hasTrumpeter, moods = appliedMusicMoods)
        } else {
            MedievalHarpPlayer.stopMusic()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MedievalHarpPlayer.stopMusic()
        }
    }

    if (!isLandscape && !forceBypass) {
        // Render a gorgeous medieval "Rotate Thy Scroll" overlay if in portrait mode
        PortraitRotationGuide(onBypass = { forceBypass = true })
    } else {
        // Render the main Bayeux Tapestry game
        MainBayeuxGameScreen(viewModel, musicOn = musicOn, onToggleMusic = { musicOn = !musicOn })
    }
}

@Composable
fun PortraitRotationGuide(onBypass: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenBg)
            .border(8.dp, TapestryDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            // Animated phone icon rotation
            val infiniteTransition = rememberInfiniteTransition(label = "rotate_phone")
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 90f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation"
            )

            // Dynamic Canvas drawing of a medieval shield rotating
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .drawBehind {
                        // Drawing decorative shield
                        drawCircle(TapestryRed, radius = 45f)
                        drawCircle(TapestryDark, radius = 45f, style = Stroke(width = 4f))
                        drawLine(TapestryMustard, Offset(50f, 5f), Offset(50f, 95f), strokeWidth = 8f)
                        drawLine(TapestryMustard, Offset(5f, 50f), Offset(95f, 50f), strokeWidth = 8f)
                    }
            ) {}

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "HIC MULTIPLICATIO LANDSCAPUS!",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = TapestryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "For proper, magnificent panoramic Bayeux Tapestry battles, thy smartphone must be rotated horizontally into Landscape.",
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = TapestryDark.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBypass,
                colors = ButtonDefaults.buttonColors(containerColor = TapestryRed),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("bypass_rotation_btn")
            ) {
                Text(
                    text = "Bypass & Play in Portrait",
                    color = TapestryLight,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MainBayeuxGameScreen(viewModel: GameViewModel, musicOn: Boolean, onToggleMusic: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val playerFighter by viewModel.playerState.collectAsState()
    val enemies by viewModel.enemiesState.collectAsState()
    val projectiles by viewModel.projectilesState.collectAsState()
    val popups by viewModel.popupsState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenBg)
            .border(8.dp, TapestryDark)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. HUD / HEADER BAR
            HeaderBar(uiState = uiState, musicOn = musicOn, onToggleMusic = onToggleMusic, playerState = playerFighter)

            Spacer(modifier = Modifier.height(4.dp))

            // 2. MAIN WORKSPACE (Active Battle or Gear Selection)
            if (uiState.isBattleActive) {
                BattlefieldScene(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { viewModel.dismissBattleResult() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: Character Preview
                    Box(
                        modifier = Modifier
                            .weight(0.24f)
                            .fillMaxHeight()
                    ) {
                        CharacterPreviewCard(uiState = uiState)
                    }

                    // Middle: Tabbed Component Lists or Level Up Screen
                    Box(
                        modifier = Modifier
                            .weight(0.53f)
                            .fillMaxHeight()
                    ) {
                        if (uiState.showMusicDecision) {
                            MusicDecisionScreen(
                                options = uiState.pendingMusicOptions,
                                onSelect = { viewModel.selectMusicMood(it) }
                            )
                        } else if (uiState.showLevelUpScreen || uiState.level > 1) {
                            LevelUpScreen(
                                uiState = uiState,
                                onSelectChoice = { viewModel.selectLevelUpChoice(it) },
                                onStartBattle = { viewModel.startBattle() },
                                onSkipReward = { viewModel.selectLevelUpChoice("") },
                                onClearSkipBonus = { viewModel.clearSkipBonus() }
                            )
                        } else {
                            GearSelectionTabs(
                                uiState = uiState, 
                                onSelect = { viewModel.selectGear(it) },
                                onUpdatePhysical = { size, hairColor, hairStyle -> viewModel.updatePhysical(size, hairColor, hairStyle) },
                                onToggleDualWield = { viewModel.toggleDualWield() }
                            )
                        }
                    }

                    // Right: Live Stats, Score Multipliers & CTA Buttons
                    Box(
                        modifier = Modifier
                            .weight(0.23f)
                            .fillMaxHeight()
                    ) {
                        StatsAndLaunchPanel(
                            uiState = uiState,
                            onCommence = { viewModel.startBattle() },
                            onRandomize = { viewModel.randomizeGear() }
                        )
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun HeaderBar(uiState: BattleSimState, musicOn: Boolean, onToggleMusic: () -> Unit, playerState: FighterState? = null) {
    var showStatsPopup by remember { mutableStateOf(false) }

    if (showStatsPopup) {
        val p = playerState
        Dialog(onDismissRequest = { showStatsPopup = false }) {
            Card(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, TapestryDark),
                colors = CardDefaults.cardColors(containerColor = TapestryLinenCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "KNIGHT STATISTICS",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = TapestryRed,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (p != null) {
                        StatText("HP", "${p.hp.toInt()} / ${p.maxHp.toInt()}")
                        StatText("DPS", "${"%.1f".format(p.baseDamage / p.attackSpeedDelay)}/s")
                        StatText("Armor", "${p.totalArmor.toInt()}")
                        StatText("Move Speed", "${p.moveSpeed.toInt()} px/s")
                        StatText("Reach", "${"%.1f".format(p.reach)} m")
                        StatText("Score Mult", "×${"%.1f".format(p.scoreMultiplier)}")
                    } else {
                        Text("No active battle data.", fontSize = 11.sp, color = TapestryDark.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showStatsPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = TapestryRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = TapestryLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TapestryLinenCard)
            .border(2.dp, TapestryDark, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Stats popup button (replaced level text)
                Box(
                    modifier = Modifier
                        .background(TapestryDark.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, TapestryDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable { showStatsPopup = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.showLevelUpScreen || uiState.pendingLevelUpChoices.isNotEmpty()) "⚔ STATS" else "⚔ STATS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (uiState.showLevelUpScreen || uiState.pendingLevelUpChoices.isNotEmpty()) TapestryRed else TapestryDark
                    )
                }
            }
            Text(
                text = "The shoreline scuffle",
                fontSize = 10.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SCORE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TapestryDark.copy(alpha = 0.6f)
                )
                Text(
                    text = "${uiState.score}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = TapestryRed
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "HIGHSCORE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TapestryDark.copy(alpha = 0.6f)
                )
                Text(
                    text = "${uiState.highscore}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TapestryDark
                )
            }

            // Medieval Harp Music Toggle
            Box(
                modifier = Modifier
                    .background(if (musicOn) TapestryGreen else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    .border(1.dp, TapestryDark, RoundedCornerShape(2.dp))
                    .clickable { onToggleMusic() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_harp_music_btn")
            ) {
                Text(
                    text = if (musicOn) "🔊 MUSIC" else "🔇 MUTE",
                    color = TapestryLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .background(TapestryRed, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "LVL %02d".format(uiState.level),
                    color = TapestryLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CharacterPreviewCard(uiState: BattleSimState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = uiState.playerName.uppercase(),
            fontSize = 9.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            color = TapestryDark.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Setup custom breeding idle animation for the preview
        val infiniteTransition = rememberInfiniteTransition(label = "preview_breathe")
        val animFrame by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "walk"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFFAF6EB))
                .border(1.dp, TapestryDark)
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background texture sketch
                drawRect(TapestryLinenBg.copy(alpha = 0.4f))
                
                // Majestic Aura Frame (Soft glowing vignette instead of swirling lines)
                val centerX = size.width / 2
                val centerY = size.height / 2
                
                // Pulsing Holy Aura Glow
                val pulse = (sin(animFrame * 5f) + 1f) / 2f // 0 to 1
                
                // Bossy Dark Vignette around edges
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF2C2219).copy(alpha = 0.2f),
                            Color(0xFF2C2219).copy(alpha = 0.7f)
                        ),
                        center = Offset(centerX, centerY),
                        radius = size.width * 0.7f
                    )
                )

                // Intense Holy Glow from behind
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            TapestryMustard.copy(alpha = 0.6f + 0.2f * pulse),
                            TapestryMustard.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY - 20f),
                        radius = 280f + 30f * pulse
                    )
                )
                
                // Divine Sparks rising aggressively
                for (i in 0 until 18) {
                    val floatY = (centerY + 160f) - ((animFrame * 50f + i * 35f) % 350f)
                    val floatX = centerX + sin(animFrame * 3f + i) * 110f
                    val r = 2f + (i % 4)
                    drawCircle(Color(0xFFFFF1AA).copy(alpha = 0.9f), radius = r, center = Offset(floatX, floatY))
                    // Add spark trail
                    drawLine(Color(0xFFFFF1AA).copy(alpha = 0.4f), Offset(floatX, floatY + r), Offset(floatX, floatY + r + 15f), strokeWidth = 1.5f)
                }

                // Draw decorative grass tuft under knight
                val gp = Path().apply {
                    moveTo(centerX - 60f, centerY + 110f)
                    quadraticTo(centerX, centerY + 100f, centerX + 60f, centerY + 110f)
                }
                drawPath(gp, TapestryGreen, style = Stroke(width = 4f))

                // Create dummy FighterState mirroring chosen gear
                val dummyFighter = FighterState(
                    id = "preview",
                    name = uiState.playerName,
                    isPlayer = true,
                    maxHp = 100f,
                    hp = 100f,
                    weaponHead = uiState.weaponHead,
                    weaponHandle = uiState.weaponHandle,
                    shield = uiState.shield,
                    armor = uiState.armor,
                    headgear = uiState.headgear,
                    isDualWielding = uiState.isDualWielding,
                    posX = centerX,
                    targetX = centerX,
                    animFrame = animFrame,
                    facingRight = true,
                    size = uiState.characterSize,
                    hairColor = uiState.hairColor,
                    hairStyle = uiState.hairStyle,
                    isMounted = uiState.unlockedAncillaries.contains("anc_mount_horse")
                )

                // Render at massive scale (Fancam style!)
                // Center it slightly lower so the head doesn't clip
                translate(top = -40f) {
                    TapestryRenderer.drawCharacter(this, dummyFighter, scale = 1.35f)
                }
            }
        }
    }
}

@Composable
fun LevelUpScreen(
    uiState: BattleSimState,
    onSelectChoice: (String) -> Unit,
    onStartBattle: () -> Unit = {},
    onSkipReward: () -> Unit = {},
    onClearSkipBonus: () -> Unit = {}
) {
    // Skip bonus popup
    var showSkipBonusPopup by remember { mutableStateOf(false) }
    var skipBonusAmount by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.pendingSkipBonus) {
        if (uiState.pendingSkipBonus > 0) {
            skipBonusAmount = uiState.pendingSkipBonus
            showSkipBonusPopup = true
            kotlinx.coroutines.delay(2000)
            showSkipBonusPopup = false
            onClearSkipBonus()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(3.dp, TapestryDark, RoundedCornerShape(12.dp))
            .padding(24.dp),
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
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in uiState.pendingLevelUpChoices.indices) {
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
                                .width(220.dp)
                                .fillMaxHeight()
                                .clickable { onSelectChoice(choice.id) },
                            colors = CardDefaults.cardColors(containerColor = bannerColor),
                            border = BorderStroke(2.dp, titleColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    color = TapestryDark.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Visible
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
                        }
                    }

                    // No-Reward card
                    Card(
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight()
                            .clickable { onSkipReward() },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        border = BorderStroke(2.dp, TapestryDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No Reward",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TapestryDark.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, TapestryDark.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                "Decline all spoils",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TapestryDark,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Thou art too proud for gifts. Earn bonus glory instead.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = TapestryDark.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Visible
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✗", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Skip bonus popup overlay
                if (showSkipBonusPopup) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(TapestryRed, RoundedCornerShape(8.dp))
                            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "+$skipBonusAmount GLORY!",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TapestryLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatText(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TapestryDark.copy(alpha = 0.6f))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
    }
}

@Composable
fun GearSelectionTabs(
    uiState: BattleSimState, 
    onSelect: (GearItem) -> Unit, 
    onUpdatePhysical: (Float, Color, String) -> Unit,
    onToggleDualWield: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Weapon ⚔️", "Shield 🛡️", "Armor 🛡️", "Helm 🪖", "Physical 🧍")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {

        // Tab Headers
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = { },
            indicator = { }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val active = selectedTab == index
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 2.dp)
                        .background(
                            if (active) TapestryDark else Color(0xFFDCD2B8),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                        .clickable {
                            selectedTab = index
                            MedievalAudioSynth.playSound(SoundType.SWOOSH)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (active) TapestryLight else TapestryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tab Content Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0x33FFFFFF))
                .border(1.dp, TapestryDark)
                .padding(4.dp)
        ) {
            if (selectedTab == 4) {
                // Physical Appearance Settings
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Size Selection
                    Column {
                        Text("BODY SIZE (Affects Mass/Speed)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.example.game.SIZE_PRESETS.forEach { preset ->
                                val sizeVal = preset.size
                                val label = preset.label
                                val isSelected = uiState.characterSize == sizeVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSelected) TapestryDark else Color(0xFFFAF6EB), RoundedCornerShape(4.dp))
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) TapestryMustard else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable { onUpdatePhysical(sizeVal, uiState.hairColor, uiState.hairStyle) }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (isSelected) TapestryLight else TapestryDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Hair Color Selection
                    Column {
                        Text("HAIR COLOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val colors = listOf(Color(0xFFE5C09F), Color(0xFFC08030), Color(0xFF5A442E), Color(0xFF2C2219))
                            colors.forEach { colorVal ->
                                val isSelected = uiState.hairColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(colorVal, CircleShape)
                                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) TapestryRed else TapestryDark, CircleShape)
                                        .clickable { onUpdatePhysical(uiState.characterSize, colorVal, uiState.hairStyle) }
                                )
                            }
                        }
                    }

                    // Hair Style Selection
                    Column {
                        Text("HAIR STYLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("short" to "Bowl Cut", "long" to "Long Locks", "bald" to "Bald/Fringe").forEach { (styleVal, label) ->
                                val isSelected = uiState.hairStyle == styleVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSelected) TapestryDark else Color(0xFFFAF6EB), RoundedCornerShape(4.dp))
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) TapestryMustard else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable { onUpdatePhysical(uiState.characterSize, uiState.hairColor, styleVal) }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (isSelected) TapestryLight else TapestryDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                if (selectedTab == 0 && uiState.shield.id == "shield_none") {
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
                
                val itemsToShow = when (selectedTab) {
                    0 -> GameData.WEAPON_HEADS.filter { it.id in uiState.unlockedGearIds }
                    1 -> GameData.SHIELDS.filter { it.id in uiState.unlockedGearIds }
                    2 -> GameData.ARMOR_PIECES.filter { it.id in uiState.unlockedGearIds }
                    else -> GameData.HEADGEAR_PIECES.filter { it.id in uiState.unlockedGearIds }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(itemsToShow) { item ->
                        val isSelected = when (item.type) {
                            ItemType.WEAPON_HEAD -> uiState.weaponHead.id == item.id
                            ItemType.WEAPON_HANDLE -> false // We'll render Handle choices under weapon heads
                            ItemType.SHIELD -> uiState.shield.id == item.id
                            ItemType.ARMOR -> uiState.armor.id == item.id
                            ItemType.HEADGEAR -> uiState.headgear.id == item.id
                        }

                        GearItemCell(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                onSelect(item)
                                // Play themed sound effect
                                val sound = when (item.type) {
                                    ItemType.SHIELD -> SoundType.CLANG
                                    else -> SoundType.SWOOSH
                                }
                                MedievalAudioSynth.playSound(sound)
                            }
                        )
                    }
                }
            }
        }

        // Handle attachment selection (Only visible on Weapon Tab)
        if (selectedTab == 0 && uiState.weaponHead.id != "head_bare") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "WEAPON HAFT / HANDLE ATTACHMENT",
                fontSize = 8.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GameData.WEAPON_HANDLES.filter { it.id != "handle_fists" && it.id in uiState.unlockedGearIds }.forEach { handle ->
                    val isHandleSelected = uiState.weaponHandle.id == handle.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isHandleSelected) TapestryDark else Color(0xFFFAF6EB),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isHandleSelected) 2.dp else 1.dp,
                                color = if (isHandleSelected) TapestryMustard else TapestryDark.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onSelect(handle)
                                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = handle.name,
                            color = if (isHandleSelected) TapestryLight else TapestryDark,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GearItemCell(item: GearItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) TapestryLinenBg else Color(0xFFFAF6EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) TapestryRed else TapestryDark,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .testTag("gear_${item.id}"),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.name.uppercase(),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = TapestryDark,
                    modifier = Modifier.weight(1f)
                )

                // Simple weight badge
                Text(
                    text = "${item.mass}kg",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TapestryDark.copy(alpha = 0.6f)
                )
            }

            Text(
                text = item.description,
                fontSize = 7.5.sp,
                lineHeight = 9.sp,
                fontFamily = FontFamily.Serif,
                color = TapestryDark.copy(alpha = 0.8f),
                maxLines = 3,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatsAndLaunchPanel(
    uiState: BattleSimState,
    onCommence: () -> Unit,
    onRandomize: () -> Unit
) {
    // We compute live stats by spinning up a dummy player FighterState
    val dummyFighter = remember(uiState) {
        FighterState(
            id = "dummy",
            name = "Player",
            isPlayer = true,
            maxHp = 100f,
            hp = 100f,
            weaponHead = uiState.weaponHead,
            weaponHandle = uiState.weaponHandle,
            shield = uiState.shield,
            armor = uiState.armor,
            headgear = uiState.headgear,
            isDualWielding = uiState.isDualWielding,
            posX = 0f, targetX = 0f,
            isMounted = uiState.unlockedAncillaries.contains("anc_mount_horse")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Base Stats Overview
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "KNIGHT BASE STATS",
                fontSize = 9.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.6f)
            )

            // Weight Meter
            StatProgressBar(
                label = "Total Weight",
                valueText = "%.1f kg".format(dummyFighter.totalMass),
                fraction = (dummyFighter.totalMass / 30f).coerceIn(0f, 1f),
                color = TapestryDark
            )

            // Armor Meter
            StatProgressBar(
                label = "Armor Protection",
                valueText = "${dummyFighter.totalArmor.toInt()}%",
                fraction = dummyFighter.totalArmor / 100f,
                color = TapestryBlue
            )

            // Attack Speed Meter
            val speedScore = (2.0f - dummyFighter.attackSpeedDelay).coerceIn(0.1f, 1.5f)
            StatProgressBar(
                label = "Attack Speed",
                valueText = "%.2fx".format(1f / dummyFighter.attackSpeedDelay),
                fraction = speedScore / 1.5f,
                color = TapestryGreen
            )

            // Weapon Range/Reach
            StatProgressBar(
                label = "Weapon Reach",
                valueText = "%.1fm".format(dummyFighter.reach),
                fraction = (dummyFighter.reach / 4f).coerceIn(0f, 1f),
                color = TapestryMustard
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Score Multiplier Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x33FFFFFF))
                .border(1.dp, TapestryDark, RoundedCornerShape(4.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SCORE MULTIPLIER",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TapestryDark.copy(alpha = 0.6f)
                )
                Text(
                    text = "x%.1f".format(dummyFighter.scoreMultiplier),
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = TapestryRed
                )
                Text(
                    text = if (dummyFighter.armor.id == "armor_bare") "MAX EXPOSURE! Hilarity ensues!" else "Lighter gear yields greater points.",
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Serif,
                    color = TapestryDark.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Launch CTAs
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCommence,
                colors = ButtonDefaults.buttonColors(containerColor = TapestryRed),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, TapestryDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("commence_fight_btn"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "COMMENCE YE FIGHT!",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = TapestryLight
                )
            }
        }
    }
}

@Composable
fun StatProgressBar(label: String, valueText: String, fraction: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label.uppercase(), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
            Text(text = valueText, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TapestryDark)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x22382F22), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Panoramic Tapestry battlefield renderer and game HUD.
 */
@Composable
fun BattlefieldScene(
    viewModel: GameViewModel,
    uiState: BattleSimState,
    onDismiss: () -> Unit
) {
    val tick by viewModel.gameTick.collectAsState()
    val shake by viewModel.screenshake.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(TapestryLinenBg)
            .border(2.dp, TapestryDark, RoundedCornerShape(6.dp))
    ) {
        // Render Tapestry Canvas
        Canvas(modifier = Modifier.fillMaxSize().testTag("bayeux_tapestry_canvas")) {
            // Force redraw on tick
            val currentTick = tick
            val playerFighter = viewModel.playerState.value
            val enemies = viewModel.enemiesState.value
            val projectiles = viewModel.projectilesState.value
            val popups = viewModel.popupsState.value
            // Draw coarse woven canvas linen backdrop
            drawRect(TapestryLinenBg)

            // Dynamic linen weave textured shading
            var yOffset = 0f
            while (yOffset < size.height) {
                drawLine(
                    color = Color(0x0C382F22),
                    start = Offset(0f, yOffset),
                    end = Offset(size.width, yOffset),
                    strokeWidth = 2f
                )
                yOffset += 4f
            }

            // 1. Draw TOP Embroidered Border (characteristic of Bayeux)
            drawTapestryBorder(this, isTop = true, textHeadline = "HIC MELEE COMEDICUS IN TERRA HASTINGS")

            // 2. Draw BOTTOM Embroidered Border (With decorative stags and some funny bones of fallen foes!)
            drawTapestryBorder(this, isTop = false, textHeadline = "")

            val shakeAmt = shake
            val offsetX = if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f
            val offsetY = if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f

            withTransform({
                translate(left = offsetX, top = offsetY)
            }) {
                // 3. Draw Players and Enemies
                if (playerFighter != null) {
                // Adjust position scaling to match standard physical resolution of drawing Canvas
                val playerScaleX = size.width / 1000f
                val scaleFactor = size.height / 350f

                // Draw Knight
                val scaledPlayer = playerFighter.copy(
                    posX = playerFighter.posX * playerScaleX
                )
                TapestryRenderer.drawCharacter(this, scaledPlayer, scale = scaleFactor)

                // Draw ridiculous unlocked Squires, Trumpeters, Heralds, Cupbearers
                TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, scaledPlayer, scale = scaleFactor)

                // Draw health bar for Player
                val px = scaledPlayer.posX
                val py = 60f
                drawHealthBar(this, px, py, playerFighter.hp, playerFighter.ghostHp, playerFighter.maxHp)

                // Draw Enemies
                enemies.forEach { enemy ->
                    val scaledEnemy = enemy.copy(
                        posX = enemy.posX * playerScaleX
                    )
                    TapestryRenderer.drawCharacter(this, scaledEnemy, scale = scaleFactor)
                    
                    // Draw health bar for enemy
                    if (!enemy.isDead) {
                        drawHealthBar(this, scaledEnemy.posX, py, enemy.hp, enemy.ghostHp, enemy.maxHp)
                    }
                }

                // 4. Draw Projectiles (Bows / Slingshots)
                projectiles.forEach { proj ->
                    val sx = proj.posX * playerScaleX
                    val sy = 200f + (proj.posY - 200f) * scaleFactor
                    val arrowDir = if (proj.velocityX > 0) 1f else -1f

                    if (proj.launchedWeaponId != null) {
                        // Drawing miniature launched weapon head as the projectile!
                        val weaponHeadId = proj.launchedWeaponId
                        if (weaponHeadId == "head_axe") {
                            val hPath = Path().apply {
                                moveTo(sx - (10f * arrowDir), sy)
                                lineTo(sx + (10f * arrowDir), sy - 15f)
                                lineTo(sx + (12f * arrowDir), sy - 3f)
                                lineTo(sx + (4f * arrowDir), sy + 8f)
                                close()
                            }
                            drawPath(hPath, Color(0xFF7A868C))
                            drawPath(hPath, TapestryDark, style = Stroke(width = 1.5f))
                            // Small handle shaft
                            drawLine(Color(0xFF8C6F47), Offset(sx - (12f * arrowDir), sy + 5f), Offset(sx + (2f * arrowDir), sy - 2f), strokeWidth = 2.5f)
                        } else if (weaponHeadId == "head_morningstar") {
                            drawCircle(Color(0xFF535C61), radius = 8f * proj.sizeMultiplier, center = Offset(sx, sy))
                            drawCircle(TapestryDark, radius = 8f * proj.sizeMultiplier, center = Offset(sx, sy), style = Stroke(width = 1.5f))
                            for (i in 0 until 4) {
                                val a = i * (Math.PI / 2)
                                drawLine(TapestryDark, Offset(sx, sy), Offset(sx + cos(a).toFloat() * 12f * proj.sizeMultiplier, sy + sin(a).toFloat() * 12f * proj.sizeMultiplier), strokeWidth = 2f)
                            }
                        } else if (weaponHeadId == "head_sword" || weaponHeadId == "head_claymore") {
                            drawLine(TapestryDark, Offset(sx - (14f * arrowDir), sy), Offset(sx + (14f * arrowDir), sy), strokeWidth = 5f)
                            drawLine(Color(0xFFA6B0B5), Offset(sx - (12f * arrowDir), sy), Offset(sx + (12f * arrowDir), sy), strokeWidth = 3f)
                        } else {
                            // general spear/pike head
                            val hPath = Path().apply {
                                moveTo(sx - (12f * arrowDir), sy)
                                lineTo(sx + (10f * arrowDir), sy - 6f)
                                lineTo(sx + (18f * arrowDir), sy)
                                lineTo(sx + (10f * arrowDir), sy + 6f)
                                close()
                            }
                            drawPath(hPath, Color(0xFF8C969E))
                            drawPath(hPath, TapestryDark, style = Stroke(width = 1.5f))
                        }
                    } else if (proj.type == "javelin") {
                        // Draw huge spear
                        val shaftColor = Color(0xFF6E5536) // Darker wood
                        val strokeW = 9f
                        val length = 110f
                        
                        // Shaft
                        drawLine(
                            color = shaftColor,
                            start = Offset(sx, sy),
                            end = Offset(sx - (length * arrowDir), sy + 3f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        // Giant Iron Spear Tip
                        val hPath = Path().apply {
                            moveTo(sx - (6f * arrowDir), sy)
                            lineTo(sx + (25f * arrowDir), sy - 8f)
                            lineTo(sx + (35f * arrowDir), sy)
                            lineTo(sx + (25f * arrowDir), sy + 8f)
                            close()
                        }
                        drawPath(hPath, Color(0xFF8C969E))
                        drawPath(hPath, TapestryDark, style = Stroke(width = 1.5f))
                        
                        // Some leather bindings
                        drawLine(TapestryDark, Offset(sx - (4f * arrowDir), sy - 3f), Offset(sx - (4f * arrowDir), sy + 3f), strokeWidth = 3f)
                        drawLine(TapestryDark, Offset(sx - (8f * arrowDir), sy - 3f), Offset(sx - (8f * arrowDir), sy + 3f), strokeWidth = 3f)
                    } else if (proj.type == "arrow") {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val strokeW = if (proj.isBallista) 10f else 7f
                        val length = if (proj.isBallista) 45f else 65f
                        
                        // Arrow Shaft
                        drawLine(
                            color = shaftColor,
                            start = Offset(sx, sy),
                            end = Offset(sx - (length * arrowDir), sy + (if(proj.isBallista) 0f else 3f)),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        // Arrow Iron Tip
                        val tipRadius = if (proj.isBallista) 11f else 6f
                        drawCircle(Color(0xFF868C91), radius = tipRadius, center = Offset(sx, sy))
                        
                        // Spiked Broadhead extra barbs
                        if (proj.hasSpikes) {
                            drawLine(TapestryDark, Offset(sx, sy), Offset(sx - (10f * arrowDir), sy + 8f), strokeWidth = 3f)
                            drawLine(TapestryDark, Offset(sx, sy), Offset(sx - (10f * arrowDir), sy - 8f), strokeWidth = 3f)
                        }

                        // Arrow feather fletching (Embroidered texture)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy + 2f), Offset(sx - (length * arrowDir), sy + 14f), strokeWidth = 5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy - 2f), Offset(sx - (length * arrowDir), sy - 14f), strokeWidth = 5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.8f * arrowDir), sy + 2f), Offset(sx - (length * 1.1f * arrowDir), sy + 9f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.8f * arrowDir), sy + 2f), Offset(sx - (length * 1.1f * arrowDir), sy - 5f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                        
                    } else {
                        // Sling stone circle (woven rock)
                        val r = 4.5f * proj.sizeMultiplier
                        val stoneColor = if (proj.isPoisonous) Color(0xFF2E7D32) else Color(0xFF535C61)
                        val outlineColor = if (proj.isPoisonous) Color(0xFF81C784) else TapestryDark
                        
                        drawCircle(stoneColor, radius = r, center = Offset(sx, sy))
                        drawCircle(outlineColor, radius = r, center = Offset(sx, sy), style = Stroke(width = 2.0f))
                        drawLine(Color.DarkGray, Offset(sx - r * 0.5f, sy - r * 0.5f), Offset(sx + r * 0.5f, sy + r * 0.5f), strokeWidth = 1.5f)

                        // Spikes around stone
                        if (proj.hasSpikes) {
                            for (i in 0 until 4) {
                                val angle = i * (Math.PI / 2)
                                val sxStart = sx + cos(angle).toFloat() * r
                                val syStart = sy + sin(angle).toFloat() * r
                                val sxEnd = sx + cos(angle).toFloat() * (r + 4f)
                                val syEnd = sy + sin(angle).toFloat() * (r + 4f)
                                drawLine(TapestryDark, Offset(sxStart, syStart), Offset(sxEnd, syEnd), strokeWidth = 1.5f)
                            }
                        }
                    }
                }

                // 5. Draw Blood Particles
                viewModel.particlesState.value.forEach { part ->
                    val px = part.x * playerScaleX
                    val py = 200f + (part.y - 200f) * scaleFactor
                    
                    if (part.y >= 240f) {
                        val poolWidth = 5f + (part.age / part.maxAge) * 15f
                        val poolHeight = 2f + (part.age / part.maxAge) * 5f
                        drawOval(
                            color = Color(0xFF9E3624).copy(alpha = (1f - (part.age / part.maxAge) * 0.5f).coerceIn(0f, 1f)),
                            topLeft = Offset(px - poolWidth / 2, py - poolHeight / 2),
                            size = Size(poolWidth, poolHeight)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF9E3624).copy(alpha = (1f - (part.age / part.maxAge)).coerceIn(0f, 1f)),
                            radius = 2.5f + (Math.random() * 2f).toFloat(),
                            center = Offset(px, py)
                        )
                    }
                }

                // 6. Draw Floating Comic popups (e.g. *CLANGUS*, *THWACKUS*)
                popups.forEach { pop ->
                    val sx = pop.x * playerScaleX
                    val sy = pop.y * scaleFactor - (pop.age * 30f) // float upwards
                    drawComicTextBubble(this, pop.text, sx, sy, pop.color, pop.age)
                }
            }
        }
    }

        // Animated visibilities for Win/Loss banners
        AnimatedVisibility(
            visible = uiState.battleWon || uiState.battleLost,
            modifier = Modifier.align(Alignment.Center)
        ) {
            val isWin = uiState.battleWon
            Card(
                colors = CardDefaults.cardColors(containerColor = TapestryLinenCard),
                modifier = Modifier
                    .width(420.dp)
                    .border(4.dp, if (isWin) TapestryGreen else TapestryRed, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
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

                    
                    val player = viewModel.playerState.collectAsState().value
                    if (isWin) {
                        Text(
                            text = "Thy valiant Norman Knight hath vanquished Harold's Anglo-Saxon defenders! Thy gear score multiplier of x%.1f earned thee massive points.".format(uiState.scoreMultiplier),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            color = TapestryDark,
                            textAlign = TextAlign.Center
                        )
                    } else {
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
                                Text("Name: ${uiState.playerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Weapon: ${player?.weaponHead?.name ?: "None"}", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                val anc = uiState.unlockedAncillaries.mapNotNull { id -> com.example.game.GameData.ANCILLARIES.find { it.id == id }?.name }.joinToString(", ")
                                Text("Ancillaries: ${if (anc.isEmpty()) "None" else anc}", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                Text("Kills: ${uiState.totalKills}", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Share Tale", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryLight)
                        }
                    }
                }
            }
        }
    }
}

private fun drawStatusEffects(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, fighter: com.example.game.FighterState) {
    var offsetX = x - 10f
    val iconRadius = 4f
    
    if (fighter.poisonDuration > 0f) {
        // Draw poison symbol (green circle with P?) We can just draw a little green bubble
        scope.drawCircle(color = Color(0xFF2E7D32), radius = iconRadius, center = Offset(offsetX, y))
        offsetX += 12f
    }
    if (fighter.bleedDuration > 0f) {
        // Draw bleed symbol (red droplet)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(offsetX, y - iconRadius)
            lineTo(offsetX + iconRadius, y + iconRadius * 0.5f)
            arcTo(androidx.compose.ui.geometry.Rect(offsetX - iconRadius, y - iconRadius * 0.5f, offsetX + iconRadius, y + iconRadius * 1.5f), 0f, 180f, false)
            close()
        }
        scope.drawPath(path, color = Color(0xFFA62B2B))
        offsetX += 12f
    }
}

private fun drawHealthBar(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, hp: Float, ghostHp: Float, maxHp: Float) {
    val barWidth = 65f
    val barHeight = 8f
    val startX = x - (barWidth / 2)

    // Red stitch background
    scope.drawRect(
        color = TapestryRed,
        topLeft = Offset(startX, y),
        size = Size(barWidth, barHeight)
    )

    // Yellow ghost health
    val ghostFrac = (ghostHp / maxHp).coerceIn(0f, 1f)
    scope.drawRect(
        color = Color(0xFFD4B144),
        topLeft = Offset(startX, y),
        size = Size(barWidth * ghostFrac, barHeight)
    )

    // Green stitch active
    val activeFrac = (hp / maxHp).coerceIn(0f, 1f)
    scope.drawRect(
        color = TapestryGreen,
        topLeft = Offset(startX, y),
        size = Size(barWidth * activeFrac, barHeight)
    )

    // Stitched frame outline
    scope.drawRect(
        color = TapestryDark,
        topLeft = Offset(startX, y),
        size = Size(barWidth, barHeight),
        style = Stroke(width = 1.5f)
    )
}

private fun drawComicTextBubble(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    age: Float
) {
    // Fade out
    val alpha = (1f - (age / 1.2f)).coerceIn(0f, 1f)
    
    // Draw raw medieval stitched text!
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 28f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        setColor(color.copy(alpha = alpha).value.toInt())
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Shadow outline for thread thickness look
    val outlinePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 28f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        setColor(TapestryDark.copy(alpha = alpha).value.toInt())
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    scope.drawContext.canvas.nativeCanvas.drawText(text, x, y, outlinePaint)
    scope.drawContext.canvas.nativeCanvas.drawText(text, x, y, textPaint)
}

private fun drawTapestryBorder(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    isTop: Boolean,
    textHeadline: String
) {
    val h = scope.size.height
    val w = scope.size.width
    val borderH = 40f
    
    val yTop = if (isTop) 0f else h - borderH
    val dividerY = if (isTop) borderH else h - borderH

    // 1. Solid woven background bar
    scope.drawRect(
        color = TapestryLinenCard,
        topLeft = Offset(0f, yTop),
        size = Size(w, borderH)
    )

    // 2. Thick black stitching divider line
    scope.drawLine(
        color = TapestryDark,
        start = Offset(0f, dividerY),
        end = Offset(w, dividerY),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    if (isTop) {
        // Draw the Latin Text Headline inside the top border
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            color = TapestryDark.value.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        scope.drawContext.canvas.nativeCanvas.drawText(textHeadline, w / 2, 28f, paint)
    }

    // 3. Draw a few simple stylized beasts/vines in the margins
    // We can sketch a couple of diagonal staves or birds
    var x = 30f
    while (x < w) {
        // Draw simple stylized leaf/twig
        scope.drawLine(
            color = TapestryGreen.copy(alpha = 0.5f),
            start = Offset(x, yTop + borderH / 2),
            end = Offset(x + 15f, yTop + borderH / 2 - 10f),
            strokeWidth = 2.5f
        )
        scope.drawCircle(TapestryRed.copy(alpha = 0.5f), radius = 3f, center = Offset(x + 15f, yTop + borderH / 2 - 10f))
        
        x += 160f
    }
}

fun generateShareImage(context: android.content.Context, player: com.example.game.FighterState?, score: Float, kills: Int, isWin: Boolean): android.net.Uri? {
    if (player == null) return null
    val width = 800
    val height = 800
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val androidCanvas = android.graphics.Canvas(bitmap)
    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    
    // Background
    androidCanvas.drawColor(android.graphics.Color.parseColor("#E5D3B3"))
    
    // Title
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#3B291A")
        textSize = 60f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    androidCanvas.drawText("Bayeux Brawler", width / 2f, 100f, paint)
    
    paint.textSize = 35f
    paint.typeface = android.graphics.Typeface.SERIF
    val statusText = if(isWin) "Vanquished" else "Perished"
    androidCanvas.drawText("Status: " + statusText + " | Score: " + score + "x | Kills: " + kills, width / 2f, 160f, paint)
    
    val wpnBase = player.weaponHead.name + " on a " + player.weaponHandle.name
    val wpnName = if (player.extraAttachments.isNotEmpty()) {
        player.extraAttachments.joinToString(", ") { it.name } + " attached to " + wpnBase
    } else wpnBase
    paint.textSize = 24f
    androidCanvas.drawText("Wielding: " + wpnName, width / 2f, 200f, paint)
    
    // Draw character
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    val size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
    
    drawScope.draw(
        androidx.compose.ui.unit.Density(context),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        composeCanvas,
        size
    ) {
        withTransform({
            scale(2.5f, 2.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(250f, 50f)
        }) {
            val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }, posX = 0f, animFrame = 0f, isDead = false, isDying = false)
            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f)
        }
    }
    
    // Player name text
    val namePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#3B291A")
        textSize = 52f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    androidCanvas.drawText(player.name, width / 2f, 700f, namePaint)

    // Kills count
    val killsPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#9E3624")
        textSize = 38f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    androidCanvas.drawText("$kills kills", width / 2f, 750f, killsPaint)

    // Ancillary labels
    val ancNames = player.extraAttachments.take(4).mapIndexed { i, g -> g.name }
    if (ancNames.isNotEmpty()) {
        val ancPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#3B291A")
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
            textAlign = android.graphics.Paint.Align.CENTER
            alpha = 180
        }
        androidCanvas.drawText("Attachments: " + ancNames.joinToString(", "), width / 2f, 790f, ancPaint)
    }

    // Save to cache
    return try {
        val cachePath = java.io.File(context.cacheDir, "")
        val file = java.io.File(cachePath, "share.png")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun MusicDecisionScreen(
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "THE BARDS AWAIT THY COMMAND",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = TapestryRed
        )
        Text(
            "Howsoever shall the music evolve?",
            fontFamily = FontFamily.Serif,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontSize = 16.sp,
            color = TapestryDark,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        // Simple 2-column grid-like layout for cards
        val chunked = options.chunked(2)
        chunked.forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                rowOptions.forEach { option ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .weight(1f)
                            .height(100.dp)
                            .clickable { onSelect(option) },
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TapestryLight),
                        border = androidx.compose.foundation.BorderStroke(2.dp, TapestryDark),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                option,
                                fontWeight = FontWeight.Bold,
                                color = TapestryDark,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                // Fill remaining space if odd number
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f).padding(8.dp))
                }
            }
        }
    }
}
