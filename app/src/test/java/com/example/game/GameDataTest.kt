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

    @Test
    fun unlockableHandlesExistAndAreHeldOutOfTheBasePool() {
        assertTrue("gating set must not be empty", GameData.UNLOCKABLE_HANDLE_IDS.isNotEmpty())
        GameData.UNLOCKABLE_HANDLE_IDS.forEach { id ->
            assertTrue("$id is gated but is not a real handle",
                GameData.WEAPON_HANDLES.any { it.id == id })
        }
        val expected = setOf(
            "handle_oar", "handle_femur", "handle_antler", "handle_trumpet",
            "handle_wheelbarrow", "handle_anchor", "handle_plank"
        )
        assertEquals(expected, GameData.UNLOCKABLE_HANDLE_IDS)
    }

    @Test
    fun theBrigandineFillsTheGapBetweenLeatherAndLamellar() {
        val leather = GameData.ARMOR_PIECES.first { it.id == "armor_leather" }.defense
        val lamellar = GameData.ARMOR_PIECES.first { it.id == "armor_lamellar" }.defense
        val brigandine = GameData.ARMOR_PIECES.first { it.id == "armor_brigandine" }.defense
        assertTrue("brigandine ($brigandine) must sit between $leather and $lamellar",
            brigandine > leather && brigandine < lamellar)
    }

    /** A player in the given body armour, everything else held constant. */
    private fun playerWearing(armorId: String) = FighterState(
        id = "p", name = "p", isPlayer = true, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == armorId },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
        posX = 0f, targetX = 0f, size = 1.0f,
        hairColor = androidx.compose.ui.graphics.Color.Black, hairStyle = "short"
    )

    @Test
    fun everyZeroDefenceOutfitPaysAScoreMultiplier() {
        // armor_smock is the control: also near-worthless, but not a comedy outfit, so it earns
        // no bonus. Without the bonus these four would be strictly worse than fighting naked.
        val baseline = playerWearing("armor_smock").scoreMultiplier
        listOf("armor_habit", "armor_apron", "armor_frock", "armor_toga").forEach { id ->
            val piece = GameData.ARMOR_PIECES.first { it.id == id }
            assertEquals("$id should offer no protection", 0f, piece.defense, 0.001f)
            assertTrue(
                "$id offers no defence and no score bonus — strictly worse than nothing",
                playerWearing(id).scoreMultiplier > baseline
            )
        }
    }

    @Test
    fun theThreeNewLayersExistAndAreLight() {
        listOf("armor_greaves", "armor_spaulders", "armor_surcoat").forEach { id ->
            val piece = GameData.ARMOR_PIECES.first { it.id == id }
            assertTrue("$id is a layer and must not weigh like a hauberk", piece.mass <= 3.0f)
        }
    }
}
