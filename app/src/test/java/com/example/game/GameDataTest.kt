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

    private val allGearIds: Set<String> =
        (GameData.WEAPON_HEADS.map { it.id } +
         GameData.WEAPON_HANDLES.map { it.id } +
         GameData.SHIELDS.map { it.id } +
         GameData.ARMOR_PIECES.map { it.id } +
         GameData.HEADGEAR_PIECES.map { it.id }).toSet()

    @Test
    fun everyGuaranteedStartingIdResolvesToRealGear() {
        // These are the ids startNewGame always seeds. A typo here is silent: the add is a no-op
        // and the player simply never gets the fallback item in his pool.
        listOf("head_bare", "handle_fists", "shield_none", "armor_bare", "helm_none").forEach {
            assertTrue("$it is not a real gear id", it in allGearIds)
        }
    }
}
