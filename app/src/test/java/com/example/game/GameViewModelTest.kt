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
        val initialState = viewModel.uiState.value.isDualWielding
        viewModel.toggleDualWield()
        val newState = viewModel.uiState.value.isDualWielding
        assertEquals(!initialState, newState)
    }
}
