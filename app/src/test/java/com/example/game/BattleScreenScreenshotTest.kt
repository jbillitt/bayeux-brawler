package com.example.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import com.example.MainBayeuxGameScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The WHOLE battle screen — header bar, borders, battlefield — at landscape phone size, with a
 * real GameViewModel driving it and the camera already panned away from the start of the level.
 *
 * Every other screenshot test renders one piece in isolation, which is why "the weather does not
 * fill the screen and gets left behind as the camera moves" survived a fix: drawWeatherFlourish
 * demonstrably fills whatever canvas it is handed (WeatherFlourishScreenshotTest), and the
 * question was never about the canvas — it was about how much of the screen the canvas is, and
 * where it sits once the world has scrolled. That can only be answered composed.
 *
 * Not a golden-image assertion. Open `app/src/test/screenshots/battle_*.png` and look.
 *
 * gradle testDebugUnitTest --tests "*BattleScreenScreenshotTest*" "-Proborazzi.test.record=true"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class BattleScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Steps the real simulation directly. The coroutine game loop does not run under Robolectric,
     * and the whole point of this file is the state AFTER the camera has moved, so the ticks are
     * driven by hand — the same private entry point GameViewModelTest uses.
     */
    private fun advance(vm: GameViewModel, seconds: Float) {
        val step = GameViewModel::class.java.getDeclaredMethod(
            "updateSimulation", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        repeat((seconds * 30f).toInt()) { step.invoke(vm, 0.033f) }
    }

    private fun mutate(vm: GameViewModel, mutator: (BattleSimState) -> BattleSimState) {
        val field = GameViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<BattleSimState>
        flow.value = mutator(flow.value)
    }

    /**
     * startBattle also launches the coroutine game loop, and the compose test rule pumps the main
     * looper — so without pausing, the battle keeps resolving underneath the screenshot and every
     * capture came out as the victory banner.
     */
    private fun battleInProgress(): GameViewModel = GameViewModel().apply {
        startBattle()
        advance(this, 1.2f)
        setPaused(true)
    }

    @Test
    fun weatherWashesTheScreenAfterTheCameraHasPanned() {
        val vm = battleInProgress()
        // Weather is a reward, so a level-1 run holds none: grant one, and shove the camera well
        // clear of the start of the level, which is the state the complaint is about.
        mutate(vm) {
            it.copy(
                divineWeathers = listOf(DivineWeather.LIGHTNING),
                weatherCooldowns = emptyMap(),
                // A short simulated battle can already be over, and a finished battle refuses a
                // weather charge — hold it open so the flourish always has something to wash.
                isBattleActive = true, battleWon = false, battleLost = false,
                cameraX = 600f
            )
        }
        vm.triggerWeather(DivineWeather.LIGHTNING.id)
        // The flourish lives 1.2 REAL seconds and the overlay clocks itself off frame callbacks;
        // let the test clock run and it expires while the rule waits for idle, capturing nothing.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_weather_composed.png")
    }

    /**
     * The same flourish with the camera still at the start of the level. If the washed region
     * lands in the same place as in the panned shot, the cut-off is a fixed clip; if it moves,
     * the flourish is riding the world transform after all.
     */
    @Test
    fun weatherWithTheCameraStillAtTheStart() {
        val vm = battleInProgress()
        mutate(vm) {
            it.copy(
                divineWeathers = listOf(DivineWeather.LIGHTNING),
                weatherCooldowns = emptyMap(),
                // A short simulated battle can already be over, and a finished battle refuses a
                // weather charge — hold it open so the flourish always has something to wash.
                isBattleActive = true, battleWon = false, battleLost = false,
                cameraX = 0f
            )
        }
        vm.triggerWeather(DivineWeather.LIGHTNING.id)
        // The flourish lives 1.2 REAL seconds and the overlay clocks itself off frame callbacks;
        // let the test clock run and it expires while the rule waits for idle, capturing nothing.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_weather_camera0.png")
    }

    /** Throne mode, in the real battle: the litter's poles have to sit on the bearers' shoulders. */
    @Test
    fun theThroneOnItsBearers() {
        val vm = GameViewModel().apply {
            toggleThroneMode()
            startBattle()
            advance(this, 0.6f)
            setPaused(true)
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_throne.png")
    }

    /**
     * A hill battle. The world backdrop is pre-rendered into a bitmap and then shifted DOWN by the
     * camera's hill lift, so anything the bitmap clipped at its own top edge — a tall tree — used
     * to end in a flat horizontal cut halfway up the screen.
     */
    @Test
    fun hillBattleKeepsTheTopsOfItsTrees() {
        val vm = GameViewModel()
        vm.startBattle()
        advance(vm, 0.6f)
        vm.setPaused(true)
        mutate(vm) {
            it.copy(
                hillState = HillField.stateFor(1066L, 9, it.levelWidth),
                backgroundObjects = FieldScenery.objectsFor(1066L, 9, it.levelWidth)
            )
        }
        // Stand the player near the crest, where the camera lift is at its largest.
        vm.playerState.value?.let { p ->
            p.posX = vm.uiState.value.levelWidth * 0.75f
            p.terrainLiftY = HillField.liftAt(p.posX, vm.uiState.value.hillState)
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_hill.png")
    }

    /** Finger held on a fighter: his exact hit points, on a scrap of linen, until it lifts. */
    @Test
    fun holdingAFighterShowsHisHealth() {
        val vm = battleInProgress()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        val target = vm.enemiesState.value.firstOrNull { !it.isPlayer && !it.isDead }
        composeTestRule.onNodeWithTag("bayeux_tapestry_canvas").performTouchInput {
            // The canvas is 1000 world units wide; press where that enemy is standing.
            val fx = ((target?.posX ?: 500f) / 1000f).coerceIn(0.05f, 0.95f)
            down(androidx.compose.ui.geometry.Offset(width * fx, height * 0.5f))
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_health_hold.png")
    }

    /** The same screen with no weather, as the reference for what "the whole screen" is. */
    @Test
    fun theComposedBattleScreen() {
        val vm = battleInProgress()
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/battle_composed.png")
    }
}
