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
    fun `toggleDualWield changes state correctly`() {
        val initialState = viewModel.uiState.value.isDualWielding
        viewModel.toggleDualWield()
        val newState = viewModel.uiState.value.isDualWielding
        assertEquals(!initialState, newState)
    }
}
