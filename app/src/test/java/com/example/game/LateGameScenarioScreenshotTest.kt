package com.example.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.MainBayeuxGameScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mid and late game, composed, with the things added this session actually on the field: the
 * diggers, Tiny Terrence, the decoy, a barrow-king, a crowded late host.
 *
 * These are reference images to be LOOKED at — the unit tests prove the rules, this proves the
 * rules produce something that survives being drawn. Not golden assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class LateGameScenarioScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    private fun shoot(vm: GameViewModel, file: String) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MainBayeuxGameScreen(viewModel = vm, musicOn = false, onToggleMusic = {})
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$file")
    }

    /** Level 34: a full late host, the diggers surfaced behind them, Terrence away after the bows. */
    @Test
    fun lateBattleWithDiggersAndSkirmisher() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(
                level = 34,
                unlockedAncillaries = listOf(
                    Ancillary.SAPPER, Ancillary.MOLEMAN, Ancillary.TINY_TERRENCE,
                    Ancillary.TINY_TERRENCE, Ancillary.WARDOG
                ),
                hasPointierSticks = true, hasGreasedWeapon = true, hasFeltShoes = true
            )
        }
        vm.startBattle()
        advance(vm, 6.5f) // past BURROW so both diggers are up behind the line
        vm.setPaused(true)
        shoot(vm, "late_diggers.png")
    }

    /** Mid-run siege: diggers come up behind the wall, where the relief column is queued. */
    @Test
    fun siegeWithBurrowersBehindTheWall() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(
                level = 21,
                unlockedAncillaries = listOf(Ancillary.SAPPER, Ancillary.MOLEMAN, Ancillary.TROJAN_HORSE)
            )
        }
        vm.startBattle()
        advance(vm, 6.5f)
        vm.setPaused(true)
        mutate(vm) { it.copy(cameraX = 1400f) }
        shoot(vm, "late_siege_burrowers.png")
    }

    /** A barrow-king at 130+, where the hurl and the stacked resistances all apply at once. */
    @Test
    fun barrowKingLateBoss() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(level = 130, unlockedAncillaries = listOf(Ancillary.MOLEMAN, Ancillary.TINY_TERRENCE))
        }
        vm.startBattle()
        advance(vm, 5f)
        vm.setPaused(true)
        mutate(vm) { it.copy(cameraX = 700f) }
        shoot(vm, "late_barrow_king.png")
    }

    /** The decoy, mid-run, now that the host will actually attack it. */
    @Test
    fun theDecoyBeingAttacked() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(level = 18, unlockedAncillaries = listOf(Ancillary.TROJAN_HORSE, Ancillary.SAPPER))
        }
        vm.startBattle()
        advance(vm, 4f)
        vm.setPaused(true)
        shoot(vm, "late_decoy.png")
    }
}
