package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDataTest {

    @Test
    fun testSizePresetsAreNotDeleted() {
        // The user specifically requested that we do not delete size options.
        // We originally built 5 size presets: Petite, Lean, Standard, Brawny, Huge
        // (represented internally as tiny, small, medium, large, huge)
        
        assertEquals("There should be exactly 5 size presets available", 5, SIZE_PRESETS.size)
        
        val presetIds = SIZE_PRESETS.map { it.id }
        assertTrue(presetIds.contains("tiny"))
        assertTrue(presetIds.contains("small"))
        assertTrue(presetIds.contains("medium"))
        assertTrue(presetIds.contains("large"))
        assertTrue(presetIds.contains("huge"))
    }

    @Test
    fun testWeaponHeadsNotDeleted() {
        // Validate we have all the weapons from Phase 1 Data Layer
        assertTrue(GameData.WEAPON_HEADS.size >= 16)
        val ids = GameData.WEAPON_HEADS.map { it.id }
        
        val requiredIds = listOf(
            "head_bare", "head_dagger",
            "head_sword", "head_axe", "head_mace", "head_spear",
            "head_javelin", "head_pike", "head_flail", "head_war_flail",
            "head_maul", "head_halberd", "head_claymore", "head_bow",
            "head_crossbow", "head_scythe", "head_morningstar", "head_slingshot", "head_longbow"
        )
        
        requiredIds.forEach { id ->
            assertTrue("Missing weapon head: $id", ids.contains(id))
        }
    }
}
