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
 * A composed look at everything built this session, at the stage of the run where it actually
 * appears. Reference images to be opened and judged, not golden assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class SessionFeatureAuditTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun advance(vm: GameViewModel, seconds: Float) {
        val step = GameViewModel::class.java.getDeclaredMethod(
            "updateSimulation", Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        repeat((seconds * 30f).toInt()) { step.invoke(vm, 0.033f) }
    }

    private fun mutate(vm: GameViewModel, m: (BattleSimState) -> BattleSimState) {
        val f = GameViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = f.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<BattleSimState>
        flow.value = m(flow.value)
    }

    private fun shoot(vm: GameViewModel, file: String) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { MainBayeuxGameScreen(vm, musicOn = false, onToggleMusic = {}) }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$file")
    }

    /**
     * A GUARANTEED siege — the schedule is seeded, so a fixed level is a coin toss. Checks the
     * garrison stands ON the wall with heads and bodies inside the canvas, which is what the
     * -150 parapet offset made impossible.
     */
    @Test
    fun siegeWallGarrisonIsFullyOnScreen() {
        val seed = MedievalHarpPlayer.gameSeed
        val siegeLevel = (5..80).firstOrNull { SiegeSchedule.isSiegeLevel(seed, it) } ?: 12
        val vm = GameViewModel()
        mutate(vm) { it.copy(level = siegeLevel) }
        vm.startBattle()
        advance(vm, 1.5f)
        vm.setPaused(true)
        mutate(vm) { it.copy(cameraX = 1500f) }
        shoot(vm, "audit_siege_parapet.png")
    }

    /** Mid-dig: both burrowers should be sinking at the dig site, throwing up spoil. */
    @Test
    fun diggersMidDescent() {
        val vm = GameViewModel()
        mutate(vm) { it.copy(level = 24, unlockedAncillaries = listOf(Ancillary.SAPPER, Ancillary.MOLEMAN)) }
        vm.startBattle()
        advance(vm, 0.5f) // inside DIG_DOWN_SECS
        vm.setPaused(true)
        shoot(vm, "audit_digging.png")
    }

    /** Pointier Sticks: half the melee entourage must be carrying a real spearhead. */
    @Test
    fun entourageWithPointierSticks() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(
                level = 16, hasPointierSticks = true,
                unlockedAncillaries = listOf(
                    Ancillary.SQUIRE, Ancillary.HERALD, Ancillary.CUPBEARER,
                    Ancillary.MONK, Ancillary.FANATIC, Ancillary.SURGEON
                )
            )
        }
        vm.startBattle()
        advance(vm, 1.2f)
        vm.setPaused(true)
        shoot(vm, "audit_pointier_sticks.png")
    }

    /** A fully upgraded bow: binding, chevrons, cord wrap, pennon, extra strings, cluster pots. */
    @Test
    fun fullyEvolvedRangedWeapon() {
        val vm = GameViewModel()
        mutate(vm) {
            it.copy(
                level = 30,
                weaponHead = GameData.WEAPON_HEADS.first { h -> h.id == "head_longbow" },
                weaponHandle = GameData.WEAPON_HANDLES.first { h -> h.id == "handle_fists" },
                rangedUpgrades = listOf(
                    "bow_bigger", "bow_spikes", "bow_weapon_heads",
                    "volley", "multishot_triple", "cluster"
                )
            )
        }
        vm.startBattle()
        advance(vm, 2.5f) // long enough for volley + multishot to be in the air
        vm.setPaused(true)
        shoot(vm, "audit_ranged_evolved.png")
    }

    /** Snails, attacking. Their bite animation and their new bulk. */
    @Test
    fun snailsInCombat() {
        val vm = GameViewModel()
        mutate(vm) { it.copy(level = 40) }
        vm.startBattle()
        val enemies = vm.enemiesState.value
        enemies.take(3).forEachIndexed { i, e ->
            e.posX = 300f + i * 90f
            e.isAttacking = true
            e.swingProgress = 0.5f
        }
        advance(vm, 0.2f)
        vm.setPaused(true)
        shoot(vm, "audit_snails.png")
    }

    private fun weatherShot(w: DivineWeather, file: String) {
        val vm = GameViewModel()
        mutate(vm) { it.copy(level = 28) }
        vm.startBattle()
        advance(vm, 1.2f)
        vm.setPaused(true)
        mutate(vm) {
            it.copy(
                divineWeathers = listOf(w), weatherCooldowns = emptyMap(),
                isBattleActive = true, battleWon = false, battleLost = false, cameraX = 900f
            )
        }
        vm.triggerWeather(w.id)
        shoot(vm, file)
    }

    @Test fun weatherLightning() = weatherShot(DivineWeather.LIGHTNING, "audit_weather_lightning.png")
    @Test fun weatherFlood() = weatherShot(DivineWeather.FLOOD, "audit_weather_flood.png")
    @Test fun weatherHail() = weatherShot(DivineWeather.HAIL, "audit_weather_hail.png")
    @Test fun weatherFrost() = weatherShot(DivineWeather.FROST, "audit_weather_frost.png")
    @Test fun weatherFrogs() = weatherShot(DivineWeather.FROGS, "audit_weather_frogs.png")
}
