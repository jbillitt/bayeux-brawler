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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // it's an idle auto-battler, screen shouldn't sleep mid-fight
        com.example.game.MedievalAudioSynth.init(applicationContext)
        com.example.game.VectorAsset.init(applicationContext) // data-driven building art in assets/art/
        com.example.game.GameProfile.init(applicationContext) // unlocks that survive between runs

        // Initialize the free, offline, local vocalization engine
        // com.example.game.MedievalVocalizer.init(applicationContext)
        com.example.game.MedievalHarpPlayer.init(applicationContext)

        // Billing first: it restores an existing "remove ads" purchase by querying owned products,
        // which AdGate then reads. Ads.init is a no-op in a debug build or once ads are purchased
        // away, and gathers UMP consent before initialising the ad SDK.
        com.example.game.Billing.init(applicationContext)
        com.example.game.Ads.init(this)

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

    // The ad break the ViewModel asked for. It only ever fires on a defeat screen, and the Activity
    // owns the call because the SDK needs a real Activity.
    val activity = LocalContext.current as? android.app.Activity
    val pendingInterstitial by viewModel.pendingInterstitial.collectAsState()
    LaunchedEffect(pendingInterstitial) {
        if (pendingInterstitial && activity != null) {
            com.example.game.Ads.showInterstitial(activity)
            viewModel.clearPendingInterstitial()
        }
    }
    val hasTrumpeter = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.TRUMPETER)
    // Brawl and Throne are first-class themes. Throne takes precedence in resolveSongSpec.
    LaunchedEffect(
        musicOn,
        uiState.level,
        hasTrumpeter,
        uiState.appliedMusicMoods,
        uiState.brawlMode,
        uiState.isThroneMode,
        uiState.forceThroneMusic,
        uiState.gameCount
    ) {
        if (musicOn) {
            MedievalHarpPlayer.startMusic(
                level = uiState.level,
                hasTrumpeter = hasTrumpeter,
                moods = uiState.appliedMusicMoods,
                brawl = uiState.brawlMode,
                throne = uiState.isThroneMode || uiState.forceThroneMusic
            )
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
    fun playRandomVoiceClip(folder: String = "victory") {
        if (!com.example.game.MedievalAudioSynth.sfxEnabled) return
        if (currentVoicePlayer?.isPlaying == true) return
        try {
            val am = context.assets
            val files = am.list(folder)
            if (files != null && files.isNotEmpty()) {
                val audioFiles = files.filter { it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".mp3") }
                if (audioFiles.isNotEmpty()) {
                    MedievalHarpPlayer.setVolume(0.2f)
                    val randomFile = audioFiles.random()
                    val afd = am.openFd("$folder/$randomFile")
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
        // A voluntary retirement is not a death — the defeat dirge stays silent for it.
        if (uiState.battleLost && !uiState.isRetired) playRandomVoiceClip("defeat")
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
    var showPauseMenu by remember { mutableStateOf(false) }
    var showTrophies by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenBg)
            .border(8.dp, TapestryDark)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. HUD / HEADER BAR
            HeaderBar(
                uiState = uiState,
                musicOn = musicOn,
                onToggleMusic = onToggleMusic,
                playerState = playerFighter,
                allies = enemies.filter { it.isPlayer },
                onTriggerWeather = { viewModel.triggerWeather(it) },
                onOpenMenu = {
                    showPauseMenu = true
                    viewModel.setPaused(true)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. MAIN WORKSPACE (Active Battle or Gear Selection)
            if (uiState.isBattleActive) {
                BattlefieldScene(
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = { viewModel.dismissBattleResult() }
                )
            } else {
                val rewardActivity = LocalContext.current as? android.app.Activity
                // Buying the game ad-free must not cost you what the ad bought. The same boon, on
                // the same budget, with nothing to sit through.
                val boonIsFree = com.example.game.GameProfile.cached.adFreePurchased
                val levelUp: @Composable () -> Unit = {
                    LevelUpScreen(
                        uiState = uiState,
                        boonOfferAvailable = uiState.boonLevel &&
                            !uiState.adRewardClaimedThisLevel &&
                            uiState.pendingLevelUpChoices.isNotEmpty() &&
                            (boonIsFree || (com.example.game.Ads.rewardedReady() && rewardActivity != null)),
                        boonIsFree = boonIsFree,
                        onClaimBoon = {
                            if (boonIsFree) viewModel.grantAdReward()
                            else rewardActivity?.let { act ->
                                // Paid out on the SDK's earned callback only, never on dismissal.
                                com.example.game.Ads.showRewarded(act) { viewModel.grantAdReward() }
                            }
                        },
                        onClearBoonBanner = { viewModel.clearLastBoon() },
                        onSelectChoice = { viewModel.selectLevelUpChoice(it) },
                        onSelectMount = { viewModel.selectMount(it) },
                        onStartBattle = { viewModel.startBattle() },
                        onSkipReward = { viewModel.selectLevelUpChoice("") },
                        onClearSkipBonus = { viewModel.clearSkipBonus() }
                    )
                }

                // Choosing spoils takes the whole width. In the middle column the tiles got about
                // 475dp between the preview and the stats panel, which cropped the third card off
                // both this screen and the player's attention. Once the choice is made the screen
                // falls back to the three columns, so the preview and the launch panel are never
                // out of reach for longer than the decision itself.
                val choosingSpoils = !showTrophies && !uiState.showMusicDecision &&
                    (uiState.showLevelUpScreen || uiState.level > 1) &&
                    uiState.pendingLevelUpChoices.isNotEmpty()

                if (choosingSpoils) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { levelUp() }
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
                        CharacterPreviewCard(
                            uiState = uiState,
                            onSelectMount = { viewModel.selectMount(it) },
                            onSelectThrone = { viewModel.selectThrone() },
                            onClearMount = { viewModel.clearMount() }
                        )
                    }

                    // Middle: Tabbed Component Lists or Level Up Screen
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        // The trophies and store links used to live in a header Row here. On a
                        // landscape phone that band cost the middle panel ~20dp of the one axis it
                        // cannot spare, squashing the reward tiles and the weapon-head grid below
                        // it. Both now live in the burger menu, which costs the panel nothing.
                        if (showTrophies) {
                            // Back sits inside the trophies view only, so it costs height on the
                            // one screen that can spare it rather than on every panel.
                            Text(
                                text = "‹ BACK",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = TapestryDark,
                                modifier = Modifier
                                    .clickable {
                                        showTrophies = false
                                        MedievalAudioSynth.playSound(SoundType.SWOOSH)
                                    }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                            TrophiesPanel(uiState)
                        } else if (uiState.showMusicDecision) {
                            MusicDecisionScreen(
                                options = uiState.pendingMusicOptions,
                                onSelect = { viewModel.selectMusicMood(it) }
                            )
                        } else if (uiState.showLevelUpScreen || uiState.level > 1) {
                            // The "Proceed to next battle" state — the choice itself was made
                            // full-bleed above.
                            levelUp()
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

        // The heavens open over the WHOLE screen, header band included, in a canvas of its own
        // above the Column. Inside the battlefield canvas it could only ever cover the battlefield,
        // and it shared that canvas's layers and camera-dependent state — so it washed part of the
        // field and drifted off as the player walked right.
        DivineWeatherOverlay(viewModel)

        if (showPauseMenu) {
            PauseMenuOverlay(
                musicOn = musicOn,
                onToggleMusic = onToggleMusic,
                canRetire = uiState.isBattleActive && !uiState.battleWon && !uiState.battleLost,
                inBattle = uiState.isBattleActive,
                onShowTrophies = {
                    showTrophies = true
                    showPauseMenu = false
                    viewModel.setPaused(false)
                },
                onResume = {
                    showPauseMenu = false
                    viewModel.setPaused(false)
                },
                onRetire = {
                    showPauseMenu = false
                    viewModel.retire()
                }
            )
        }
    }
}

/**
 * The Intermissio: a marginal note in the tapestry, not a battle panel. Scrim of dark thread,
 * one linen card crowned with a titulus band (the same border language as the top of the scroll),
 * the music/sound rites, and — set apart below a stitch line — the Retire rite, which arms on
 * first touch so a level-79 campaign cannot end by a slipped thumb.
 */
@Composable
fun PauseMenuOverlay(
    musicOn: Boolean,
    onToggleMusic: () -> Unit,
    canRetire: Boolean,
    /** Out of battle this is a menu, not a pause — the wording changes with it. */
    inBattle: Boolean = true,
    onShowTrophies: () -> Unit = {},
    onResume: () -> Unit,
    onRetire: () -> Unit
) {
    var sfxOn by remember { mutableStateOf(com.example.game.MedievalAudioSynth.sfxEnabled) }
    var retireArmed by remember { mutableStateOf(false) }
    var adsOn by remember { mutableStateOf(com.example.game.AdGate.testAdsEnabled) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryDark.copy(alpha = 0.55f))
            .clickable(onClick = onResume), // tap the linen outside to resume
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TapestryLinenCard),
            modifier = Modifier
                .width(340.dp)
                .border(4.dp, TapestryDark, RoundedCornerShape(8.dp))
                .padding(4.dp)
                .clickable(enabled = false) {}, // swallow taps so the scrim doesn't resume
            shape = RoundedCornerShape(8.dp)
        ) {
            // Scrolls inside the screen on short landscape displays — the Retire rite must
            // never hang off the bottom edge.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Titulus band: the card wears the scroll's own border as its crown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TapestryLinenBg)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✛", fontSize = 11.sp, color = TapestryMustard)
                    Text(
                        if (inBattle) "  INTERMISSIO  " else "  SCRIPTORIUM  ",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = TapestryDark
                    )
                    Text("✛", fontSize = 11.sp, color = TapestryMustard)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(TapestryDark)
                )
                Text(
                    if (inBattle) "The needles rest. The battle holds its breath."
                    else "The needles wait. Choose your rites before the march.",
                    fontFamily = FontFamily.Serif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 11.sp,
                    color = TapestryDark.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    @Composable
                    fun toggleRow(
                        label: String,
                        sub: String,
                        on: Boolean,
                        tag: String,
                        // The state words belong to the thing being toggled. "SILENCED" ads read
                        // as nonsense — a control should say what it actually does.
                        onWord: String = "SOUNDING",
                        offWord: String = "SILENCED",
                        onFlip: () -> Unit
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, TapestryDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { onFlip() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag(tag),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(label, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TapestryDark)
                                Text(sub, fontFamily = FontFamily.Serif, fontSize = 9.sp, color = TapestryDark.copy(alpha = 0.6f))
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (on) TapestryGreen else TapestryDark.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                    .border(1.dp, TapestryDark, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (on) onWord else offWord,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = TapestryLight
                                )
                            }
                        }
                    }

                    toggleRow("MUSICA", "The harpist and his procedural airs", musicOn, "toggle_harp_music_btn") { onToggleMusic() }
                    toggleRow("SONITUS", "The clang and thwack of honest combat", sfxOn, "toggle_sfx_btn") {
                        sfxOn = !sfxOn
                        com.example.game.MedievalAudioSynth.sfxEnabled = sfxOn
                    }
                    // Mid-fight this overlay is a pause and carries only what you need to resume or
                    // withdraw — adding these two pushed the irreversible Retire rite off the
                    // bottom edge on a landscape phone, which the harness caught. Out of battle
                    // there is room, nothing is running, and this is where they belong.
                    if (!inBattle) {
                        // Off by default so testers get a build with no ads in it at all. This is
                        // the only way to turn them on, and it lasts until the app is relaunched.
                        // Flipping the flag was never enough on its own: Ads.init runs once at
                        // launch and bails while the gate is shut, so the SDK was still
                        // uninitialised and no placement could ever load. Re-run it here, now
                        // that the gate is open.
                        val adActivity = LocalContext.current as? android.app.Activity
                        toggleRow(
                            "ENABLE TEST ADS", "Off for testers; turn on to exercise placements",
                            adsOn, "toggle_test_ads_btn", onWord = "SHOWING", offWord = "HIDDEN"
                        ) {
                            adsOn = !adsOn
                            com.example.game.AdGate.testAdsEnabled = adsOn
                            if (adsOn && adActivity != null) com.example.game.Ads.init(adActivity)
                        }

                        // The trophy case, moved off the between-battle panels where its header
                        // row was stealing height from the reward tiles.
                        Button(
                            onClick = onShowTrophies,
                            colors = ButtonDefaults.buttonColors(containerColor = TapestryMustard),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().testTag("open_trophies_btn")
                        ) {
                            Text("✦ Trophies", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryDark)
                        }
                    }

                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = TapestryGreen),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().testTag("resume_battle_btn")
                    ) {
                        Text(
                            if (inBattle) "Resume the Fray" else "Close",
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = TapestryLight
                        )
                    }

                    if (canRetire) {
                        // A stitch line sets the irreversible rite apart from the reversible ones
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(TapestryDark.copy(alpha = 0.3f))
                        )
                        Button(
                            onClick = { if (retireArmed) onRetire() else retireArmed = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (retireArmed) TapestryRed else TapestryLinenCard
                            ),
                            border = androidx.compose.foundation.BorderStroke(2.dp, TapestryRed),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().testTag("retire_btn")
                        ) {
                            Text(
                                if (retireArmed) "Tap Again to Retire" else "Retire from the Field",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = if (retireArmed) TapestryLight else TapestryRed
                            )
                        }
                        Text(
                            "End the campaign with honour. Thy tale is still stitched, and may still be shared.",
                            fontFamily = FontFamily.Serif,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            color = TapestryDark.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun HeaderBar(
    uiState: BattleSimState,
    musicOn: Boolean,
    onToggleMusic: () -> Unit,
    playerState: FighterState? = null,
    /** Everyone fighting on your side who has a body on the field. Their stats are THEIRS. */
    allies: List<FighterState> = emptyList(),
    onTriggerWeather: (String) -> Unit = {},
    onOpenMenu: () -> Unit = {}
) {
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
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
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
                        // The panel used to stop at six lines, which left the numbers that decide
                        // most fights (damage split, attack speed, weight, block) unreadable
                        // anywhere in a running battle.
                        StatSectionHeader("THY OWN BODY", uiState.playerName.uppercase())
                        StatText("HP", "${p.hp.toInt()} / ${p.maxHp.toInt()}")
                        StatText("Damage / swing", "%.1f".format(p.baseDamage))
                        StatText("  · pierce / slash / blunt",
                            "%.0f / %.0f / %.0f".format(p.damagePierce, p.damageSlash, p.damageBlunt))
                        StatText("Attack Speed", "%.2f/s  (%.2fs delay)".format(1f / p.attackSpeedDelay, p.attackSpeedDelay))
                        StatText("DPS", "${"%.1f".format(p.baseDamage / p.attackSpeedDelay)}/s")
                        StatText("Armor", "${p.totalArmor.toInt()}${if (p.armorShred > 0f) "  (shred -${p.armorShred.toInt()})" else ""}")
                        StatText("Carried Weight", "%.1f kg".format(p.totalMass))
                        StatText("Move Speed", "${p.moveSpeed.toInt()} px/s")
                        StatText("Reach", "${"%.1f".format(p.reach)} m")
                        if (p.shield.id != "shield_none") {
                            StatText("Shield", "${(p.shield.blockChance * 100).toInt()}% block  ${p.shieldHp.toInt()} hp left")
                        }
                        StatText("Score Mult", "×${"%.1f".format(p.scoreMultiplier)}")

                        // Companions carry their OWN numbers. Listing them unlabelled beside the
                        // knight's read as if the Great Horse's 200 hp were somehow his.
                        val companions = allies.filter { !it.isDead && it.id != p.id }
                        if (companions.isNotEmpty()) {
                            StatSectionHeader("THY RETINUE", "these are THEIR stats, not thine")
                            companions.forEach { c ->
                                StatText(
                                    c.name,
                                    buildList {
                                        add("${c.hp.toInt()}/${c.maxHp.toInt()} hp")
                                        if (c.baseDamage > 0f) add("%.0f dmg".format(c.baseDamage))
                                        if (c.totalArmor > 0f) add("${c.totalArmor.toInt()} arm")
                                    }.joinToString("  ")
                                )
                            }
                        }
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
        
        // Center: Battle Name, with any held divine weathers pinned beside it like relic
        // medallions on the titulus band
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = FlavourText.battleName(MedievalHarpPlayer.gameSeed, uiState.level),
                // weight(fill = false) makes the title yield: in a Row, unweighted children are
                // measured FIRST at their intrinsic size, so the weather charges always get their
                // space and the title takes only what is left over. Without this a long battle
                // name simply grew and shoved the charges off the end of the band, where they
                // could be neither seen nor pressed — and they are the only way to spend a relic.
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = TapestryDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            uiState.divineWeathers.forEach { weather ->
                Spacer(modifier = Modifier.width(6.dp))
                WeatherCharge(
                    weather = weather,
                    cooldownFraction = (uiState.weatherCooldowns[weather.id] ?: 0f) / WEATHER_COOLDOWN_SECS,
                    onTrigger = { onTriggerWeather(weather.id) }
                )
            }
        }

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
            
            // Menu seal: a linen roundel. Two upright bars mid-battle, where it really is a pause;
            // three stacked bars between battles, where nothing is running and it is simply the
            // menu — it now holds trophies and the ad switch as well as the sound rites.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(TapestryLinenBg, androidx.compose.foundation.shape.CircleShape)
                    .border(2.dp, TapestryDark, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onOpenMenu() }
                    .testTag("open_pause_menu_btn"),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isBattleActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 11.dp)
                                    .background(TapestryDark, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 13.dp, height = 2.5.dp)
                                    .background(TapestryDark, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterPreviewCard(
    uiState: BattleSimState,
    onSelectMount: (com.example.game.Ancillary) -> Unit = {},
    onSelectThrone: () -> Unit = {},
    onClearMount: () -> Unit = {}
) {
    // The mount the player is actually riding into the next battle — effectiveMount, so the
    // preview cannot disagree with what startBattle will actually put underneath him.
    val previewMount: com.example.game.Ancillary? = uiState.effectiveMount

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
        // Uncapped: the glow used to stop growing at level 9 and every later level looked
        // identical. It keeps climbing now. Radius and colour ride the raw tier; the spark COUNT
        // is the only thing still capped, because sparks are draw calls per frame and a level-60
        // knight should not cost 400 of them.
        val auraTier = 1 + uiState.level / 3

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

                // Intense Holy Glow from behind. Radius grows without limit; alpha and heat
                // approach white asymptotically so a very high level saturates rather than
                // overflowing into a flat block of colour.
                val heat = 1f - 1f / (1f + auraTier * 0.25f)   // 0..1, never quite 1
                val coreColor = androidx.compose.ui.graphics.lerp(TapestryMustard, Color(0xFFFFFBE8), heat)
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = (0.6f + 0.2f * pulse + 0.15f * heat).coerceAtMost(0.95f)),
                            TapestryMustard.copy(alpha = 0.2f + 0.25f * heat),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY - 20f),
                        radius = 240f + 40f * auraTier + 30f * pulse
                    )
                )

                // Haloes: one more concentric ring every third tier, so the higher levels read as
                // escalating rank and not just a brighter smear.
                for (ring in 1..minOf(4, auraTier / 3)) {
                    drawCircle(
                        coreColor.copy(alpha = 0.10f + 0.05f * pulse),
                        radius = 120f + ring * 55f + 10f * pulse,
                        center = Offset(centerX, centerY - 20f),
                        style = Stroke(width = 2f + ring)
                    )
                }

                // Divine Sparks rising aggressively
                val sparkCount = (10 + 6 * auraTier).coerceAtMost(64)
                val sparkColor = androidx.compose.ui.graphics.lerp(Color(0xFFFFF1AA), Color.White, heat)
                for (i in 0 until sparkCount) {
                    val floatY = (centerY + 160f) - ((risePhase + i * 35f) % 350f)
                    val floatX = centerX + sin(swayPhase * 3f + i) * 110f
                    val r = 2f + (i % 4) + heat * 2f
                    drawCircle(sparkColor.copy(alpha = 0.9f), radius = r, center = Offset(floatX, floatY))
                    drawLine(sparkColor.copy(alpha = 0.4f), Offset(floatX, floatY + r), Offset(floatX, floatY + r + 15f + 20f * heat), strokeWidth = 1.5f + heat)
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
                            id = FighterId("front"), name = "Front", isPlayer = true,
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
                            faceNoseShape = uiState.faceNoseShape, faceBiteShape = uiState.faceBiteShape, faceForehead = uiState.faceForehead, faceMustache = uiState.faceMustache
                        )
                        val back = front.copy(id = FighterId("back"), name = "Back", posX = centerX - 45f, targetX = centerX - 45f)
                        val king = com.example.game.FighterState(
                            id = FighterId("king"), name = uiState.playerName, isPlayer = true, isLord = true, isMounted = true,
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
                            faceNoseShape = uiState.faceNoseShape, faceBiteShape = uiState.faceBiteShape, faceForehead = uiState.faceForehead, faceMustache = uiState.faceMustache
                        )
                        TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, king, scale = 1.35f)
                        TapestryRenderer.drawCharacter(this, back, scale = 1.35f, isBattleActive = true)
                        TapestryRenderer.drawCharacter(this, king, scale = 1.35f, isBattleActive = true)
                        TapestryRenderer.drawCharacter(this, front, scale = 1.35f, isBattleActive = true)
                    } else {
                        // Create dummy FighterState mirroring chosen gear
                        val dummyFighter = com.example.game.FighterState(
                            id = FighterId("preview"),
                            name = uiState.playerName,
                            isPlayer = true,
                            faceNoseShape = uiState.faceNoseShape,
                            faceBiteShape = uiState.faceBiteShape,
                            faceForehead = uiState.faceForehead,
                            faceMustache = uiState.faceMustache,
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
                            // Show the mount you actually chose. This used to key off what was
                            // *unlocked*, so switching mounts changed nothing on the preview.
                            isMounted = previewMount != null || uiState.isThroneMode,
                            isChariot = previewMount == com.example.game.Ancillary.CHARIOT,
                            isStilts = previewMount == com.example.game.Ancillary.STILTS,
                            isOx = previewMount == com.example.game.Ancillary.WAR_OX,
                            isMule = previewMount == com.example.game.Ancillary.PACK_MULE,
                            isBear = previewMount == com.example.game.Ancillary.WAR_BEAR,
                            isLord = uiState.isThroneMode,
                            mountHp = when {
                                uiState.isThroneMode -> 100f
                                previewMount == com.example.game.Ancillary.WARHORSE -> 80f
                                previewMount == com.example.game.Ancillary.CHARIOT -> 120f
                                previewMount == com.example.game.Ancillary.STILTS -> 40f
                                previewMount == com.example.game.Ancillary.WAR_OX -> 140f
                                previewMount == com.example.game.Ancillary.PACK_MULE -> 40f
                                previewMount == com.example.game.Ancillary.WAR_BEAR -> 100f
                                else -> 0f
                            }
                        )
                        TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, dummyFighter, scale = 1.35f)
                        TapestryRenderer.drawCharacter(this, dummyFighter, scale = 1.35f, isBattleActive = false)
                    }
                }
            }
        }

        // Mount picklist, directly under the man it changes. The preview box above takes weight(1f),
        // so it simply gives up the height this needs — no squashing anything else on the screen.
        // distinct(): a Thrice-Blessed mount stacks stats, but one Blanche is one picklist row.
        val mounts = uiState.unlockedAncillaries.filter { it.id.startsWith("anc_mount_") }.distinct()
        // The throne is a mount too, but only once it has actually been taken. hasThroneOption is
        // just the 20% offer roll at character creation — listing it here put a throne in the
        // picklist for players who never accepted one.
        val throneAvailable = uiState.hasTakenThrone
        // Shown for even ONE mount. It used to need two before it appeared, so a player with a
        // single unlocked mount was permanently astride it with no control on screen at all.
        if (mounts.isNotEmpty() || throneAvailable) {
            val activeMount = previewMount
            Text(
                text = "MOUNT",
                fontSize = 8.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = TapestryDark.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 3.dp),
                textAlign = TextAlign.Center
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // On foot, always first and always available — the way to decline a mount.
                run {
                    val isSelected = activeMount == null && !uiState.isThroneMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                if (isSelected) TapestryMustard.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) TapestryRed else TapestryDark.copy(alpha = 0.35f),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onClearMount() }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isSelected) "▸" else " ", fontSize = 9.sp, color = TapestryRed, modifier = Modifier.width(10.dp))
                        Text(
                            text = "On Foot",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            color = TapestryDark,
                            maxLines = 1
                        )
                    }
                }
                if (throneAvailable) {
                    val isSelected = uiState.isThroneMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                if (isSelected) TapestryMustard.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) TapestryRed else TapestryDark.copy(alpha = 0.35f),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onSelectThrone() }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isSelected) "▸" else " ", fontSize = 9.sp, color = TapestryRed, modifier = Modifier.width(10.dp))
                        Text(
                            text = "The Throne",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            color = TapestryDark,
                            maxLines = 1
                        )
                    }
                }
                mounts.forEach { mount ->
                    val isSelected = mount == activeMount && !uiState.isThroneMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                if (isSelected) TapestryMustard.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) TapestryRed else TapestryDark.copy(alpha = 0.35f),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onSelectMount(mount) }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) "▸" else " ",
                            fontSize = 9.sp,
                            color = TapestryRed,
                            modifier = Modifier.width(10.dp)
                        )
                        Text(
                            text = mount.ancillaryName,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            color = TapestryDark,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Square. A rounded pane has to have its fill clipped to the curve or a wedge of colour escapes
 * past the border at every corner, and it kept coming back; a square panel cannot have the bug at
 * all, and an embroidered panel was never rounded in the first place.
 */
private val RewardTileShape = androidx.compose.ui.graphics.RectangleShape

/**
 * The narrowest a tile may be squeezed before the row starts scrolling instead. Low enough that the
 * usual hand — three spoils and decline — still divides the width without a scrollbar.
 */
private val REWARD_TILE_MIN_WIDTH = 190.dp

/** The kind's wool, its ink, and what to call it. */
private fun rewardPalette(type: String): Triple<Color, Color, String> = when (type) {
    "follower" -> Triple(Color(0xFFE3F2FD), TapestryBlue, "Entourage")
    "attachment" -> Triple(Color(0xFFFFEBEE), TapestryRed, "Weapon Head")
    "extension" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Haft Upgrade")
    "armor" -> Triple(Color(0xFFFFF8E1), Color(0xFF8D6E63), "Layered Armor")
    "comedy" -> Triple(Color(0xFFFFE0E0), Color(0xFFC62828), "Comedy Gear")
    else -> Triple(Color(0xFFF5F5F5), TapestryDark, "Upgrade")
}

/** A multiple arrives as a sigil, not as the word TWINS shouted in the title and again in the body. */
private fun multipleSigil(choice: LevelUpChoice): String? = when {
    choice.type == "follower_multiply" -> "×3"
    choice.copies > 1 -> "+${choice.copies}"
    else -> null
}

/**
 * One spoil. Sized by its column, never by a fixed width: at 285dp the third card was cropped at
 * the panel edge and the hand could only be read by scrolling, which is the whole complaint.
 */
@Composable
private fun RewardTile(choice: LevelUpChoice, onSelect: (String) -> Unit) {
    val (bannerColor, titleColor, tagLabel) = rewardPalette(choice.type)
    val impact = buildImpactFor(choice)
    Box(
        // clip BEFORE background: Card filled its container as a rectangle and only stroked the
        // rounded border over it, so a wedge of the pale fill escaped past the curve at every
        // corner. Clipping to the shape first means nothing can sit outside it.
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RewardTileShape)
            .background(bannerColor)
            .border(2.dp, titleColor, RewardTileShape)
            .clickable { onSelect(choice.id) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                multipleSigil(choice)?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier
                            .background(TapestryRed, RoundedCornerShape(4.dp))
                            .border(1.dp, TapestryDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
                // Keeps the tag optically centred whether or not a badge is present
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                choice.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 18.sp,
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
            // What this actually does to the build, in the same numbers the simulation uses.
            if (impact.isNotEmpty()) {
                Text(
                    impact,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier.size(36.dp).background(titleColor, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚔", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Declining is a real choice and keeps a tile of its own beside the spoils — narrower, and in
 * undyed linen rather than a kind's wool, so it reads as the option to take none of them.
 */
@Composable
private fun DeclineSpoils(onSkip: () -> Unit) {
    val ink = TapestryDark.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RewardTileShape)
            .background(Color(0xFFF2EEE4))
            .border(2.dp, ink, RewardTileShape)
            .clickable { onSkip() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No Reward",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TapestryDark.copy(alpha = 0.6f),
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                "Decline all spoils",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = TapestryDark,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            Text(
                "Take nothing, and be paid in score instead.",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = TapestryDark.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier.size(30.dp).background(ink, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✗", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun LevelUpScreen(
    uiState: BattleSimState,
    onSelectChoice: (String) -> Unit,
    onSelectMount: (com.example.game.Ancillary) -> Unit = {},
    onStartBattle: () -> Unit = {},
    onSkipReward: () -> Unit = {},
    onClearSkipBonus: () -> Unit = {},
    /** Whether the boon offer can be shown. Decided by the caller — see MainActivity. */
    boonOfferAvailable: Boolean = false,
    /** True for players who bought the game ad-free: the boon is claimed outright, no ad. */
    boonIsFree: Boolean = false,
    onClaimBoon: () -> Unit = {},
    onClearBoonBanner: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(3.dp, TapestryDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Top, not Center: centring left the header floating low with dead space above it, which
        // pushed the tiles down and cost them height they needed.
        verticalArrangement = Arrangement.Top
    ) {
        // Header row. A landscape phone is about 360dp tall inside this border and the reward
        // cards need nearly all of it, so the title, the subtitle and the ad offer share ONE row
        // and spend width — which landscape has to spare — instead of three stacked bands of
        // height it does not. Stacked, the ad button alone cost ~50dp and clipped the card text.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Victory!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TapestryRed,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    "Select thy spoils of war:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TapestryDark.copy(alpha = 0.85f)
                )
            }
            // Opt-in only, and only when the boon can actually be delivered — an ad-funded offer
            // needs a loaded ad; an ad-free player needs neither. Absent in debug.
            if (boonOfferAvailable) {
                Button(
                    onClick = onClaimBoon,
                    colors = ButtonDefaults.buttonColors(containerColor = TapestryMustard),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.widthIn(max = 190.dp).testTag("boon_offer")
                ) {
                    // Names what arrives, not what it costs, and never promises which boon lands.
                    Text(
                        if (boonIsFree) "Claim a herald's favour\n(yours, ad-free)"
                        else "Hear a herald's message\nfor a favour",
                        color = TapestryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

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
                // Every tile the same width, decline included. They share the row when they fit,
                // and the row scrolls once there are too many to — a hand can be more than four.
                BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
                    val tiles = uiState.pendingLevelUpChoices.size + 1
                    val gaps = 8.dp * (tiles - 1)
                    // Floored to whole dp: an exact division rounds up by a fraction, and the row
                    // came out a pixel wider than the box — enough to scroll and show a scrollbar
                    // on a hand that actually fits.
                    val tileWidth = ((maxWidth - gaps).value / tiles).toInt().dp
                        .coerceAtLeast(REWARD_TILE_MIN_WIDTH)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.pendingLevelUpChoices.forEach { choice ->
                            Box(modifier = Modifier.width(tileWidth).fillMaxHeight()) {
                                RewardTile(choice = choice, onSelect = onSelectChoice)
                            }
                        }
                        Box(modifier = Modifier.width(tileWidth).fillMaxHeight()) {
                            DeclineSpoils(onSkip = onSkipReward)
                        }
                    }
                }

                // The scrollbar, which only appears when there is in fact more row than screen.
                if (scrollState.maxValue > 0) {
                    val scrollPercent = scrollState.value.toFloat() / scrollState.maxValue
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(200.dp)
                            .height(6.dp)
                            .background(TapestryDark.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
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

                // Which boon landed. A herald's favour is a random draw, so saying only "claimed"
                // would leave the player guessing what changed — especially for Vigour and the
                // Retinue, which alter nothing they can see on this screen.
                uiState.lastBoon?.let { boon ->
                    LaunchedEffect(boon) {
                        kotlinx.coroutines.delay(4000)
                        onClearBoonBanner()
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(TapestryMustard, RoundedCornerShape(8.dp))
                            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("boon_banner"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            boon.headline,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = TapestryDark
                        )
                        Text(
                            boon.detail,
                            fontSize = 11.sp,
                            color = TapestryDark.copy(alpha = 0.8f)
                        )
                    }
                }

                // The credit for declining last battle's spoils. It used to read "+1750 GLORY!" in
                // red — a currency the game does not have, never named as the score in the header,
                // and appearing a screen after the decision with nothing to say why. It now names
                // the number, the unit, and what earned it.
                if (uiState.pendingSkipBonus > 0) {
                    LaunchedEffect(uiState.pendingSkipBonus) {
                        kotlinx.coroutines.delay(4000)
                        onClearSkipBonus()
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(TapestryLinenCard)
                            .border(2.dp, TapestryDark)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("skip_bonus_notice"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "+${"%,d".format(uiState.pendingSkipBonus)}",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = TapestryRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "SCORE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TapestryRed.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "for declining last battle's spoils",
                            fontSize = 11.sp,
                            color = TapestryDark.copy(alpha = 0.8f)
                        )
                    }
                }

            }
        }

        // Mount selection lives under the character preview now — see CharacterPreviewCard.
        // It used to sit here as a row of 80dp cards, which squashed the rest of the screen.
    }
}

/**
 * A ruled band naming WHOSE stats follow. The old panel was one undivided list, so a companion's
 * numbers beside the knight's read as the knight's own.
 */
@Composable
private fun StatSectionHeader(title: String, note: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(TapestryDark.copy(alpha = 0.5f)))
        Text(
            title,
            fontSize = 10.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            color = TapestryRed,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            note,
            fontSize = 8.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = TapestryDark.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun GearSelectionTabs(
    uiState: BattleSimState, 
    onSelect: (GearItem) -> Unit, 
    onUpdatePhysical: (Float, Color, String) -> Unit,
    onToggleDualWield: () -> Unit,
    onToggleThroneMode: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    // Whatever the gloss register is currently showing. Null falls back to the selected item, so
    // the band is never blank and long-press is only ever a shortcut.
    var glossItem by remember { mutableStateOf<GearItem?>(null) }
    /** Same idea for the body-size row on the Physical tab. Null falls back to the chosen size. */
    var sizeGloss by remember { mutableStateOf<com.example.game.SizePreset?>(null) }
    // No emoji. The helm glyph rendered as a tofu box on the device font, shield and armour used
    // the SAME emoji so it carried no information, and the five labels plus the Dual Wield chip
    // did not fit the 475dp this panel actually gets — "Physical" was clipped. Words only.
    val tabTitles = listOf("WEAPON", "SHIELD", "ARMOUR", "HELM", "PHYSICAL")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TapestryLinenCard)
            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {

        // Tab band. Dual Wield rides at the right-hand end of it rather than in a band of its
        // own: it is a property of the weapon, so it belongs beside the tabs it modifies, and
        // reusing this row costs the grid nothing. It kept a full 44dp strip and a sentence of
        // consequence text permanently on screen for one boolean.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                tabTitles.forEachIndexed { index, title ->
                    val active = selectedTab == index
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .padding(horizontal = 2.dp)
                            .background(
                                if (active) TapestryDark else Color(0xFFDCD2B8),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                            .clickable {
                                selectedTab = index
                                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                            }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (active) TapestryLight else TapestryDark,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            maxLines = 1
                        )
                    }
                }
            }
            if (selectedTab == 0) {
                val dual = uiState.isDualWielding
                // State is spelled out, not implied by a tick: it changes how combat works, so
                // it has to be legible at a glance and not merely on inspection.
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(
                            if (dual) TapestryRed else Color(0xFFDCD2B8),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                        .border(
                            1.dp,
                            if (dual) TapestryRed else TapestryDark.copy(alpha = 0.5f),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                        .clickable { onToggleDualWield() }
                        .padding(horizontal = 10.dp)
                        .testTag("toggle_dual_wield_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (dual) "⚔ DUAL · ON" else "⚔ DUAL · OFF",
                        color = if (dual) TapestryLight else TapestryDark,
                        fontSize = 9.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }

        // The consequence, on the same shared band as everything else's detail.
        if (selectedTab == 0 && uiState.isDualWielding) {
            Text(
                "Two blades, no shield. Faster strikes, and one swing in five goes wide.",
                fontSize = 8.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = TapestryRed,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

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
                        // IntrinsicSize.Min measures the tallest tile first and hands that height
                        // to all of them. Without it each box sizes to its own label, and the ones
                        // whose name wraps to two lines stand taller than the rest — five buttons
                        // that should read as one row of equals, again.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(IntrinsicSize.Min)
                        ) {
                            com.example.game.SIZE_PRESETS.forEach { preset ->
                                val sizeVal = preset.size
                                val label = preset.label
                                val isSelected = uiState.characterSize == sizeVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(if (isSelected) TapestryDark else Color(0xFFFAF6EB), RoundedCornerShape(4.dp))
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) TapestryMustard else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .combinedClickable(
                                            onClick = {
                                                sizeGloss = preset
                                                onUpdatePhysical(sizeVal, uiState.hairColor, uiState.hairStyle)
                                            },
                                            onLongClick = { sizeGloss = preset }
                                        )
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Name only. The descriptions are different lengths, so
                                    // putting them inside made every box a different height —
                                    // five buttons that should read as one row of equals.
                                    Text(
                                        label,
                                        color = if (isSelected) TapestryLight else TapestryDark,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        // The same gloss register the weapon grid uses, for the same reason: the
                        // detail lives once, below, instead of inside every tile.
                        val glossedSize = sizeGloss ?: com.example.game.SIZE_PRESETS
                            .firstOrNull { it.size == uiState.characterSize }
                        if (glossedSize != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TapestryLinenBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, TapestryDark.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.Top   // see the weapon gloss below
                            ) {
                                Text(
                                    glossedSize.label.uppercase(),
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    color = TapestryRed
                                )
                                Text(
                                    "  ·  ${glossedSize.description}",
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = TapestryDark.copy(alpha = 0.85f),
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // Hair Color Selection
                    Column {
                        Text("HAIR COLOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TapestryDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val colors = com.example.game.HAIR_COLORS
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
                        // The three base cuts, plus any earned by a milestone. Earned styles are
                        // saved in the profile's item set like gear, so they filter the same way.
                        val earnedStyles = listOf(
                            "hair_tonsure_norman" to "✦ Norman Crop",
                            "hair_braids" to "✦ Braids",
                            "hair_tonsure_monk" to "✦ Tonsure",
                            "hair_topknot" to "✦ Topknot",
                            "hair_mystic" to "✦ Mystic",
                            "hair_germanic" to "✦ Germanic",
                            "hair_samson" to "✦ Samsonite"
                        ).filter { (id, _) -> id in uiState.unlockedGearIds }
                        val allStyles = listOf(
                            "short" to "Bowl Cut", "long" to "Long Locks", "bald" to "Bald/Fringe"
                        ) + earnedStyles
                        // Chunked: seven cuts in one weighted Row squeezes every label to nothing.
                        allStyles.chunked(3).forEach { rowStyles ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).height(IntrinsicSize.Min)
                            ) {
                                rowStyles.forEach { (styleVal, label) ->
                                    val isSelected = uiState.hairStyle == styleVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (isSelected) TapestryDark else Color(0xFFFAF6EB), RoundedCornerShape(4.dp))
                                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) TapestryMustard else TapestryDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .clickable { onUpdatePhysical(uiState.characterSize, uiState.hairColor, styleVal) }
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // A third of a phone's width is ~100dp; "✦ Norman Crop" at
                                        // 11sp is wider than that, and with nothing bounding it the
                                        // label ran out of its tile. It wraps to two lines now, and
                                        // IntrinsicSize.Min above keeps the row level when it does.
                                        Text(
                                            label,
                                            color = if (isSelected) TapestryLight else TapestryDark,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                                // Keep the last row's cells the same width as a full row's.
                                repeat(3 - rowStyles.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                        // What the cut actually does. The same gloss register the weapon and size
                        // grids use, reading straight off HAIR_TRAITS so the sentence and the
                        // numbers can never disagree.
                        val hairTrait = com.example.game.hairTrait(uiState.hairStyle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TapestryLinenBg, RoundedCornerShape(4.dp))
                                .border(1.dp, TapestryDark.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val deltas = buildList {
                                if (hairTrait.hpBonus != 0f) add("${if (hairTrait.hpBonus > 0) "+" else ""}${hairTrait.hpBonus.toInt()} HP")
                                if (hairTrait.speedBonus != 0f) add("${if (hairTrait.speedBonus > 0) "+" else ""}${(hairTrait.speedBonus * 100).toInt()}% SPEED")
                            }
                            Text(
                                if (deltas.isEmpty()) "NO EFFECT" else deltas.joinToString("  "),
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Black,
                                color = TapestryRed
                            )
                            Text(
                                "  ·  ${hairTrait.effect}",
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                fontFamily = FontFamily.Serif,
                                color = TapestryDark.copy(alpha = 0.85f),
                                maxLines = 2
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

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
                    // weight, not fillMaxSize: the grid must yield the gloss register its strip
                    // rather than consuming the column and pushing it off the bottom.
                    modifier = Modifier.fillMaxWidth().weight(1f)
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
                            onHold = { glossItem = item },
                            onClick = {
                                onSelect(item)
                                glossItem = item
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

                // The gloss register. In the tapestry itself the narrow border below a scene is
                // where the commentary on it runs, so that is where a piece's description lives
                // now — once, shared, instead of repeated inside all forty tiles.
                //
                // It shows the SELECTED item by default and never sits empty, so holding is a
                // shortcut for reading about something you have not chosen yet, not the only way
                // to find out what anything does. A hidden gesture must never carry primary
                // meaning; long-press here only ever previews.
                val glossed: GearItem? = glossItem ?: when (selectedTab) {
                    0 -> uiState.weaponHead
                    1 -> uiState.shield
                    2 -> uiState.armor
                    else -> uiState.headgear
                }
                if (glossed != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .background(TapestryLinenBg, RoundedCornerShape(4.dp))
                            .border(1.dp, TapestryDark.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        // Top, not centre: the description runs to two lines and the name to one,
                        // so centring pushed the "·" down to the midpoint of the taller block and
                        // left it sitting below the name it separates. Matching lineHeight lines
                        // the two first lines up exactly.
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = glossed.itemName.uppercase(),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = TapestryRed
                        )
                        Text(
                            text = "  ·  ${glossed.description}",
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontFamily = FontFamily.Serif,
                            color = TapestryDark.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                } // End Column
            }
        }

        // Handle attachment selection: sits under the head grid in the SAME menu, and stays put
        // whatever head is currently picked. Hiding it while bare-handed made it pop into
        // existence only after a head was chosen, which read as a second, glitchy step.
        // Ranged heads are still the one exception — a longer shaft does nothing for a bow.
        if (selectedTab == 0 && uiState.weaponHead.id !in listOf("head_bow", "head_longbow", "head_slingshot")) {
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
                            // ✦ now means earned: these are only in the list at all if a milestone granted them.
                            text = if (handle.id in GameData.UNLOCKABLE_HANDLE_IDS) "✦ ${handle.itemName}" else handle.itemName,
                            color = if (isHandleSelected) TapestryLight else TapestryDark,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Only worth a legend once the player actually holds one of them.
            if (GameData.UNLOCKABLE_HANDLE_IDS.any { it in uiState.unlockedGearIds }) {
                Text(
                    "✦ earned by deeds, and yours for good",
                    color = TapestryDark.copy(alpha = 0.6f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Earned and locked milestones. A locked row always states its condition — a trophy nobody can work
 * out how to earn is not progression, it is a locked door.
 */
@Composable
fun TrophiesPanel(uiState: BattleSimState) {
    val earnedCount = Milestone.values().count { it.id in uiState.clearedMilestones }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Its own linen, as every sibling panel has. Without it the case drew straight onto
            // the dark backdrop and locked entries — deliberately dimmed to 45% — were illegible.
            .background(TapestryLinenCard, RoundedCornerShape(8.dp))
            .border(2.dp, TapestryDark, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            "TROPHIES  $earnedCount/${Milestone.values().size}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = TapestryDark,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // Three abreast. A single column of twenty-two used about a seventh of an 850dp-wide
        // landscape screen and made the case scroll for no reason; three columns fit almost the
        // whole thing at once, which is what a trophy case is for.
        Milestone.values().toList().chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        row.forEach { m ->
            val earned = m.id in uiState.clearedMilestones
            val reward = GameData.WEAPON_HEADS.find { it.id == m.grants }?.itemName
                ?: GameData.WEAPON_HANDLES.find { it.id == m.grants }?.itemName
                ?: GameData.ARMOR_PIECES.find { it.id == m.grants }?.itemName
                ?: GameData.HEADGEAR_PIECES.find { it.id == m.grants }?.itemName
                ?: m.grants
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (earned) Color(0xFFFAF6EB) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .padding(vertical = 3.dp, horizontal = 4.dp)
            ) {
                Text(
                    if (earned) "✦" else "·",
                    fontSize = 10.sp,
                    color = if (earned) TapestryMustard else TapestryDark.copy(alpha = 0.4f),
                    modifier = Modifier.width(16.dp)
                )
                Column {
                    Text(
                        m.label,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (earned) TapestryDark else TapestryDark.copy(alpha = 0.45f)
                    )
                    Text(
                        if (earned) "won: $reward" else m.condition,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = TapestryDark.copy(alpha = 0.7f)
                    )
                }
            }
        }
        // Keep the last row's cells the same width as a full row's.
        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun GearItemCell(item: GearItem, isSelected: Boolean, onClick: () -> Unit, onHold: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) TapestryLinenBg else Color(0xFFFAF6EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            // 72dp for ~28dp of content: every tile carried two lines of flavour text and a dead
            // gap, and only two and a half rows fitted on a landscape phone. The description now
            // lives once, in the gloss register below the grid, so a tile is just its name, its
            // weight and what it does. 38dp keeps a comfortable touch target.
            //
            // heightIn, not height: the tile is sized in dp but its contents are in sp, which
            // grows with the phone's font-size setting. At a fixed 38dp anything past the first
            // line was clipped away, which is why the S/P/B/RCH row vanished on real devices
            // while the weight badge (same row as the name) survived.
            .heightIn(min = 38.dp)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) TapestryRed else TapestryDark,
                shape = RoundedCornerShape(6.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onHold)
            .testTag("gear_${item.id}"),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 2.dp),
            // Packed, not SpaceBetween: pushing the stat row to the far edge left a dead gap
            // above it and clipped the descenders on "P" and "RCH" against the border.
            verticalArrangement = Arrangement.spacedBy(1.dp)
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
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

            // Combat stats so you can see what a piece actually does, not just its flavour.
            val stats = buildList {
                if (item.pierce > 0f) add("P${item.pierce.toInt()}")
                if (item.slash > 0f) add("S${item.slash.toInt()}")
                if (item.blunt > 0f) add("B${item.blunt.toInt()}")
                // Shields don't add armour — they roll to block, by weight, and wear down per block
                if (item is com.example.game.GameData.Shield && item.defense > 0f) {
                    add("BLK${(item.blockChance * 100).toInt()}%")
                    add("HP${(item.defense * 2f).toInt()}")
                } else if (item.defense > 0f) add("DEF${item.defense.toInt()}")
                if (!item.isRanged && item.reach > 0f) add("RCH${"%.1f".format(item.reach)}")
                if (item.isRanged) add("RANGED")
            }.joinToString("  ")
            // Always rendered, even when empty. The "none/bare" options have no stats at all and
            // are always sorted first, so dropping the row left exactly the first tile in every
            // list a line shorter than its neighbours — the two-thirds-height cell. A dash also
            // states the baseline honestly instead of leaving a blank.
            Text(
                text = stats.ifEmpty { "—" },
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (stats.isEmpty()) TapestryDark.copy(alpha = 0.45f) else TapestryRed,
                maxLines = 1
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
            id = FighterId("stat_dummy"),
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
            extraArmors = uiState.extraArmors.mapNotNull { id -> com.example.game.GameData.ARMOR_PIECES.find { it.id == id } },
            headgear = if (uiState.isThroneMode) com.example.game.GameData.HEADGEAR_PIECES.first { it.id == "helm_crown" } else uiState.headgear,
            isDualWielding = uiState.isDualWielding,
            posX = 0f, targetX = 0f,
            isMounted = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT) || uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS) || uiState.isThroneMode,
            mountHp = if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.WARHORSE)) 80f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT)) 120f else if (uiState.unlockedAncillaries.contains(com.example.game.Ancillary.STILTS)) 40f else 0f,
            isChariot = uiState.unlockedAncillaries.contains(com.example.game.Ancillary.CHARIOT),
            isLord = uiState.isThroneMode,
            hasSilkenGarments = uiState.hasSilkenGarments
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
            // No version here: versionName already carries its own "v" (so this read "vv0.8"),
            // and the header bar states it once beside the level, which is enough.
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
                fraction = (dummyFighter.reach / 6f).coerceIn(0f, 1f),
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
        val borderSeed = remember(uiState.level) { MedievalHarpPlayer.gameSeed * 7L + uiState.level }
        val fixedBackdropCache = remember { TapestryBackdropCache() }
        val worldBackdropCache = remember { TapestryBackdropCache() }
        // Press and hold a fighter to read his health. Bars tell you a boss is "nearly dead" for
        // about a minute; the number tells you whether that is true.
        //
        // Holding now TOGGLES the read-out open and it stays open, because a stat line you can
        // only see while your thumb is covering the man you are reading about is not much use.
        // The next tap anywhere closes it, and it is cleared whenever the battle ends so it can
        // never be left hanging over the victory screen.
        val inspectAt = remember { mutableStateOf<Offset?>(null) }
        val battleOver = uiState.battleWon || uiState.battleLost || !uiState.isBattleActive
        LaunchedEffect(battleOver) { if (battleOver) inspectAt.value = null }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("bayeux_tapestry_canvas")
                .pointerInput(Unit) {
                    detectTapGestures(
                        // A tap while a tag is open dismisses it, wherever it lands.
                        onTap = { if (inspectAt.value != null) inspectAt.value = null },
                        onLongPress = { at -> inspectAt.value = at },
                        onPress = { at ->
                            // Keep the press-and-hold feel: the tag appears under the finger at
                            // once, and only survives the release if the hold was a long one.
                            val hadTag = inspectAt.value != null
                            if (!hadTag) inspectAt.value = at
                            tryAwaitRelease()
                        }
                    )
                }
        ) {
            // Force redraw on tick
            val currentTick = tick
            val playerFighter = viewModel.playerState.value
            val enemies = viewModel.enemiesState.value
            val projectiles = viewModel.projectilesState.value
            val popups = viewModel.popupsState.value
            val shakeAmt = shake
            val playerScaleX = size.width / 1000f
            // Pull the camera back only when the player needs the headroom — a big fighter, a mount,
            // or stilts. A normal-sized man on foot sees exactly what he always did.
            val worldZoom = when {
                playerFighter == null -> 1f
                playerFighter.isStilts -> 0.80f
                playerFighter.isChariot || playerFighter.isMounted -> 0.88f
                playerFighter.size > 1.2f -> 0.90f
                else -> 1f
            }
            val scaleFactor = (size.height / 350f) * worldZoom
            
            val scaledCameraX = uiState.cameraX * playerScaleX
            val offsetX = (if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f) - scaledCameraX
            val offsetY = if (shakeAmt > 0f) (kotlin.random.Random.nextFloat() * shakeAmt * 2f - shakeAmt) else 0f

            // Anchor the ground to the bottom border: fighter feet (200 + 158*scale in this
            // block's coordinates) land 45px above the border — room for bodies/blood pools,
            // no dead space.
            val groundOffsetY = (size.height - 40f - 45f) - (200f + 158f * scaleFactor)
            // Vertical camera follow: as the player climbs a hill (negative terrainLiftY) the whole
            // world shifts down by the same amount, so he never walks up out of frame.
            val cameraLiftY = -(playerFighter?.terrainLiftY ?: 0f) * scaleFactor

            val canvasWidth = size.width.toInt().coerceAtLeast(1)
            val canvasHeight = size.height.toInt().coerceAtLeast(1)
            val fixedBackdrop = fixedBackdropCache.bitmapFor(
                BackdropKey(uiState.level, borderSeed, canvasWidth, canvasHeight)
            ) {
                drawRect(TapestryLinenBg)
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
                drawTapestryBorder(this, isTop = true, textHeadline = "", motifSeed = borderSeed)
                drawTapestryBorder(this, isTop = false, textHeadline = "", motifSeed = borderSeed + 1)
            }
            drawImage(fixedBackdrop)

            // Keep the scrolling scenery in world coordinates while caching its expensive
            // embroidery. The fixed linen and borders above remain screen-anchored as before.
            val worldPadding = 600f
            val worldWidth = kotlin.math.ceil(uiState.levelWidth * playerScaleX + worldPadding * 2f)
                .toInt()
                .coerceAtLeast(canvasWidth)
            val staticBackgroundObjects = uiState.backgroundObjects.filterNot {
                it.type.isLiveBackgroundObject()
            }
            val backgroundVisualSeed = com.example.game.staticBackgroundVisualSeed(
                borderSeed,
                staticBackgroundObjects,
                worldZoom
            )
            // A hill shifts this bitmap DOWN the screen by up to MAX_LIFT, which drags whatever the
            // bitmap clipped at its own top edge into view — tall trees ended in a flat horizontal
            // cut halfway up a hill battle. Render with that much headroom and blit it back up.
            val worldHeadroom = if (uiState.hillState != null)
                kotlin.math.ceil(com.example.game.HillField.MAX_LIFT * scaleFactor).toInt() else 0
            val worldBackdrop = worldBackdropCache.bitmapFor(
                BackdropKey(uiState.level, backgroundVisualSeed, worldWidth, canvasHeight + worldHeadroom)
            ) {
                withTransform({
                    translate(left = worldPadding, top = groundOffsetY + worldHeadroom)
                }) {
                    staticBackgroundObjects.forEach { bg ->
                        com.example.game.drawStaticBackgroundObject(
                            this,
                            bg,
                            bg.posX * playerScaleX,
                            scaleFactor
                        )
                    }
                }
            }
            withTransform({
                clipRect(top = 40f, bottom = size.height - 40f)
                translate(left = offsetX - worldPadding, top = offsetY + cameraLiftY - worldHeadroom)
            }) {
                drawImage(worldBackdrop)
            }

            withTransform({
                // Keep the game world inside the embroidered borders (40px bands)
                clipRect(top = 40f, bottom = size.height - 40f)
                translate(left = offsetX, top = offsetY + groundOffsetY + cameraLiftY)
            }) {
                // Hill slope sits behind all scenery and fighters, matching their lifted feet.
                uiState.hillState?.let { hill ->
                    com.example.game.drawHillTerrain(this, hill, uiState.levelWidth, playerScaleX, scaleFactor)
                }
                staticBackgroundObjects.forEach { bg ->
                    com.example.game.drawBackgroundDamageDecals(
                        this,
                        bg,
                        bg.posX * playerScaleX,
                        scaleFactor
                    )
                }

                uiState.backgroundObjects
                    .firstOrNull { it.type == BackgroundObjectType.CASTLE_GATE }
                    ?.let { gate ->
                        val siege = uiState.siegeState
                        com.example.game.drawLiveCastleGate(
                            this,
                            gate,
                            gate.posX * playerScaleX,
                            scaleFactor,
                            siege?.gateHp ?: gate.hp,
                            siege?.gateMaxHp ?: gate.maxHp
                        )
                        if (siege != null && com.example.game.SiegeRules.ladderStanding(siege)) {
                            com.example.game.drawSiegeLadder(
                                this,
                                gate.posX * playerScaleX,
                                scaleFactor
                            )
                        }
                    }

                // The great decoy is enormous. Draw it before the player so it stands behind him
                // instead of hiding him completely.
                enemies.filter { it.isKind("trojan_horse") }.forEach { th ->
                    TapestryRenderer.drawCharacter(
                        this,
                        th.copy(posX = th.posX * playerScaleX),
                        scale = scaleFactor,
                        isBattleActive = true
                    )
                }

                // 3. Draw Players and Enemies
                if (playerFighter != null) {
                val (rearThroneActors, foregroundActors) =
                    com.example.game.splitThroneBattleActors(enemies)
                fun isVisible(fighter: FighterState): Boolean {
                    val screenX = fighter.posX * playerScaleX + offsetX
                    val margin = com.example.game.fighterCullMargin(fighter, scaleFactor)
                    return screenX >= -margin && screenX <= size.width + margin
                }

                // Match the throne preview: rear bearers, throne/lord, then front bearers.
                rearThroneActors.filter(::isVisible).forEach { bearer ->
                    TapestryRenderer.drawCharacter(
                        this,
                        bearer.copy(posX = bearer.posX * playerScaleX),
                        scale = scaleFactor
                    )
                }

                // Draw Knight
                val scaledPlayer = playerFighter.copy(
                    posX = playerFighter.posX * playerScaleX
                )
                // Ancillaries draw behind the player (matches customization/throne previews) so
                // Lil Guy's backpack reads as attached to the knight's back, not floating in front of him
                TapestryRenderer.drawAncillaries(this, uiState.unlockedAncillaries, scaledPlayer, scale = scaleFactor)
                // recordInto: the copy above is a throwaway, and the flame/smoke positions the
                // renderer works out have to land on the fighter the simulation actually ticks.
                TapestryRenderer.drawCharacter(this, scaledPlayer, scale = scaleFactor, recordInto = playerFighter)

                // Draw health bar for Player
                val px = scaledPlayer.posX
                val headDist = if (scaledPlayer.isMounted && !scaledPlayer.isChariot) 70f else if (scaledPlayer.isChariot) 50f else 40f
                // Clamp so the bar never rises above the top border clip (tall fighters/mounts)
                val py = (200f - (headDist + 35f) * scaledPlayer.size * scaleFactor +
                    com.example.game.elevationVisualOffset(playerFighter) * scaleFactor)
                    .coerceAtLeast(55f - groundOffsetY)
                drawHealthBar(this, px, py, playerFighter.hp, playerFighter.ghostHp, playerFighter.maxHp)
                // The beast under you has its own health and losing it is a real event, but the
                // only sign of it was MOUNT SHATTERED! after the fact. Player only — a second bar
                // over every rider in the host would be noise.
                val mountBarY = drawMountHealthBar(this, px, py, playerFighter)
                drawStatusEffects(this, px, mountBarY - 10f, playerFighter)

                // Draw Enemies (the trojan horse already went in behind the player)
                // Cull anything the camera can't see BEFORE the copy: drawCharacter allocates ~80
                // Paths per fighter per frame, and FighterState.copy() clones a fat data class 30
                // times a second each. Off-screen draws are invisible by definition, so skipping
                // them changes nothing on screen — it just stops paying for it. The margin is
                // generous so mounts/tall sprites never pop at the edges.
                foregroundActors.filter { !it.isKind("trojan_horse") }.forEach { enemy ->
                    if (!isVisible(enemy)) return@forEach
                    val scaledEnemy = enemy.copy(
                        posX = enemy.posX * playerScaleX
                    )
                    TapestryRenderer.drawCharacter(this, scaledEnemy, scale = scaleFactor, recordInto = enemy)
                    
                    // Draw health bar for enemy
                    if (!enemy.isDead) {
                        val enemyHeadDist = if (scaledEnemy.isMounted && !scaledEnemy.isChariot) 70f else if (scaledEnemy.isChariot) 50f else 40f
                        val epy = (200f - (enemyHeadDist + 35f) * scaledEnemy.size * scaleFactor +
                            com.example.game.elevationVisualOffset(enemy) * scaleFactor)
                            .coerceAtLeast(55f - groundOffsetY)
                        drawHealthBar(this, scaledEnemy.posX, epy, enemy.hp, enemy.ghostHp, enemy.maxHp)
                        drawStatusEffects(this, scaledEnemy.posX, epy - 12f, enemy)
                        if (enemy.bossType != null) {
                            drawContext.canvas.nativeCanvas.drawText(
                                enemy.name.uppercase(),
                                scaledEnemy.posX,
                                epy - 18f,
                                bossNamePaint
                            )
                        }
                    }
                }

                // Rear bearers were painted behind the throne, but their HUD stays on top.
                rearThroneActors.filter { isVisible(it) && !it.isDead }.forEach { enemy ->
                    val scaledEnemy = enemy.copy(posX = enemy.posX * playerScaleX)
                    val enemyHeadDist =
                        if (scaledEnemy.isMounted && !scaledEnemy.isChariot) 70f
                        else if (scaledEnemy.isChariot) 50f else 40f
                    val epy = (200f - (enemyHeadDist + 35f) * scaledEnemy.size * scaleFactor +
                        com.example.game.elevationVisualOffset(enemy) * scaleFactor)
                        .coerceAtLeast(55f - groundOffsetY)
                    drawHealthBar(this, scaledEnemy.posX, epy, enemy.hp, enemy.ghostHp, enemy.maxHp)
                    drawStatusEffects(this, scaledEnemy.posX, epy - 12f, enemy)
                }

                // 4. Draw Projectiles (Bows / Slingshots)
                projectiles.forEach { proj ->
                    val sx = proj.posX * playerScaleX
                    val sy = 200f + (proj.posY - 200f) * scaleFactor
                    val arrowDir = if (proj.velocityX > 0) 1f else -1f

                    // The flying shaft — shared, because a weapon-tipped shot is still an arrow.
                    // It used to be drawn only in the ARROW/BOLT branch, and the launchedWeaponId
                    // branch above it replaced the whole missile with a floating weapon head.
                    // withTip is false when a weapon head is about to be drawn over the point.
                    val drawArrowShaft = { withTip: Boolean ->
                        val shaftColor = if (proj.isBallista) Color(0xFF8A7156) else TapestryDark
                        val isBolt = proj.type == com.example.game.ProjectileType.BOLT
                        val strokeW = (if (proj.isBallista) 10f else if (isBolt) 4f else 5f * proj.sizeMultiplier) * scaleFactor
                        val length = (if (isBolt) 35f else 55f) * proj.sizeMultiplier * scaleFactor

                        drawLine(
                            color = shaftColor,
                            start = Offset(sx, sy),
                            end = Offset(sx - (length * arrowDir), sy + (if (proj.isBallista) 0f else 3f)),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        if (withTip) {
                            val tipRadius = if (proj.isBallista) 11f else 6f
                            drawCircle(Color(0xFF868C91), radius = tipRadius, center = Offset(sx, sy))
                        }
                        if (proj.hasSpikes) {
                            drawLine(TapestryDark, Offset(sx, sy), Offset(sx - (10f * arrowDir), sy + 8f), strokeWidth = 3f)
                            drawLine(TapestryDark, Offset(sx, sy), Offset(sx - (10f * arrowDir), sy - 8f), strokeWidth = 3f)
                        }
                        // Arrow feather fletching (Embroidered texture)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy + 2f), Offset(sx - (length * arrowDir), sy + 14f), strokeWidth = 5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.7f * arrowDir), sy - 2f), Offset(sx - (length * arrowDir), sy - 14f), strokeWidth = 5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.8f * arrowDir), sy + 2f), Offset(sx - (length * 1.1f * arrowDir), sy + 9f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                        drawLine(TapestryRed, Offset(sx - (length * 0.8f * arrowDir), sy + 2f), Offset(sx - (length * 1.1f * arrowDir), sy - 5f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                    }

                    if (proj.id.startsWith("bee_hive_")) {
                        // A flying bee skep: banded straw dome with the swarm trailing behind it
                        val dome = Path().apply {
                            moveTo(sx - 9f, sy + 6f)
                            quadraticTo(sx - 10f, sy - 8f, sx, sy - 10f)
                            quadraticTo(sx + 10f, sy - 8f, sx + 9f, sy + 6f)
                            close()
                        }
                        drawPath(dome, Color(0xFFD9B871))
                        drawPath(dome, TapestryDark, style = Stroke(width = 1.5f))
                        for (band in 0..2) {
                            val by = sy + 4f - band * 5f
                            drawLine(TapestryDark.copy(alpha = 0.6f), Offset(sx - 9f + band, by), Offset(sx + 9f - band, by), strokeWidth = 1.5f)
                        }
                        drawCircle(TapestryDark, radius = 2f, center = Offset(sx, sy + 2f)) // entrance hole
                        // The swarm streaming after it
                        val brng = kotlin.random.Random(proj.id.hashCode())
                        repeat(5) { b ->
                            val bx = sx - arrowDir * (12f + brng.nextFloat() * 22f)
                            val by = sy - 6f + brng.nextFloat() * 12f
                            drawCircle(if (b % 2 == 0) Color(0xFFD6A420) else TapestryDark, radius = 1.6f, center = Offset(bx, by))
                        }
                    } else if (proj.launchedWeaponId != null) {
                        // Drawing miniature launched weapon head as the projectile!
                        // On an arrow or bolt the head is only the TIP: shaft and fletching first,
                        // head over the point. A thrown weapon has no shaft and skips this.
                        if (proj.type == com.example.game.ProjectileType.ARROW ||
                            proj.type == com.example.game.ProjectileType.BOLT) {
                            drawArrowShaft(false)
                        }
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
                    } else if (proj.type == com.example.game.ProjectileType.DART) {
                        val length = 18f * scaleFactor
                        val shaftStart = Offset(sx, sy)
                        val shaftEnd = Offset(sx - length * arrowDir, sy + 2f)
                        for (trail in 1..2) {
                            val offset = trail * 5f * arrowDir
                            drawLine(
                                color = TapestryDark.copy(alpha = 0.35f / trail),
                                start = Offset(sx - offset, sy),
                                end = Offset(sx - length * arrowDir - offset, sy + 2f),
                                strokeWidth = 1f
                            )
                        }
                        drawLine(TapestryDark, shaftStart, shaftEnd, strokeWidth = 2f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFF868C91), radius = 2.5f, center = shaftStart)
                    } else if (proj.type == com.example.game.ProjectileType.BOMB) {
                        // A clay pot with a lit cord, tumbling as it goes. The spin is off the id
                        // and the position so each one tumbles differently without any state.
                        val r = 9f * proj.sizeMultiplier
                        val spin = (sx + sy) * 1.4f
                        drawCircle(Color(0xFF4A4238), radius = r, center = Offset(sx, sy))
                        drawCircle(TapestryDark, radius = r, center = Offset(sx, sy), style = Stroke(width = 2f))
                        // Banding on the pot, rotating with the tumble
                        drawLine(
                            TapestryDark.copy(alpha = 0.7f),
                            Offset(sx - cos(spin.toDouble()).toFloat() * r * 0.8f, sy - sin(spin.toDouble()).toFloat() * r * 0.8f),
                            Offset(sx + cos(spin.toDouble()).toFloat() * r * 0.8f, sy + sin(spin.toDouble()).toFloat() * r * 0.8f),
                            strokeWidth = 2f
                        )
                        // The fuse: a short cord with a spark on the end, trailing behind the throw
                        val fx = sx - arrowDir * r * 0.9f
                        val fy = sy - r * 0.9f
                        drawLine(Color(0xFF8C6F47), Offset(sx, sy - r * 0.6f), Offset(fx, fy - 5f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFFFFC34D), radius = 3f, center = Offset(fx, fy - 6f))
                        drawCircle(Color(0xFFE07020), radius = 1.6f, center = Offset(fx, fy - 6f))
                    } else if (proj.type == com.example.game.ProjectileType.TORCH) {
                        drawLine(
                            Color(0xFF6E5536),
                            Offset(sx - 22f * arrowDir, sy + 5f),
                            Offset(sx, sy),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(Color(0xFFE07020), radius = 9f, center = Offset(sx, sy))
                        drawCircle(Color(0xFFFFC34D), radius = 4f, center = Offset(sx + 2f, sy - 2f))
                    } else if (proj.type == com.example.game.ProjectileType.ARROW || proj.type == com.example.game.ProjectileType.BOLT) {
                        drawArrowShaft(true)
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
                        val baseRadius = if (part.isSmoke) 6f + (part.age * 5f) else part.radius
                        drawCircle(
                            color = part.color.copy(alpha = (1f - (part.age / part.maxAge)).coerceIn(0f, 1f)),
                            radius = baseRadius,
                            center = Offset(px, py)
                        )
                    }
                }

                // 5b. Blood thrown clear onto the linen itself after a knot of men falls together.
                // Positioned in canvas fractions, not world pixels: the conceit is that the
                // artefact got splashed, so it must not pan with the battlefield behind it.
                viewModel.tapestrySplats.value.forEach { splat ->
                    val fade = (1f - (splat.age / splat.maxAge)).coerceIn(0f, 1f)
                    val cx = splat.xFrac * size.width
                    val cy = splat.yFrac * size.height
                    // Oxblood, and opaque. A bright red at low alpha came out pink over the cream
                    // linen and read as berries rather than blood.
                    val gore = Color(0xFF6E1414).copy(alpha = fade * 0.88f)

                    // The whole splat — gout, spatter and run-off — is ONE path filled ONCE.
                    //
                    // Drawing them as separate translucent shapes made every overlap compound: the
                    // core where blobs met came out darker than 0.88, while the drip running onto
                    // bare linen was exactly 0.88, so the run-off read as a different opacity from
                    // the splat it belongs to. A single path has no overlaps to compound.
                    val splatPath = Path()

                    // An irregular gout, never a disc: perfect circles read as polka dots against
                    // the flat embroidery. Jitter is seeded, so a splat does not crawl frame to
                    // frame while it fades.
                    fun addBlob(bx: Float, by: Float, r: Float, salt: Int) {
                        for (i in 0 until 11) {
                            val a = i / 11f * 2f * Math.PI.toFloat()
                            val jitter = 0.62f + ((splat.seed * 31 + salt * 17 + i * 7919) % 100) / 130f
                            val px = bx + cos(a) * r * jitter
                            val py = by + sin(a) * r * jitter
                            if (i == 0) splatPath.moveTo(px, py) else splatPath.lineTo(px, py)
                        }
                        splatPath.close()
                    }

                    addBlob(cx, cy, splat.radius, 0)
                    // Satellite spatter thrown clear of the main gout.
                    for (i in 0 until 5) {
                        val a = ((splat.seed + i * 97) % 360) * (Math.PI.toFloat() / 180f)
                        val d = splat.radius * (1.15f + ((splat.seed + i * 31) % 70) / 100f)
                        addBlob(
                            cx + cos(a) * d,
                            cy + sin(a) * d,
                            splat.radius * (0.12f + ((splat.seed + i * 13) % 22) / 100f),
                            i + 1
                        )
                    }
                    // The run-off, which lengthens as it ages — it is running down the cloth.
                    // A tapering sliver rather than a stroked line, so it can join the same path.
                    val dripTop = cy + splat.radius * 0.5f
                    val dripEnd = cy + splat.radius * (1.4f + splat.age * 0.5f)
                    val dripW = splat.radius * 0.09f
                    splatPath.moveTo(cx - dripW, dripTop)
                    splatPath.lineTo(cx + dripW, dripTop)
                    splatPath.lineTo(cx + dripW * 0.45f, dripEnd)
                    splatPath.lineTo(cx - dripW * 0.45f, dripEnd)
                    splatPath.close()

                    drawPath(splatPath, gore)
                }

                // 6. Draw Floating Comic popups (e.g. *CLANGUS*, *THWACKUS*)
                popups.forEach { pop ->
                    val sx = pop.x * playerScaleX
                    val sy = pop.y * scaleFactor - (pop.age * 30f) // float upwards
                    drawComicTextBubble(this, pop.text, sx, sy, pop.color, pop.age)
                }
            }

            // (The Halley's-comet portent — the "miniature sun" — is retired along with its hidden
            // 1.5x both-sides buff. Weather visuals are reserved for the divine weather rewards.)

            // (Divine weather is drawn by DivineWeatherOverlay, a Canvas of its own over the whole
            // screen. Drawn here it was still subject to this canvas's layers and camera state —
            // it washed only part of the field and thinned out as the player walked right, which
            // is exactly what it was moved out of the camera transform to stop doing.)

            // Held-finger health read-out. Screen space, like the weather: it must not scroll with
            // the world while the finger is still down.
            inspectAt.value?.let { at ->
                val candidates = (listOfNotNull(playerFighter) + enemies).filter { !it.isDead }
                val target = candidates.minByOrNull {
                    kotlin.math.abs(it.posX * playerScaleX + offsetX - at.x)
                }
                if (target != null) {
                    val tx = target.posX * playerScaleX + offsetX
                    if (kotlin.math.abs(tx - at.x) <= INSPECT_GRAB_PX) {
                        drawHealthTag(
                            this,
                            target,
                            tx.coerceIn(80f, size.width - 80f),
                            (at.y - 60f).coerceIn(70f, size.height - 60f)
                        )
                    }
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
            val isRetired = uiState.isRetired
            Card(
                colors = CardDefaults.cardColors(containerColor = TapestryLinenCard),
                modifier = Modifier
                    .width(420.dp)
                    .border(4.dp, if (isWin) TapestryGreen else if (isRetired) TapestryMustard else TapestryRed, RoundedCornerShape(8.dp))
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
                        text = if (isWin) "VICTORIA GLORIOSUS!" else if (isRetired) "RETIRED WITH HONOUR" else "MORTIS ET DEFEAT!",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isWin) TapestryGreen else if (isRetired) TapestryMustard else TapestryRed
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
                                    com.example.game.FlavourText.slainByLine(
                                        uiState.slainByName, uiState.slainByWeapon
                                    )?.let { epitaph ->
                                        Text(
                                            epitaph,
                                            fontSize = 9.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontFamily = FontFamily.Serif,
                                            color = TapestryDark
                                        )
                                    }
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

private val bossNamePaint = android.graphics.Paint().apply {
    isAntiAlias = true
    textSize = 15f
    typeface = android.graphics.Typeface.create(
        android.graphics.Typeface.SERIF,
        android.graphics.Typeface.BOLD
    )
    color = TapestryDark.toArgb()
    textAlign = android.graphics.Paint.Align.CENTER
}

private fun drawStatusEffects(scope: androidx.compose.ui.graphics.drawscope.DrawScope, x: Float, y: Float, fighter: com.example.game.FighterState) {
    var offsetX = x - 10f
    val iconRadius = 4f
    
    if (fighter.poisonDuration > 0f) {
        // Draw poison symbol (green circle with P?) We can just draw a little green bubble
        scope.drawCircle(color = Color(0xFF2E7D32), radius = iconRadius, center = Offset(offsetX, y))
        offsetX += 12f
    }
    if (fighter.igniteDuration > 0f) {
        scope.drawCircle(color = Color(0xFFE07020), radius = iconRadius + 1f, center = Offset(offsetX, y))
        scope.drawCircle(color = Color(0xFFFFC34D), radius = iconRadius * 0.45f, center = Offset(offsetX, y - 1f))
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
    if (fighter.diseaseDuration > 0f || fighter.isContagious) {
        // Disease had no icon at all, and its tint was green — indistinguishable from the hag's
        // poison. Own symbol now: a yellow swelling ringed by three dark buboes.
        scope.drawCircle(color = Color(0xFFC9B03C), radius = iconRadius, center = Offset(offsetX, y))
        repeat(3) { i ->
            val a = i * 2.094f - 1.57f
            scope.drawCircle(
                color = Color(0xFF6B4A1F),
                radius = 1.6f,
                center = Offset(offsetX + kotlin.math.cos(a) * iconRadius, y + kotlin.math.sin(a) * iconRadius)
            )
        }
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

/**
 * The player's mount bar, sitting just above his own. Orange for the beast, with the same trailing
 * yellow ghost so you can see how hard it was just hit.
 *
 * Returns the y the caller should treat as the top of the stack — unchanged when there is no
 * mount, so the status icons stay put for a man on foot.
 */
private fun drawMountHealthBar(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    x: Float,
    y: Float,
    fighter: com.example.game.FighterState
): Float {
    if (!fighter.isMounted || fighter.mountMaxHp <= 0f) return y
    val barWidth = 100f
    val barHeight = 8f          // slimmer than the man's, so the two never read as one bar
    val gap = 4f
    val barY = y - barHeight - gap
    val startX = x - barWidth / 2f

    scope.drawRect(TapestryDark.copy(alpha = 0.45f), Offset(startX, barY), Size(barWidth, barHeight))
    val ghostFrac = (fighter.mountGhostHp / fighter.mountMaxHp).coerceIn(0f, 1f)
    scope.drawRect(Color(0xFFD4B144), Offset(startX, barY), Size(barWidth * ghostFrac, barHeight))
    val frac = (fighter.mountHp / fighter.mountMaxHp).coerceIn(0f, 1f)
    scope.drawRect(Color(0xFFD97A1E), Offset(startX, barY), Size(barWidth * frac, barHeight))
    scope.drawRect(
        TapestryDark, Offset(startX, barY), Size(barWidth, barHeight), style = Stroke(width = 1.5f)
    )
    return barY
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

/**
 * The divine weather flourish, as a full-screen layer of its own. It draws nothing at all except
 * during the 1.2s after a charge is spent, so it costs one empty canvas the rest of the time.
 */
@Composable
private fun DivineWeatherOverlay(viewModel: GameViewModel) {
    val weatherFlash by viewModel.weatherFlash.collectAsState()
    // Clock the flourish from when the UI first SEES the flash, not from when the tap fired.
    // Under frame lag the wall-clock window could expire before a single frame drew it, so the
    // weather looked like it "didn't work" even though its combat effect had applied.
    val shownAt = remember(weatherFlash) { System.currentTimeMillis() }
    val flash = weatherFlash ?: return
    // Repaint for the length of the flourish; nothing else in this canvas changes.
    var now by remember(weatherFlash) { mutableStateOf(shownAt) }
    LaunchedEffect(weatherFlash) {
        while (now - shownAt <= (WEATHER_FLOURISH_SECS * 1000f).toLong()) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    val elapsed = (now - shownAt) / 1000f
    if (elapsed > WEATHER_FLOURISH_SECS) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawWeatherFlourish(this, flash.first, elapsed / WEATHER_FLOURISH_SECS)
    }
}

/** How near the finger has to land, in screen px, to be inspecting a given fighter. */
internal const val INSPECT_GRAB_PX = 70f

/**
 * One affliction a body is carrying: the wool it is stitched in, what to call it, how many doses
 * are held, and how many seconds are left on the clock. [capacity] is 0 for the effects that do
 * not stack, so no empty knots are drawn against them.
 */
private class Affliction(
    val wool: androidx.compose.ui.graphics.Color,
    val label: String,
    val doses: Int,
    val capacity: Int,
    val seconds: Float
)

/** The knots row is capped so the card cannot run off the top of the field. */
private const val INSPECT_MAX_ROWS = 4
private const val INSPECT_ROW_H = 20f

/**
 * Everything currently rotting, slowing or flattening this body, worst-first: the damage-over-time
 * afflictions carry doses, the rest are on or off. The four Dots are read through [dotStacks] so a
 * doubled venom shows as two knots rather than a refreshed clock.
 */
private fun afflictionsOf(f: FighterState): List<Affliction> = buildList {
    // Dyed in the eight wools the rest of the tapestry uses — no new colour enters for the UI.
    val dye = mapOf(
        Dot.BLEED to TapestryRed,
        Dot.POISON to TapestryGreen,
        Dot.IGNITE to TapestryMustard,
        Dot.DISEASE to androidx.compose.ui.graphics.Color(0xFF6E5536)
    )
    val name = mapOf(
        Dot.BLEED to "Bleeding", Dot.POISON to "Poisoned",
        Dot.IGNITE to "Alight", Dot.DISEASE to "Diseased"
    )
    Dot.entries.forEach { d ->
        val secs = f.dotSeconds(d)
        if (secs > 0f) add(
            Affliction(dye.getValue(d), name.getValue(d), f.dotStacks[d.ordinal].coerceAtLeast(1), MAX_DOT_STACKS, secs)
        )
    }
    if (f.crumpleDuration > 0f) add(Affliction(TapestryDark, "Crumpled", 1, 0, f.crumpleDuration))
    if (f.panicDuration > 0f) add(Affliction(androidx.compose.ui.graphics.Color(0xFF5A6B63), "Blinded", 1, 0, f.panicDuration))
    if (f.slowDuration > 0f) add(Affliction(TapestryBlue, "Slowed", 1, 0, f.slowDuration))
}

/**
 * The hold-to-inspect tag: a scrap of linen with a name, an exact hit-point count, and the roll of
 * what the body is currently carrying.
 *
 * Doses are stitched, not numbered — a filled knot per dose on a short thread in that affliction's
 * own wool, hollow knots for the doses it could still take. It is the tally a herald would keep,
 * it costs no width, and it is the one place this card is allowed to be decorative.
 */
private fun drawHealthTag(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    target: FighterState,
    x: Float,
    y: Float
) {
    val name = target.name
    val hp = "${target.hp.toInt()} / ${target.maxHp.toInt()}"
    val namePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        setColor(TapestryDark.toArgb())
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val hpPaint = android.graphics.Paint(namePaint).apply {
        textSize = 30f
        setColor(TapestryRed.toArgb())
    }
    val labelPaint = android.graphics.Paint(namePaint).apply {
        textSize = 18f
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val clockPaint = android.graphics.Paint(labelPaint).apply {
        textSize = 16f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        setColor(TapestryDark.copy(alpha = 0.65f).toArgb())
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    val all = afflictionsOf(target)
    val shown = all.take(INSPECT_MAX_ROWS)
    val overflow = all.size - shown.size
    val tail = if (overflow > 0) "and $overflow more" else null
    val rows = shown.size + (if (tail != null) 1 else 0)

    // The knot thread reserves a fixed gutter so every label starts on the same left margin.
    val knotGutter = 38f
    val rowW = shown.maxOfOrNull { a ->
        knotGutter + labelPaint.measureText(a.label) + 16f + clockPaint.measureText("${ceil(a.seconds).toInt()}s")
    } ?: 0f
    val tailW = tail?.let { knotGutter + labelPaint.measureText(it) } ?: 0f
    val halfW = maxOf(namePaint.measureText(name), hpPaint.measureText(hp), rowW, tailW) / 2f + 14f

    val top = y - 46f
    val height = 62f + rows * INSPECT_ROW_H + (if (rows > 0) 6f else 0f)
    scope.drawRect(TapestryLinenCard, topLeft = Offset(x - halfW, top), size = Size(halfW * 2f, height))
    scope.drawRect(TapestryDark, topLeft = Offset(x - halfW, top), size = Size(halfW * 2f, height), style = Stroke(width = 2.5f))
    scope.drawContext.canvas.nativeCanvas.drawText(name, x, y - 26f, namePaint)
    scope.drawContext.canvas.nativeCanvas.drawText(hp, x, y + 6f, hpPaint)

    if (rows == 0) return
    // A thread ruled across the card separates who he is from what he is carrying.
    val ruleY = top + 62f
    scope.drawLine(
        TapestryDark.copy(alpha = 0.35f),
        Offset(x - halfW + 10f, ruleY), Offset(x + halfW - 10f, ruleY),
        strokeWidth = 1.5f
    )
    val left = x - halfW + 12f
    shown.forEachIndexed { i, a ->
        val baseY = ruleY + 6f + i * INSPECT_ROW_H + 14f
        val knotY = baseY - 5f
        val slots = if (a.capacity > 0) a.capacity else 1
        repeat(slots) { k ->
            val kx = left + 5f + k * 9f
            if (k < a.doses) {
                scope.drawCircle(a.wool, radius = 3.4f, center = Offset(kx, knotY))
            } else {
                scope.drawCircle(a.wool.copy(alpha = 0.45f), radius = 3.4f, center = Offset(kx, knotY), style = Stroke(width = 1.2f))
            }
        }
        scope.drawContext.canvas.nativeCanvas.drawText(a.label, left + knotGutter, baseY, labelPaint)
        scope.drawContext.canvas.nativeCanvas.drawText("${ceil(a.seconds).toInt()}s", x + halfW - 12f, baseY, clockPaint)
    }
    tail?.let {
        val baseY = ruleY + 6f + shown.size * INSPECT_ROW_H + 14f
        scope.drawContext.canvas.nativeCanvas.drawText(it, left + knotGutter, baseY, clockPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })
    }
}

// --- Divine weather: header medallions and battle flourishes ---

private const val WEATHER_FLOURISH_SECS = 1.2f
private const val WEATHER_COOLDOWN_SECS = GameViewModel.WEATHER_COOLDOWN

/** The motif is drawn in a 44x44 design space and scaled to whatever box it lands in. */
internal const val WEATHER_ICON_BOX = 44f

/**
 * A held divine weather, as a tappable medallion in the header bar. Ready charges sit in full
 * colour and answer a tap; a spent one goes pale while a red thread re-stitches itself around
 * the rim, and ignores taps until the stitch closes.
 */
@Composable
internal fun WeatherCharge(
    weather: DivineWeather,
    cooldownFraction: Float,
    onTrigger: () -> Unit
) {
    val ready = cooldownFraction <= 0f
    Canvas(
        modifier = Modifier
            .size(26.dp)
            .clickable(enabled = ready, onClick = onTrigger)
            .testTag("weather_charge_${weather.id}")
    ) {
        val s = size.minDimension / WEATHER_ICON_BOX
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawWeatherIcon(
                this,
                weather,
                Offset(WEATHER_ICON_BOX / 2f, WEATHER_ICON_BOX / 2f),
                cooldownFraction
            )
        }
    }
}

/**
 * A weather charge, embroidered as a border roundel. [cooldownFraction] 1 = just spent,
 * 0 = ready: a spent charge is drawn pale, with a thread arc closing around the rim as it recharges.
 */
internal fun drawWeatherIcon(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    weather: DivineWeather,
    center: Offset,
    cooldownFraction: Float
) {
    val ready = cooldownFraction <= 0f
    val alpha = if (ready) 1f else 0.3f
    val cx = center.x
    val cy = center.y

    // Roundel of linen to sit the motif on, so it reads as part of the border
    scope.drawCircle(TapestryLinenBg.copy(alpha = 0.9f), radius = 17f, center = center)
    scope.drawCircle(TapestryDark.copy(alpha = alpha), radius = 17f, center = center, style = Stroke(width = 2f))

    when (weather) {
        DivineWeather.LIGHTNING -> { // jagged gold bolt
            val bolt = Path().apply {
                moveTo(cx + 4f, cy - 11f)
                lineTo(cx - 5f, cy + 1f)
                lineTo(cx + 1f, cy + 1f)
                lineTo(cx - 3f, cy + 11f)
                lineTo(cx + 7f, cy - 2f)
                lineTo(cx + 1f, cy - 2f)
                close()
            }
            scope.drawPath(bolt, TapestryMustard.copy(alpha = alpha))
            scope.drawPath(bolt, TapestryDark.copy(alpha = alpha), style = Stroke(width = 1.5f))
        }
        DivineWeather.FLOOD -> { // curling wave crest
            val wave = Path().apply {
                moveTo(cx - 11f, cy + 6f)
                quadraticTo(cx - 5f, cy - 9f, cx + 3f, cy - 1f)
                quadraticTo(cx + 7f, cy + 3f, cx + 11f, cy - 4f)
            }
            scope.drawPath(wave, TapestryBlue.copy(alpha = alpha), style = Stroke(width = 3f, cap = StrokeCap.Round))
            scope.drawLine(
                TapestryBlue.copy(alpha = alpha * 0.7f),
                Offset(cx - 10f, cy + 11f), Offset(cx + 10f, cy + 11f),
                strokeWidth = 2.5f, cap = StrokeCap.Round
            )
        }
        DivineWeather.HAIL -> { // cluster of falling stones
            listOf(
                Triple(-6f, -6f, 4.5f), Triple(5f, -8f, 3.5f), Triple(0f, 2f, 5f),
                Triple(-7f, 7f, 3f), Triple(7f, 5f, 4f)
            ).forEach { (dx, dy, r) ->
                scope.drawCircle(Color.White.copy(alpha = alpha), radius = r, center = Offset(cx + dx, cy + dy))
                scope.drawCircle(TapestryDark.copy(alpha = alpha), radius = r, center = Offset(cx + dx, cy + dy), style = Stroke(width = 1.2f))
            }
        }
        DivineWeather.FROGS -> { // one plump frog, mid-leap
            val body = Path().apply {
                moveTo(cx - 8f, cy + 6f)
                quadraticTo(cx - 6f, cy - 6f, cx + 4f, cy - 5f)
                quadraticTo(cx + 10f, cy - 4f, cx + 9f, cy + 3f)
                quadraticTo(cx + 2f, cy + 8f, cx - 8f, cy + 6f)
                close()
            }
            scope.drawPath(body, TapestryGreen.copy(alpha = alpha))
            scope.drawPath(body, TapestryDark.copy(alpha = alpha), style = Stroke(width = 1.5f))
            // Folded leaping leg
            scope.drawLine(TapestryGreen.copy(alpha = alpha), Offset(cx - 7f, cy + 6f), Offset(cx - 12f, cy + 1f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            scope.drawLine(TapestryGreen.copy(alpha = alpha), Offset(cx - 12f, cy + 1f), Offset(cx - 13f, cy + 9f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            // Bulging eyes
            scope.drawCircle(Color.White.copy(alpha = alpha), radius = 2.5f, center = Offset(cx + 3f, cy - 7f))
            scope.drawCircle(TapestryDark.copy(alpha = alpha), radius = 1.2f, center = Offset(cx + 3.5f, cy - 7f))
        }
        DivineWeather.FROST -> { // six-armed frost crystal
            repeat(6) { i ->
                val a = (Math.PI / 3.0 * i).toFloat()
                val ex = cx + kotlin.math.cos(a) * 11f
                val ey = cy + kotlin.math.sin(a) * 11f
                scope.drawLine(TapestryBlue.copy(alpha = alpha), center, Offset(ex, ey), strokeWidth = 2f, cap = StrokeCap.Round)
                // little barbs, so it reads as ice rather than a wheel
                val bx = cx + kotlin.math.cos(a) * 6.5f
                val by = cy + kotlin.math.sin(a) * 6.5f
                scope.drawLine(
                    TapestryBlue.copy(alpha = alpha * 0.8f),
                    Offset(bx, by),
                    Offset(bx + kotlin.math.cos(a + 0.9f) * 4.5f, by + kotlin.math.sin(a + 0.9f) * 4.5f),
                    strokeWidth = 1.5f
                )
            }
        }
    }

    if (!ready) {
        // The charge re-stitches itself: a red thread closing clockwise around the rim
        val fill = (1f - cooldownFraction).coerceIn(0f, 1f)
        val r = 20f
        scope.drawArc(
            color = TapestryRed.copy(alpha = 0.85f),
            startAngle = -90f,
            sweepAngle = 360f * fill,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

/** A single-pass flourish across the whole field. [progress] runs 0 -> 1 and then it is gone. */
/**
 * The concrete stat delta a reward card applies, for the card's impact line.
 *
 * Read straight off the same GameData the simulation uses, so the card can't drift from what you
 * actually get. Empty string means "no numbers worth showing" — the flavour text already says it.
 */
private fun buildImpactFor(choice: com.example.game.LevelUpChoice): String {
    fun gearStats(item: com.example.game.GearItem): String = buildList {
        if (item.pierce > 0f) add("+${item.pierce.toInt()} pierce")
        if (item.slash > 0f) add("+${item.slash.toInt()} slash")
        if (item.blunt > 0f) add("+${item.blunt.toInt()} blunt")
        if (item.defense > 0f) add("+${item.defense.toInt()} armor")
        if (item.mass > 0f) add("+${"%.1f".format(item.mass)}kg")
        if (item.speedPenalty > 0f) add("-${(item.speedPenalty * 100).toInt()}% speed")
    }.joinToString("  ")

    return when (choice.type) {
        "attachment" -> com.example.game.GameData.WEAPON_HEADS.find { it.id == choice.itemId }
            ?.let { att ->
                // An attachment contributes half its damage on top of the main head (CombatEngine).
                val half = com.example.game.GameData.WEAPON_HEADS.first { it.id == choice.itemId }
                buildList {
                    if (half.pierce > 0f) add("+${(half.pierce * 0.5f).toInt()} pierce")
                    if (half.slash > 0f) add("+${(half.slash * 0.5f).toInt()} slash")
                    if (half.blunt > 0f) add("+${(half.blunt * 0.5f).toInt()} blunt")
                    add("+${"%.1f".format(att.mass)}kg")
                }.joinToString("  ")
            } ?: ""
        "armor", "comedy" -> com.example.game.GameData.ARMOR_PIECES.find { it.id == choice.itemId }
            ?.let { gearStats(it) } ?: ""
        "extension" -> "+0.5 reach on every melee weapon  +0.5kg"
        "follower" -> com.example.game.GameData.ANCILLARIES.find { it.id == choice.itemId }?.let { a ->
            buildList {
                if (a.hpBoost != 0f) add("${if (a.hpBoost > 0) "+" else ""}${a.hpBoost.toInt()} max HP")
                if (a.speedBoost != 0f) add("${if (a.speedBoost > 0) "+" else ""}${(a.speedBoost * 100).toInt()}% speed")
            }.joinToString("  ")
        } ?: ""
        "follower_multiply" -> com.example.game.GameData.ANCILLARIES.find { it.id == choice.itemId }?.let { a ->
            buildList {
                if (a.hpBoost != 0f) add("+${(a.hpBoost * 2).toInt()} max HP")
                if (a.speedBoost != 0f) add("+${(a.speedBoost * 200).toInt()}% speed")
            }.joinToString("  ").ifEmpty { "Three bodies take the field" }
        } ?: ""
        "panoply" -> "Retinue: +45 helm  +${com.example.game.GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" }.defense.toInt()} mail  +15 gauntlets"
        "weather" -> "One divine charge per battle"
        else -> ""
    }
}

internal fun drawWeatherFlourish(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    weather: DivineWeather,
    progress: Float
) {
    val p = progress.coerceIn(0f, 1f)
    val fade = 1f - p
    // The heavens open over the WHOLE tapestry, border bands included — boxed inside the inner
    // field the flourish read as weather-in-a-window.
    val fieldRect = androidx.compose.ui.geometry.Rect(0f, 0f, scope.size.width, scope.size.height)
    val geometry = weatherFlourishGeometry(weather, fieldRect, p)

    scope.withTransform({
        clipRect(
            left = fieldRect.left,
            top = fieldRect.top,
            right = fieldRect.right,
            bottom = fieldRect.bottom
        )
    }) {
        val washColor = when (weather) {
            DivineWeather.LIGHTNING -> Color.White.copy(alpha = fade * 0.08f)
            DivineWeather.FLOOD -> TapestryBlue.copy(alpha = fade * 0.16f)
            DivineWeather.HAIL -> TapestryBlue.copy(alpha = fade * 0.18f)
            DivineWeather.FROST -> TapestryBlue.copy(alpha = fade * 0.30f)
            DivineWeather.FROGS -> TapestryGreen.copy(alpha = fade * 0.14f)
        }
        drawRect(
            color = washColor,
            topLeft = geometry.washRect.topLeft,
            size = geometry.washRect.size
        )

        when (geometry) {
        is LightningFlourishGeometry -> {
            // Two forked bolts — one per foe the smite actually picks (GameViewModel takes the two
            // toughest). The white flash snaps on hard and dies fast; the gold thread lingers.
            val flash = (1f - p * 4f).coerceAtLeast(0f)
            drawRect(
                Color.White.copy(alpha = flash * 0.75f),
                topLeft = geometry.washRect.topLeft,
                size = geometry.washRect.size
            )
            geometry.strikeXs.forEachIndexed { i, strikeX ->
                val rng = kotlin.random.Random(1066L + i)
                // Jagged descent: each segment stutters sideways, like couched thread.
                val bolt = Path().apply {
                    moveTo(strikeX, geometry.skyY)
                    var y = geometry.skyY
                    var x = strikeX
                    while (y < geometry.groundY) {
                        y += (geometry.groundY - geometry.skyY) / 6f
                        x = strikeX +
                            (rng.nextFloat() * 2f - 1f) * geometry.jaggedXRadius
                        lineTo(x, y)
                    }
                }
                drawPath(bolt, Color.White.copy(alpha = fade * 0.9f), style = Stroke(width = 14f, cap = StrokeCap.Round))
                drawPath(bolt, TapestryMustard.copy(alpha = fade), style = Stroke(width = 8f, cap = StrokeCap.Round))
                drawPath(bolt, TapestryDark.copy(alpha = fade * 0.9f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                // Strike burst at the earth, expanding as it fades
                val burst = geometry.burstStartRadius + p * geometry.burstGrowthRadius
                drawCircle(TapestryMustard.copy(alpha = fade * 0.5f), radius = burst, center = Offset(strikeX, geometry.groundY))
                repeat(7) {
                    val a = rng.nextFloat() * 6.283f
                    drawLine(
                        TapestryMustard.copy(alpha = fade * 0.8f),
                        Offset(strikeX, geometry.groundY),
                        Offset(strikeX + kotlin.math.cos(a) * burst * 1.3f, geometry.groundY + kotlin.math.sin(a) * burst * 0.5f),
                        strokeWidth = 3f, cap = StrokeCap.Round
                    )
                }
            }
        }
        is FloodFlourishGeometry -> {
            // A stitched wave band sweeping across the field
            val edge = geometry.edgeX
            val band = Path().apply {
                moveTo(edge - geometry.backExtent, fieldRect.bottom)
                lineTo(edge - geometry.backExtent * 0.75f, geometry.crestY)
                quadraticTo(
                    edge - geometry.backExtent * 0.25f,
                    geometry.crestDipY,
                    edge,
                    geometry.crestY
                )
                lineTo(edge + geometry.frontExtent, fieldRect.bottom)
                close()
            }
            drawPath(band, TapestryBlue.copy(alpha = fade * 0.8f))
            drawPath(band, TapestryDark.copy(alpha = fade * 0.6f), style = Stroke(width = 3f))
            // Foam on the leading crest, so the band reads as water rather than a grey slab
            val crest = Path().apply {
                moveTo(edge - geometry.backExtent * 0.75f, geometry.crestY)
                quadraticTo(
                    edge - geometry.backExtent * 0.25f,
                    geometry.crestDipY,
                    edge,
                    geometry.crestY
                )
            }
            drawPath(crest, Color.White.copy(alpha = fade * 0.85f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            // Spray thrown off the crest, so the deluge reads as violent water
            val rng = kotlin.random.Random(1068L)
            repeat(26) {
                val sy = fieldRect.top + rng.nextFloat() * fieldRect.height
                val sx = edge - geometry.sprayBackExtent +
                    rng.nextFloat() *
                    (geometry.sprayBackExtent + geometry.sprayFrontExtent)
                drawCircle(
                    Color.White.copy(alpha = fade * (0.4f + rng.nextFloat() * 0.5f)),
                    radius = 2f + rng.nextFloat() * 5f,
                    center = Offset(sx, sy)
                )
            }
        }
        is HailFlourishGeometry -> {
            // Falling white stitches, seeded so they do not jitter between frames. Denser and
            // faster than before, with stones that shatter on the earth — hail you can feel.
            val rng = kotlin.random.Random(1066L)
            repeat(150) {
                val x = geometry.dropRect.left + rng.nextFloat() * geometry.dropRect.width
                val startY = rng.nextFloat() * geometry.dropRect.height
                val y = geometry.dropRect.top +
                    (startY + p * geometry.dropRect.height * 2.6f) % geometry.dropRect.height
                val len = geometry.streakMinLength +
                    rng.nextFloat() * (geometry.streakMaxLength - geometry.streakMinLength)
                drawLine(
                    Color.White.copy(alpha = fade * 0.9f),
                    Offset(x, y), Offset(x - len * 0.35f, y + len),
                    strokeWidth = 3.5f, cap = StrokeCap.Round
                )
            }
            // Shatter marks where the stones land
            val srng = kotlin.random.Random(2066L)
            repeat(22) {
                val x = fieldRect.left + srng.nextFloat() * fieldRect.width
                val hitAt = srng.nextFloat()
                val since = p - hitAt
                if (since in 0f..0.35f) {
                    val r = since / 0.35f
                    drawCircle(
                        Color.White.copy(alpha = (1f - r) * fade * 0.8f),
                        radius = 4f + r * 16f,
                        center = Offset(
                            x,
                            geometry.groundY +
                                srng.nextFloat() * (geometry.impactBottomY - geometry.groundY)
                        ),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
        is FrogsFlourishGeometry -> {
            // Falling frogs: plump green bodies with trailing legs, seeded so they do not
            // jitter, and landing hop-rings where they strike the earth.
            val rng = kotlin.random.Random(1068L)
            repeat(60) {
                val x = geometry.dropRect.left + rng.nextFloat() * geometry.dropRect.width
                val startY = rng.nextFloat() * geometry.dropRect.height
                val y = geometry.dropRect.top +
                    (startY + p * geometry.dropRect.height * 2.2f) % geometry.dropRect.height
                val s = 0.7f + rng.nextFloat() * 0.6f
                val frogGreen = Color(0xFF4C7A3D).copy(alpha = fade * 0.95f)
                // Body
                drawOval(frogGreen, topLeft = Offset(x - 7f * s, y - 5f * s), size = Size(14f * s, 10f * s))
                drawOval(TapestryDark.copy(alpha = fade * 0.8f), topLeft = Offset(x - 7f * s, y - 5f * s), size = Size(14f * s, 10f * s), style = Stroke(width = 1.5f))
                // Trailing splayed legs (falling posture)
                drawLine(frogGreen, Offset(x - 5f * s, y + 3f * s), Offset(x - 11f * s, y - 6f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
                drawLine(frogGreen, Offset(x + 5f * s, y + 3f * s), Offset(x + 11f * s, y - 6f * s), strokeWidth = 2f * s, cap = StrokeCap.Round)
                // Eyes
                drawCircle(Color.White.copy(alpha = fade), radius = 1.8f * s, center = Offset(x - 3f * s, y - 5f * s))
                drawCircle(Color.White.copy(alpha = fade), radius = 1.8f * s, center = Offset(x + 3f * s, y - 5f * s))
            }
            // Hop-rings where they land
            val srng = kotlin.random.Random(3066L)
            repeat(16) {
                val x = fieldRect.left + srng.nextFloat() * fieldRect.width
                val hitAt = srng.nextFloat()
                val since = p - hitAt
                if (since in 0f..0.4f) {
                    val r = since / 0.4f
                    drawOval(
                        TapestryGreen.copy(alpha = (1f - r) * fade * 0.7f),
                        topLeft = Offset(x - (6f + r * 18f), geometry.groundY + srng.nextFloat() * (geometry.impactBottomY - geometry.groundY) - (2f + r * 5f)),
                        size = Size((6f + r * 18f) * 2f, (2f + r * 5f) * 2f),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
        is FrostFlourishGeometry -> {
            // Rime that creeps out from the ground rather than snapping on as a slab: the frozen
            // band grows with progress, and six-armed frost crystals bloom across it.
            val creep = (p * 2.5f).coerceAtMost(1f)
            drawRect(
                TapestryBlue.copy(alpha = fade * 0.5f),
                topLeft = Offset(fieldRect.left, geometry.bandTopY),
                size = Size(
                    fieldRect.width,
                    (fieldRect.bottom - geometry.bandTopY) * creep
                )
            )
            val rng = kotlin.random.Random(1067L)
            repeat(34) {
                val x = geometry.crystalRect.left +
                    rng.nextFloat() * geometry.crystalRect.width
                val y = geometry.crystalRect.top +
                    rng.nextFloat() * geometry.crystalRect.height
                val bloomAt = rng.nextFloat() * 0.5f
                if (p < bloomAt) return@repeat
                val r = (
                    geometry.crystalStartRadius +
                        (p - bloomAt) * geometry.crystalMaxRadius * 2.25f
                    ).coerceAtMost(geometry.crystalMaxRadius)
                repeat(3) { arm ->
                    val a = arm * (Math.PI.toFloat() / 3f)
                    val dx = kotlin.math.cos(a) * r
                    val dy = kotlin.math.sin(a) * r * 0.5f
                    drawLine(
                        Color.White.copy(alpha = fade * 0.8f),
                        Offset(x - dx, y - dy), Offset(x + dx, y + dy),
                        strokeWidth = 2f, cap = StrokeCap.Round
                    )
                }
            }
        }
        }
    }
}

private fun drawTapestryBorder(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    isTop: Boolean,
    textHeadline: String,
    motifSeed: Long
) {
    val h = scope.size.height
    val w = scope.size.width
    val borderH = TAPESTRY_BORDER_BAND_PX
    
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
    val statusText = if (isWin) "Vanquished" else if (uiState.isRetired) "Retired" else "Perished"
    // How far he got is the number people actually compare, and it was the one stat missing.
    androidCanvas.drawText(
        "Status: " + statusText + " | Level: " + uiState.level +
            " | Score: " + uiState.score + " | Kills: " + uiState.totalKills,
        width / 2f, 150f, paint
    )
    
    val wpnBase = uiState.weaponHead.itemName + " on a " + uiState.weaponHandle.itemName
    val wpnName = if (player.extraAttachments.isNotEmpty()) {
        player.extraAttachments.joinToString(", ") { it.itemName } + " attached to " + wpnBase
    } else wpnBase
    
    // StaticLayout handles the center alignment of lines, but TextPaint itself must be LEFT aligned
    val textPaint = android.text.TextPaint(paint).apply { 
        textSize = 24f 
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val wielding = if (uiState.weaponHead.id == "head_bare" && uiState.weaponHandle.id == "handle_fists") {
        "Wielding: Bare Hands (Brawler)"
    } else {
        "Wielding: $wpnName"
    }
    // The epitaph rides as a second line in the same StaticLayout — it already wraps and centres,
    // and two lines at 24px still clear the portrait box that starts at y=260.
    val epitaph = if (isWin) null else
        com.example.game.FlavourText.slainByLine(uiState.slainByName, uiState.slainByWeapon)
    val wpnText = if (epitaph != null) "$wielding\n$epitaph" else wielding
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
                isMounted = false, isChariot = false, isStilts = false, isLord = false, // mounts shift the rider up and out of the crop
                // Clean portrait: just the man's face, no wounds/blood/bandages on the share image.
                hp = player.maxHp, missingArm = false, bleedDuration = 0f, poisonDuration = 0f, diseaseDuration = 0f, bandagesCount = 0
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

    val subPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#3B291A")
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.SERIF
        textAlign = android.graphics.Paint.Align.CENTER
        alpha = 180
    }

    // Weapon attachments
    val attachNames = player.extraAttachments.take(4).map { it.itemName }
    if (attachNames.isNotEmpty()) {
        androidCanvas.drawText("Attachments: " + attachNames.joinToString(", "), width / 2f, 770f, subPaint)
    }

    // The retinue and relics he rode out with — the ancillaries, which the tale was missing
    val ancNames = uiState.unlockedAncillaries.map { it.ancillaryName } +
        uiState.divineWeathers.map { it.label }
    if (ancNames.isNotEmpty()) {
        val ancLayout = android.text.StaticLayout.Builder.obtain(
            "Retinue: " + ancNames.joinToString(", "),
            0, "Retinue: ".length + ancNames.joinToString(", ").length,
            android.text.TextPaint(subPaint).apply { textAlign = android.graphics.Paint.Align.LEFT },
            width - 80
        ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
        androidCanvas.save()
        androidCanvas.translate(40f, 800f)
        ancLayout.draw(androidCanvas)
        androidCanvas.restore()
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
