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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // it's an idle auto-battler, screen shouldn't sleep mid-fight
        com.example.game.MedievalAudioSynth.init(applicationContext)
        
        // Initialize the free, offline, local vocalization engine
        // com.example.game.MedievalVocalizer.init(applicationContext)
        com.example.game.MedievalHarpPlayer.init(applicationContext)

        // Let's set the activity orientation request to user's sensor to encourage landscape,
        // but handle layout adaptation gracefully in Compose!
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        BayeuxAppContent(viewModel)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

private var currentVoicePlayer: android.media.MediaPlayer? = null

@Composable
fun BayeuxAppContent(viewModel: GameViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var forceBypass by rememberSaveable { mutableStateOf(false) }

    // Medieval Harp Background Music State
    var musicOn by rememberSaveable { mutableStateOf(true) }
    val uiState by viewModel.uiState.collectAsState()
    val hasTrumpeter = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.TRUMPETER)
    val appliedMusicMoods = uiState.appliedMusicMoods

    LaunchedEffect(musicOn, uiState.level, hasTrumpeter, appliedMusicMoods, uiState.gameCount) {
        if (musicOn) {
            MedievalHarpPlayer.startMusic(level = uiState.level, hasTrumpeter = hasTrumpeter, moods = appliedMusicMoods)
        } else {
            MedievalHarpPlayer.stopMusic()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, musicOn) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> MedievalHarpPlayer.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (musicOn) MedievalHarpPlayer.resume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    fun playRandomVoiceClip() {
        if (currentVoicePlayer?.isPlaying == true) return
        try {
            val am = context.assets
            val files = am.list("victory")
            if (files != null && files.isNotEmpty()) {
                val audioFiles = files.filter { it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".mp3") }
                if (audioFiles.isNotEmpty()) {
                    MedievalHarpPlayer.setVolume(0.2f)
                    val randomFile = audioFiles.random()
                    val afd = am.openFd("victory/$randomFile")
                    currentVoicePlayer = android.media.MediaPlayer()
                    currentVoicePlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    currentVoicePlayer?.setOnCompletionListener { 
                        it.release() 
                        if (currentVoicePlayer == it) {
                            currentVoicePlayer = null
                            MedievalHarpPlayer.setVolume(1.0f)
                        }
                    }
                    currentVoicePlayer?.prepare()
                    currentVoicePlayer?.start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MedievalHarpPlayer.setVolume(1.0f)
        }
    }

    LaunchedEffect(uiState.battleWon) {
        if (uiState.battleWon) playRandomVoiceClip()
    }
    LaunchedEffect(uiState.battleLost) {
        // Voice clips are victory-only
    }
    LaunchedEffect(uiState.showLevelUpScreen) {
        // Voice clips are victory-only
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
                            .weight(0.19f)
                            .fillMaxHeight()
                    ) {
                        CharacterPreviewCard(uiState = uiState)
                    }

                    // Middle: Tabbed Component Lists or Level Up Screen
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
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
                                onToggleDualWield = { viewModel.toggleDualWield() },
                                onToggleThroneMode = { viewModel.toggleThroneMode() }
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
                            onRandomize = { viewModel.randomizeGear() },
                            onToggleThroneMode = { viewModel.toggleThroneMode() }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Stats popup button
            Box(
                modifier = Modifier
                    .background(TapestryDark.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, TapestryDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { showStatsPopup = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚔ STATS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (uiState.showLevelUpScreen || uiState.pendingLevelUpChoices.isNotEmpty()) TapestryRed else TapestryDark
                )
            }
            Box(
                modifier = Modifier
                    .background(TapestryRed, RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LVL %02d".format(uiState.level),
                    color = TapestryLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Version Number
            Text(
                text = com.example.BuildConfig.VERSION_NAME,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.6f)
            )
        }
        
        // Center: Battle Name
        Text(
            text = FlavourText.battleName(MedievalHarpPlayer.gameSeed, uiState.level),
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = TapestryDark,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SCORE:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TapestryDark.copy(alpha = 0.6f))
                Text("${uiState.score}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, color = TapestryRed)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("HIGH:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TapestryDark.copy(alpha = 0.6f))
                Text("${uiState.highscore}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TapestryDark)
            }
            
            // Medieval Harp Music Toggle
            Box(
                modifier = Modifier
                    .background(if (musicOn) TapestryGreen else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    .border(1.dp, TapestryDark, RoundedCornerShape(2.dp))
                    .clickable { onToggleMusic() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("toggle_harp_music_btn")
            ) {
                Text(
                    text = if (musicOn) "🎵" else "🎵✕",
                    color = TapestryLight,
                    fontSize = 10.sp
                )
            }
            // SFX Toggle
            var sfxOn by remember { mutableStateOf(com.example.game.MedievalAudioSynth.sfxEnabled) }
            Box(
                modifier = Modifier
                    .background(if (sfxOn) TapestryGreen else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    .border(1.dp, TapestryDark, RoundedCornerShape(2.dp))
                    .clickable {
                        sfxOn = !sfxOn
                        com.example.game.MedievalAudioSynth.sfxEnabled = sfxOn
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("toggle_sfx_btn")
            ) {
                Text(
                    text = if (sfxOn) "🔊" else "🔇",
                    color = TapestryLight,
                    fontSize = 10.sp
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

        val auraTransition = rememberInfiniteTransition(label = "aura")
        val risePhase by auraTransition.animateFloat(
            initialValue = 0f, targetValue = 350f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rise"
        )
        val swayPhase by auraTransition.animateFloat(
            initialValue = 0f, targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "sway"
        )
        val auraTier = 1 + minOf(3, uiState.level / 3)

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
                val pulse = (sin(swayPhase) + 1f) / 2f
                
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
                        radius = 240f + 40f * auraTier + 30f * pulse
                    )
                )

                // Divine Sparks rising aggressively
                val sparkCount = 10 + 6 * auraTier
                val sparkColor = if (auraTier >= 3) Color(0xFFFFFBE8) else Color(0xFFFFF1AA)
                for (i in 0 until sparkCount) {
                    val floatY = (centerY + 160f) - ((risePhase + i * 35f) % 350f)
                    val floatX = centerX + sin(swayPhase * 3f + i) * 110f
                    val r = 2f + (i % 4)
                    drawCircle(sparkColor.copy(alpha = 0.9f), radius = r, center = Offset(floatX, floatY))
                    drawLine(sparkColor.copy(alpha = 0.4f), Offset(floatX, floatY + r), Offset(floatX, floatY + r + 15f), strokeWidth = 1.5f)
                }
                if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE)) {
                    drawCircle(TapestryMustard.copy(alpha = 0.35f + 0.2f * pulse), radius = 150f + 8f * pulse,
                        center = Offset(centerX, centerY), style = Stroke(width = 5f))
                }

                // Character body spans ~y146..413 after the 1.35 scale (pivot 200), centre ≈280.
                // Anchor that to the panel midpoint so the knight is centered, not floating.
                translate(top = centerY - 280f) {
                    if (uiState.isThroneMode) {
                        val front = com.example.game.FighterState(
                            id = "front", name = "Front", isPlayer = true,
                            maxHp = 100f, hp = 100f, posX = centerX + 45f, targetX = centerX + 45f,
                            animFrame = animFrame, facingRight = true,
                            size = uiState.characterSize,
                            weaponHead = uiState.weaponHead,
                            weaponHandle = uiState.weaponHandle,
                            shield = uiState.shield,
                            armor = uiState.armor,
                            headgear = uiState.headgear,
                            isDualWielding = uiState.isDualWielding,
                            hairColor = uiState.hairColor, hairStyle = uiState.hairStyle,
                            faceNoseShape = uiState.faceNoseShape, faceBiteShape = uiState.faceBiteShape, faceForehead = uiState.faceForehead
                        )
                        val back = front.copy(id = "back", name = "Back", posX = centerX - 45f, targetX = centerX - 45f)
                        val king = com.example.game.FighterState(
                            id = "king", name = uiState.playerName, isPlayer = true, isLord = true, isMounted = true,
                            maxHp = 100f, hp = 100f, posX = centerX, targetX = centerX,
                            animFrame = animFrame, facingRight = true,
                            size = uiState.characterSize,
                            weaponHead = com.example.game.GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                            weaponHandle = com.example.game.GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                            shield = com.example.game.GameData.SHIELDS.first { it.id == "shield_none" },
                            armor = uiState.armor,
                            headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_crown" },
                            isDualWielding = false,
                            hairColor = uiState.hairColor, hairStyle = uiState.hairStyle,
                            faceNoseShape = uiState.faceNoseShape, faceBiteShape = uiState.faceBiteShape, faceForehead = uiState.faceForehead
                        )
                        TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, king, scale = 1.35f)
                        TapestryRenderer.drawCharacter(this, back, scale = 1.35f, isBattleActive = true)
                        TapestryRenderer.drawCharacter(this, king, scale = 1.35f, isBattleActive = true)
                        TapestryRenderer.drawCharacter(this, front, scale = 1.35f, isBattleActive = true)
                    } else {
                        // Create dummy FighterState mirroring chosen gear
                        val dummyFighter = com.example.game.FighterState(
                            id = "preview",
                            name = uiState.playerName,
                            isPlayer = true,
                            faceNoseShape = uiState.faceNoseShape,
                            faceBiteShape = uiState.faceBiteShape,
                            faceForehead = uiState.faceForehead,
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
                            isMounted = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS),
                            isChariot = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT),
                            isStilts = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS),
                            isLord = false,
                            mountHp = if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE)) 80f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT)) 120f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS)) 40f else 0f
                        )
                        TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, dummyFighter, scale = 1.35f)
                        TapestryRenderer.drawCharacter(this, dummyFighter, scale = 1.35f, isBattleActive = false)
                    }
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
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(bottom = 16.dp)
                        .horizontalScroll(scrollState),
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
                            "comedy" -> Triple(Color(0xFFFFE0E0), Color(0xFFC62828), "Comedy Gear")
                            else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
                        }
                        Card(
                            modifier = Modifier
                                .width(285.dp)
                                .fillMaxHeight()
                                .clickable { onSelectChoice(choice.id) },
                            colors = CardDefaults.cardColors(containerColor = bannerColor),
                            border = BorderStroke(2.dp, titleColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    tagLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .border(1.dp, titleColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Text(
                                    choice.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TapestryDark,
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    choice.description,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    color = TapestryDark.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
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
                            .width(260.dp)
                            .fillMaxHeight()
                            .clickable { onSkipReward() },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        border = BorderStroke(2.dp, TapestryDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No Reward",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TapestryDark.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, TapestryDark.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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

                // Visual Scrollbar overlay
                if (scrollState.maxValue > 0) {
                    val scrollPercent = scrollState.value.toFloat() / scrollState.maxValue
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .width(200.dp)
                            .height(6.dp)
                            .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(80.dp)
                                .offset(x = (120.dp * scrollPercent))
                                .background(TapestryDark, RoundedCornerShape(3.dp))
                        )
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
    onToggleDualWield: () -> Unit,
    onToggleThroneMode: () -> Unit
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
                        if (uiState.hasThroneOption) {
                            Button(
                                onClick = onToggleThroneMode,
                                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isThroneMode) TapestryGreen else TapestryDark),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(bottom = 8.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = "I WON'T FIGHT (Throne Mode)",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TapestryLight
                                )
                            }
                        }
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
                            val colors = listOf(Color(0xFF888888), Color(0xFFC08030), Color(0xFF5A442E), Color(0xFF2C2219))
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
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selectedTab == 0) {
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
                        Text("Dual Wield (Disables shield, faster attacks but 20% miss chance)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                    }
                }
                
                val itemsToShow = when (selectedTab) {
                    0 -> GameData.WEAPON_HEADS.filter { it.id in uiState.unlockedGearIds }
                    1 -> GameData.SHIELDS.filter { it.id in uiState.unlockedGearIds }
                    2 -> GameData.ARMOR_PIECES.filter { it.id in uiState.unlockedGearIds }
                    else -> GameData.HEADGEAR_PIECES.filter { it.id in uiState.unlockedGearIds }
                }.sortedBy { item ->
                    // "None/bare" options lead (top-left) so they read as the baseline, not an upgrade
                    if (item.id.endsWith("_none") || item.id == "head_bare" || item.id == "handle_fists") 0 else 1
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
                } // End Column
            }
        }

        // Handle attachment selection (Only visible on Weapon Tab, not for ranged)
        if (selectedTab == 0 && uiState.weaponHead.id != "head_bare" && uiState.weaponHead.id !in listOf("head_bow", "head_longbow", "head_slingshot")) {
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
                            text = handle.itemName,
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
                    text = item.itemName.uppercase(),
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
    onRandomize: () -> Unit,
    onToggleThroneMode: () -> Unit
) {
    // We compute live stats by spinning up a dummy player FighterState
    val dummyFighter = remember(uiState) {
        FighterState(
            id = "stat_dummy",
            name = uiState.playerName,
            isPlayer = true,
            faceNoseShape = uiState.faceNoseShape,
            faceBiteShape = uiState.faceBiteShape,
            faceForehead = uiState.faceForehead,
            maxHp = 100f,
            hp = 100f,
            weaponHead = if (uiState.isThroneMode) com.example.game.GameData.WEAPON_HEADS.first { it.id == "head_bare" } else uiState.weaponHead,
            weaponHandle = if (uiState.isThroneMode) com.example.game.GameData.WEAPON_HANDLES.first { it.id == "handle_fists" } else uiState.weaponHandle,
            shield = if (uiState.isThroneMode) com.example.game.GameData.SHIELDS.first { it.id == "shield_none" } else uiState.shield,
            armor = uiState.armor,
            headgear = if (uiState.isThroneMode) com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_crown" } else uiState.headgear,
            isDualWielding = uiState.isDualWielding,
            posX = 0f, targetX = 0f,
            isMounted = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS) || uiState.isThroneMode,
            mountHp = if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE)) 80f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT)) 120f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS)) 40f else 0f,
            isChariot = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT),
            isLord = uiState.isThroneMode
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
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "KNIGHT BASE STATS",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = TapestryDark.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "v${com.example.BuildConfig.VERSION_NAME}",
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Serif,
                    color = TapestryDark.copy(alpha = 0.4f)
                )
            }

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
        val latinHeadline = remember(uiState.level) { FlavourText.latinHeadline(MedievalHarpPlayer.gameSeed, uiState.level) }
        val borderSeed = remember(uiState.level) { MedievalHarpPlayer.gameSeed * 7L + uiState.level }
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
                    color = Color(0x15382F22),
                    start = Offset(0f, yOffset),
                    end = Offset(size.width, yOffset),
                    strokeWidth = 2f
                )
                yOffset += 4f
            }
            var xOffset = 0f
            while (xOffset < size.width) {
                drawLine(
                    color = Color(0x10382F22),
                    start = Offset(xOffset, 0f),
                    end = Offset(xOffset, size.height),
                    strokeWidth = 2f
                )
                xOffset += 4f
            }

            // 1. Draw TOP Embroidered Border (characteristic of Bayeux)
            drawTapestryBorder(this, isTop = true, textHeadline = "", motifSeed = borderSeed)

            // 2. Draw BOTTOM Embroidered Border (With decorative stags and some funny bones of fallen foes!)
            drawTapestryBorder(this, isTop = false, textHeadline = "", motifSeed = borderSeed + 1)

            val shakeAmt = shake
            val playerScaleX = size.width / 1000f
            val scaleFactor = size.height / 350f
            
            val scaledCameraX = uiState.cameraX * playerScaleX
            val offsetX = (if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f) - scaledCameraX
            val offsetY = if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f

            // Anchor the ground to the bottom border: fighter feet (200 + 158*scale in this
            // block's coordinates) land 45px above the border — room for bodies/blood pools,
            // no dead space.
            val groundOffsetY = (size.height - 40f - 45f) - (200f + 158f * scaleFactor)
            withTransform({
                // Keep the game world inside the embroidered borders (40px bands)
                clipRect(top = 40f, bottom = size.height - 40f)
                translate(left = offsetX, top = offsetY + groundOffsetY)
            }) {
                // 2.5 Draw Background Environment Objects
                uiState.backgroundObjects.forEach { bg ->
                    val scaledBgX = bg.posX * playerScaleX
                    com.example.game.drawBackgroundObject(this, bg, scaledBgX, scaleFactor)
                }
            
                // 3. Draw Players and Enemies
                if (playerFighter != null) {

                // Draw Knight
                val scaledPlayer = playerFighter.copy(
                    posX = playerFighter.posX * playerScaleX
                )
                // Ancillaries draw behind the player (matches customization/throne previews) so
                // Lil Guy's backpack reads as attached to the knight's back, not floating in front of him
                TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, scaledPlayer, scale = scaleFactor)
                TapestryRenderer.drawCharacter(this, scaledPlayer, scale = scaleFactor)

                // Draw health bar for Player
                val px = scaledPlayer.posX
                val headDist = if (scaledPlayer.isMounted && !scaledPlayer.isChariot) 70f else if (scaledPlayer.isChariot) 50f else 40f
                // Clamp so the bar never rises above the top border clip (tall fighters/mounts)
                val py = (200f - (headDist + 35f) * scaledPlayer.size * scaleFactor).coerceAtLeast(55f - groundOffsetY)
                drawHealthBar(this, px, py, playerFighter.hp, playerFighter.ghostHp, playerFighter.maxHp)
                drawStatusEffects(this, px, py - 10f, playerFighter)

                // Draw Enemies
                enemies.forEach { enemy ->
                    val scaledEnemy = enemy.copy(
                        posX = enemy.posX * playerScaleX
                    )
                    TapestryRenderer.drawCharacter(this, scaledEnemy, scale = scaleFactor)
                    
                    // Draw health bar for enemy
                    if (!enemy.isDead) {
                        val enemyHeadDist = if (scaledEnemy.isMounted && !scaledEnemy.isChariot) 70f else if (scaledEnemy.isChariot) 50f else 40f
                        val epy = (200f - (enemyHeadDist + 35f) * scaledEnemy.size * scaleFactor).coerceAtLeast(55f - groundOffsetY)
                        drawHealthBar(this, scaledEnemy.posX, epy, enemy.hp, enemy.ghostHp, enemy.maxHp)
                        drawStatusEffects(this, scaledEnemy.posX, epy - 12f, enemy)
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
                    } else if (proj.type == com.example.game.ProjectileType.JAVELIN) {
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
                    } else if (proj.type == com.example.game.ProjectileType.ARROW) {
                        // Draw flying arrow line with feathers
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        // Match embedded arrows, which inherit the fighter transform's scaleFactor
                        val strokeW = (if (proj.isBallista) 10f else 5f * proj.sizeMultiplier) * scaleFactor
                        val length = 55f * proj.sizeMultiplier * scaleFactor
                        
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

                // 5. Draw Game Particles (Blood, Smoke)
                viewModel.particlesState.value.forEach { part ->
                    val px = part.x * playerScaleX
                    val py = 200f + (part.y - 200f) * scaleFactor
                    
                    if (part.y >= 350f && !part.isSmoke) {
                        val poolWidth = 5f + (part.age / part.maxAge) * 15f
                        val poolHeight = 2f + (part.age / part.maxAge) * 5f
                        drawOval(
                            color = part.color.copy(alpha = (1f - (part.age / part.maxAge) * 0.5f).coerceIn(0f, 1f)),
                            topLeft = Offset(px - poolWidth / 2, py - poolHeight / 2),
                            size = Size(poolWidth, poolHeight)
                        )
                    } else {
                        val baseRadius = if (part.isSmoke) 6f + (part.age * 5f) else 2.5f + (Math.random() * 2f).toFloat()
                        drawCircle(
                            color = part.color.copy(alpha = (1f - (part.age / part.maxAge)).coerceIn(0f, 1f)),
                            radius = baseRadius,
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
                    
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isWin) {
                            val perf = player?.let {
                                val frac = it.hp / it.maxHp
                                when { frac >= 0.95f -> Perf.FLAWLESS; frac <= 0.25f -> Perf.PYRRHIC; else -> Perf.STANDARD }
                            } ?: Perf.STANDARD
                            Text(
                                text = FlavourText.victoryQuote(MedievalHarpPlayer.gameSeed, uiState.level, perf),
                                fontSize = 11.sp, fontFamily = FontFamily.Serif, color = TapestryDark, textAlign = TextAlign.Center
                            )
                        } else {
                            val quote = remember { FlavourText.defeatQuote() }

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
                                                // Draw just head without helmet; neutralize size and mounts,
                                                // which shift the head out of this fixed-position crop
                                                val dummy = player.copy(headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it == com.example.game.GameData.HeadgearPiece.NONE }, posX = 0f, animFrame = 0f, isDead = false, isDying = false, size = 1f, isMounted = false, isChariot = false, isStilts = false, isLord = false)
                                                com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f, isBattleActive = false)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Name: ${uiState.playerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryDark)
                                    val wpnName = if (uiState.weaponHead.id == "head_bare" && uiState.weaponHandle.id == "handle_fists") "Bare Hands (Brawler)" else uiState.weaponHead.itemName
                                    Text("Weapon: $wpnName", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                    val anc = uiState.unlockedAncillaries.joinToString(", ") { it.ancillaryName }
                                    Text("Ancillaries: ${if (anc.isEmpty()) "None" else anc}", fontSize = 9.sp, fontFamily = FontFamily.Serif, color = TapestryDark)
                                    Text("Kills: ${uiState.totalKills}", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = TapestryRed)
                                }
                            }
                        }
                    }
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val shareScope = rememberCoroutineScope()
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
                        
                        if (!isWin) {
                            Button(
                                onClick = {
                                    shareScope.launch(Dispatchers.IO) {
                                        val uri = generateShareImage(context, player, uiState, isWin)
                                        if (uri != null) withContext(Dispatchers.Main) {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share Tale"))
                                        }
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
    val barWidth = 100f
    val barHeight = 12f
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
        setColor(color.copy(alpha = alpha).toArgb())
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Shadow outline for thread thickness look
    val outlinePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 28f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        setColor(TapestryDark.copy(alpha = alpha).toArgb())
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
    textHeadline: String,
    motifSeed: Long
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
            color = TapestryDark.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        scope.drawContext.canvas.nativeCanvas.drawText(textHeadline, w / 2, 28f, paint)
    }

    // 3. Bayeux margin bestiary - seeded motifs instead of one repeated twig
    val motifRng = kotlin.random.Random(motifSeed)
    var x = 30f
    val cy = yTop + borderH / 2
    while (x < w - 40f) {
        when (if (isTop) motifRng.nextInt(5) else motifRng.nextInt(6)) {
            0 -> { // STAG: body line, head, antlers
                scope.drawLine(TapestryMustard, Offset(x, cy + 6f), Offset(x + 22f, cy + 6f), strokeWidth = 3f)
                scope.drawLine(TapestryMustard, Offset(x + 4f, cy + 6f), Offset(x + 4f, cy + 14f), strokeWidth = 2.5f)
                scope.drawLine(TapestryMustard, Offset(x + 18f, cy + 6f), Offset(x + 18f, cy + 14f), strokeWidth = 2.5f)
                scope.drawLine(TapestryMustard, Offset(x + 22f, cy + 6f), Offset(x + 26f, cy - 2f), strokeWidth = 2.5f)
                scope.drawLine(TapestryDark, Offset(x + 26f, cy - 2f), Offset(x + 22f, cy - 10f), strokeWidth = 2f)
                scope.drawLine(TapestryDark, Offset(x + 26f, cy - 2f), Offset(x + 30f, cy - 10f), strokeWidth = 2f)
            }
            1 -> { // HOUND: low body, tail up
                scope.drawLine(TapestryRed.copy(alpha = 0.8f), Offset(x, cy + 8f), Offset(x + 18f, cy + 8f), strokeWidth = 3.5f)
                scope.drawLine(TapestryRed.copy(alpha = 0.8f), Offset(x + 18f, cy + 8f), Offset(x + 24f, cy + 2f), strokeWidth = 2.5f)
                scope.drawLine(TapestryRed.copy(alpha = 0.8f), Offset(x, cy + 8f), Offset(x - 4f, cy), strokeWidth = 2f)
                scope.drawCircle(TapestryDark, radius = 2f, center = Offset(x + 24f, cy + 1f))
            }
            2 -> { // RAVEN: wing chevrons
                scope.drawLine(TapestryDark, Offset(x, cy), Offset(x + 8f, cy - 6f), strokeWidth = 2.5f)
                scope.drawLine(TapestryDark, Offset(x + 8f, cy - 6f), Offset(x + 16f, cy), strokeWidth = 2.5f)
                scope.drawCircle(TapestryDark, radius = 3f, center = Offset(x + 8f, cy - 2f))
            }
            3 -> { // POINTING HAND (manicula)
                scope.drawLine(TapestryDark, Offset(x, cy), Offset(x + 14f, cy), strokeWidth = 4f)
                scope.drawLine(TapestryDark, Offset(x + 14f, cy), Offset(x + 20f, cy), strokeWidth = 2f)
                scope.drawCircle(TapestryDark, radius = 3.5f, center = Offset(x, cy))
            }
            4 -> { // VINE TWIG (the classic)
                scope.drawLine(TapestryGreen.copy(alpha = 0.6f), Offset(x, cy), Offset(x + 15f, cy - 10f), strokeWidth = 2.5f)
                scope.drawCircle(TapestryRed.copy(alpha = 0.6f), radius = 3f, center = Offset(x + 15f, cy - 10f))
            }
            else -> { // FALLEN FOE + bones (bottom border only - the comment's old promise, honoured)
                scope.drawLine(TapestryDark.copy(alpha = 0.7f), Offset(x, cy + 6f), Offset(x + 16f, cy + 6f), strokeWidth = 3f)
                scope.drawCircle(TapestryDark.copy(alpha = 0.7f), radius = 3f, center = Offset(x + 19f, cy + 6f))
                scope.drawLine(TapestryLight, Offset(x + 4f, cy + 4f), Offset(x + 8f, cy + 8f), strokeWidth = 1.5f)
                scope.drawLine(TapestryLight, Offset(x + 8f, cy + 4f), Offset(x + 4f, cy + 8f), strokeWidth = 1.5f)
            }
        }
        x += 90f + motifRng.nextInt(70)
    }
}

fun generateShareImage(context: android.content.Context, player: com.example.game.FighterState?, uiState: com.example.game.BattleSimState, isWin: Boolean): android.net.Uri? {
    if (player == null) return null
    val width = 1080
    val height = 1080

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
    androidCanvas.drawText("Bayeux Brawler", width / 2f, 90f, paint)
    
    paint.textSize = 35f
    paint.typeface = android.graphics.Typeface.SERIF
    val statusText = if(isWin) "Vanquished" else "Perished"
    androidCanvas.drawText("Status: " + statusText + " | Score: " + uiState.score + " | Kills: " + uiState.totalKills, width / 2f, 150f, paint)
    
    val wpnBase = uiState.weaponHead.itemName + " on a " + uiState.weaponHandle.itemName
    val wpnName = if (player.extraAttachments.isNotEmpty()) {
        player.extraAttachments.joinToString(", ") { it.itemName } + " attached to " + wpnBase
    } else wpnBase
    
    // StaticLayout handles the center alignment of lines, but TextPaint itself must be LEFT aligned
    val textPaint = android.text.TextPaint(paint).apply { 
        textSize = 24f 
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val wpnText = if (uiState.weaponHead.id == "head_bare" && uiState.weaponHandle.id == "handle_fists") {
        "Wielding: Bare Hands (Brawler)"
    } else {
        "Wielding: $wpnName"
    }
    val staticLayout = android.text.StaticLayout.Builder.obtain(wpnText, 0, wpnText.length, textPaint, width - 40)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .build()
        
    androidCanvas.save()
    // Weapon text vertically positioned at 190, horizontally aligned via ALIGN_CENTER in width-40
    androidCanvas.translate(20f, 190f)
    staticLayout.draw(androidCanvas)
    androidCanvas.restore()
    
    // Draw character
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    val size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
    
    drawScope.draw(
        androidx.compose.ui.unit.Density(context),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        composeCanvas,
        size
    ) {
        // Create a boxed frame for the portrait
        val portraitRect = androidx.compose.ui.geometry.Rect(
            left = width / 2f - 200f,
            top = 260f,
            right = width / 2f + 200f,
            bottom = 600f
        )
        
        // Draw the box background and border
        drawRect(androidx.compose.ui.graphics.Color(0xFFF5F5F5), topLeft = portraitRect.topLeft, size = portraitRect.size)
        drawRect(androidx.compose.ui.graphics.Color(0xFF3B291A), topLeft = portraitRect.topLeft, size = portraitRect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))

        withTransform({
            clipRect(portraitRect.left, portraitRect.top, portraitRect.right, portraitRect.bottom)
            scale(3.5f, 3.5f, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(114.3f, -50f)
        }) {
            val dummy = player.copy(
                headgear = com.example.game.GameData.HEADGEAR_PIECES.first { it == com.example.game.GameData.HeadgearPiece.NONE },
                posX = 0f,
                animFrame = 0f,
                isDead = false,
                isDying = false,
                facingRight = true, // Force face rendering to point correctly in portrait box
                size = 1f, // portrait zoom targets a fixed head position; a runt/giant body would slide the face out of the crop
                isMounted = false, isChariot = false, isStilts = false, isLord = false // mounts shift the rider up and out of the crop
            )
            com.example.game.TapestryRenderer.drawCharacter(this, dummy, scale = 1f, isBattleActive = false)
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
    androidCanvas.drawText(player.name, width / 2f, 660f, namePaint)

    // Kills count
    val killsPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#9E3624")
        textSize = 38f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    androidCanvas.drawText("${uiState.totalKills} kills", width / 2f, 720f, killsPaint)

    // Ancillary labels
    val ancNames = player.extraAttachments.take(4).mapIndexed { i, g -> g.itemName }
    if (ancNames.isNotEmpty()) {
        val ancPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#3B291A")
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
            textAlign = android.graphics.Paint.Align.CENTER
            alpha = 180
        }
        androidCanvas.drawText("Attachments: " + ancNames.joinToString(", "), width / 2f, 770f, ancPaint)
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
