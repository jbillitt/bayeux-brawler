package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        viewModel = GameViewModel()
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value
        assertEquals(1, state.level)
        assertFalse(state.isBattleActive)
    }

    @Test
    fun `startBattle triggers battle state`() {
        viewModel.startBattle()
        val state = viewModel.uiState.value
        assertTrue(state.isBattleActive)
    }

    @Test
    fun `startBattle generates enemies without crashing`() {
        // Arrange
        // (ViewModel is already initialized)

        // Act
        // This should invoke generateRandomSaxon inside startBattle.
        // If it throws NoSuchElementException (due to empty filtered gear lists), the test will fail.
        viewModel.startBattle()
        val state = viewModel.uiState.value

        // Assert
        assertTrue("Battle should be active", state.isBattleActive)
        
        val enemies = viewModel.enemiesState.value
        assertTrue("Enemies should be generated", enemies.isNotEmpty())
        
        // Verify the enemy has valid equipment
        val firstEnemy = enemies.first()
        assertTrue("Enemy should have a valid weapon head", firstEnemy.weaponHead.id.isNotEmpty())
        assertTrue("Enemy should have a valid armor", firstEnemy.armor.id.isNotEmpty())
    }

    @Test
    fun `fists preselected never equip a hilt at battle start`() {
        val bareHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
        viewModel.selectGear(bareHead)

        viewModel.startBattle()

        val player = viewModel.playerState.value!!
        assertEquals("head_bare", player.weaponHead.id)
        assertEquals("handle_fists", player.weaponHandle.id)
    }

    // Reflection helper: the endBattle(won) that flags a run as lost is private, and the
    // random new-attempt loadout (the bug's actual home, ~GameViewModel.kt:1117) only runs
    // from dismissBattleResult() once battleLost is true. There's no public way to lose a
    // battle deterministically, so we invoke the private method directly.
    private fun loseBattle() {
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(viewModel, false)
    }

    @Test
    fun `random loadout after losing never equips a hilt with bare fists`() {
        // The next-attempt gear randomiser picks weaponHead and weaponHandle independently
        // and can roll head_bare with a non-fists handle. Repeat to reliably surface it.
        repeat(50) {
            viewModel.startBattle()
            loseBattle()
            viewModel.dismissBattleResult()

            val state = viewModel.uiState.value
            if (state.weaponHead.id == "head_bare") {
                assertEquals("handle_fists", state.weaponHandle.id)
            }
        }
    }

    @Test
    fun `toggleDualWield changes state correctly`() {
        viewModel.selectGear(com.example.game.GameData.WEAPON_HEADS.first { it.id == "head_sword" })
        val initialState = viewModel.uiState.value.isDualWielding
        viewModel.toggleDualWield()
        val newState = viewModel.uiState.value.isDualWielding
        assertEquals(!initialState, newState)
    }

    private fun mutateState(mutator: (BattleSimState) -> BattleSimState) {
        val field = GameViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<BattleSimState>
        flow.value = mutator(flow.value)
    }

    private fun foes() = viewModel.enemiesState.value.filter { !it.isPlayer && !it.isDead && !it.isDying }

    @Test
    fun `divine bolt smites the mightiest foe`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.LIGHTNING)) }
        viewModel.startBattle()
        val mightiest = foes().maxByOrNull { it.hp }!!
        val before = mightiest.hp
        viewModel.triggerWeather("weather_lightning")
        assertTrue("the heavens did nothing", mightiest.hp < before || mightiest.isDying)
    }

    @Test
    fun `the deluge sweeps the furthest foes off the field`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.FLOOD)) }
        viewModel.startBattle()
        val before = foes()
        val furthest = before.maxByOrNull { it.posX }!!
        viewModel.triggerWeather("weather_flood")
        assertTrue("furthest foe was not swept away", furthest.isDying || furthest.isDead)
        assertEquals(DeathType.KNOCKED_FLYING, furthest.deathType)
        assertTrue("swept foe has no wash-away velocity", furthest.velocityX > 0f)
    }

    @Test
    fun `hail batters every foe to the ground`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.HAIL)) }
        viewModel.startBattle()
        val before = foes()
        viewModel.triggerWeather("weather_hail")
        assertTrue("hail left someone standing", before.all { it.isCrumpled && it.crumpleDuration > 0f })
    }

    @Test
    fun `killing frost slows the whole host`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.FROST)) }
        viewModel.startBattle()
        val before = foes()
        viewModel.triggerWeather("weather_frost")
        assertTrue("frost slowed nobody", before.all { it.slowDuration > 0f })
    }

    @Test
    fun `a weather cannot be called twice until it recharges, and battle start recharges it`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.HAIL)) }
        viewModel.startBattle()
        viewModel.triggerWeather("weather_hail")
        assertTrue("no cooldown after use", viewModel.uiState.value.weatherCooldowns["weather_hail"]!! > 0f)

        // Second call must be a no-op: everyone gets back up, and hail does not re-crumple them
        val foe = foes().first()
        foe.isCrumpled = false
        foe.crumpleDuration = 0f
        viewModel.triggerWeather("weather_hail")
        assertFalse("hail fired while still cooling down", foe.isCrumpled)

        // Next battle: the charge is full again (startBattle no-ops while one is still running)
        mutateState { it.copy(isBattleActive = false) }
        viewModel.startBattle()
        assertEquals(0f, viewModel.uiState.value.weatherCooldowns["weather_hail"]!!, 0.001f)
    }

    @Test
    fun `weather is only offered late and never a third time`() {
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true

        // Too early: level 5 must never offer it
        mutateState { it.copy(level = 5, divineWeathers = emptyList()) }
        repeat(40) {
            method.invoke(viewModel, true)
            assertFalse(
                "weather offered below level $12",
                viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }
            )
            mutateState { it.copy(level = 5) }
        }

        // Already holding two: never offer a third
        mutateState { it.copy(level = 20, divineWeathers = listOf(DivineWeather.HAIL, DivineWeather.FROST)) }
        repeat(40) {
            method.invoke(viewModel, true)
            assertFalse(
                "a third weather was offered",
                viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }
            )
            mutateState { it.copy(level = 20) }
        }

        // Late and holding none: it shows up eventually
        mutateState { it.copy(level = 20, divineWeathers = emptyList()) }
        var offered = false
        repeat(80) {
            method.invoke(viewModel, true)
            if (viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }) offered = true
            mutateState { it.copy(level = 20, divineWeathers = emptyList()) }
        }
        assertTrue("weather never offered at level 20", offered)
    }

    @Test
    fun `chariot collapses if armor is too heavy`() {
        // headgear must be pinned: BattleSimState defaults it to a *random* piece (0-6kg),
        // which silently decided whether this loadout crossed the weight limit
        mutateState { it.copy(
            unlockedAncillaries = setOf(Ancillary.CHARIOT),
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif")
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertFalse(player.isChariot)
        val bgObjects = viewModel.uiState.value.backgroundObjects
        assertTrue(bgObjects.any { it.type == BackgroundObjectType.BROKEN_CHARIOT })
    }

    @Test
    fun `silken garments prevents chariot collapse`() {
        mutateState { it.copy(
            unlockedAncillaries = setOf(Ancillary.CHARIOT),
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif"),
            hasSilkenGarments = true
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertTrue(player.isChariot)
    }

    @Test
    fun `silken_garments appears in level up choices if over limit`() {
        mutateState { it.copy(
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif"),
            level = 5
        )}
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        var found = false
        repeat(50) {
            method.invoke(viewModel, true)
            println("CHOICES: " + viewModel.uiState.value.pendingLevelUpChoices.map { it.id })
            val choices = viewModel.uiState.value.pendingLevelUpChoices
            if (choices.any { it.id == "silken_garments" }) {
                found = true
            }
            viewModel.selectLevelUpChoice(choices.first().id)
        }
        assertTrue(found)
    }

    @Test
    fun `battle starts with selected mount when multiple mounts unlocked`() {
        mutateState { it.copy(
            unlockedAncillaries = setOf(Ancillary.CHARIOT, Ancillary.WARHORSE),
            activeMount = Ancillary.WARHORSE
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertFalse(player.isChariot)
        assertTrue(player.isMounted)
        assertEquals(80f, player.mountHp)
    }
}
