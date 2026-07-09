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
@Config(sdk = [36])
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
    fun `toggleDualWield changes state correctly`() {
        val initialState = viewModel.uiState.value.isDualWielding
        viewModel.toggleDualWield()
        val newState = viewModel.uiState.value.isDualWielding
        assertEquals(!initialState, newState)
    }
}
