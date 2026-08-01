package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBackdropContentTest {
    @Test
    fun `siege levels select wall live gate and motte set`() {
        val seed = 1066L
        val siegeLevel = SiegeSchedule.levels(seed, 40).first()

        val objects = BattlegroundContent.objectsForBattle(seed, siegeLevel, 2500f, 400f)

        assertEquals(
            setOf(
                BackgroundObjectType.CASTLE_WALL,
                BackgroundObjectType.CASTLE_GATE,
                BackgroundObjectType.MOTTE
            ),
            objects.map { it.type }.toSet()
        )
        assertTrue(objects.single { it.type == BackgroundObjectType.CASTLE_GATE }.type.isLiveBackgroundObject())
        assertFalse(objects.single { it.type == BackgroundObjectType.CASTLE_WALL }.type.isLiveBackgroundObject())
        assertFalse(objects.single { it.type == BackgroundObjectType.MOTTE }.type.isLiveBackgroundObject())
    }

    @Test
    fun `castle gate damage thresholds cover intact splintered shattered and breached`() {
        assertEquals(CastleGateDamageState.INTACT, castleGateDamageState(100f, 100f))
        assertEquals(CastleGateDamageState.INTACT, castleGateDamageState(66.01f, 100f))
        assertEquals(CastleGateDamageState.SPLINTERED, castleGateDamageState(66f, 100f))
        assertEquals(CastleGateDamageState.SPLINTERED, castleGateDamageState(33.01f, 100f))
        assertEquals(CastleGateDamageState.SHATTERED, castleGateDamageState(33f, 100f))
        assertEquals(CastleGateDamageState.BREACHED, castleGateDamageState(0f, 100f))
        assertEquals(
            SiegeRules.PARAPET_ELEVATION_OFFSET,
            elevationVisualOffset(
                FighterState(
                    id = FighterId("wall"), name = "wall", isPlayer = false, maxHp = 1f, hp = 1f,
                    weaponHead = GameData.WeaponHead.BOW,
                    weaponHandle = GameData.WeaponHandle.LONG,
                    shield = GameData.Shield.NONE,
                    armor = GameData.ArmorPiece.PADDED,
                    headgear = GameData.HeadgearPiece.NONE,
                    posX = 0f, targetX = 0f, elevated = true
                )
            ),
            0f
        )
    }

    @Test
    fun `battleground mapping is deterministic and respects unlocked level bands`() {
        for (level in 2..40) {
            assertEquals(
                BattlegroundContent.themeFor(8675309L, level),
                BattlegroundContent.themeFor(8675309L, level)
            )
        }
        for (level in 2..6) {
            // Early bands are field, village or an interior room (no Mont-Saint-Michel yet).
            assertTrue(
                BattlegroundContent.themeFor(42L, level) in
                    listOf(BattlegroundTheme.FIELD, BattlegroundTheme.VILLAGE, BattlegroundTheme.INTERIOR)
            )
        }
        for (seed in 1L..100L) {
            assertNotEquals(
                BattlegroundTheme.MONT_SAINT_MICHEL,
                BattlegroundContent.themeFor(seed, 10)
            )
        }
    }

    @Test
    fun `every new battleground theme maps to its exhaustive renderer type`() {
        val seen = (1L..500L)
            .map { BattlegroundContent.themeFor(it, 20) }
            .toSet()
        assertEquals(BattlegroundTheme.entries.toSet(), seen)

        val montSeed = (1L..500L).first { BattlegroundContent.themeFor(it, 20) == BattlegroundTheme.MONT_SAINT_MICHEL }
        assertEquals(
            BackgroundObjectType.MONT_SAINT_MICHEL,
            BattlegroundContent.objectsFor(montSeed, 20, 2500f).single().type
        )

        // INTERIOR is one procedurally chosen room.
        val interiorSeed = (1L..500L).first { BattlegroundContent.themeFor(it, 20) == BattlegroundTheme.INTERIOR }
        assertTrue(
            BattlegroundContent.objectsFor(interiorSeed, 20, 2500f).single().type in listOf(
                BackgroundObjectType.FEASTING_HALL,
                BackgroundObjectType.INTERIOR_KITCHEN,
                BackgroundObjectType.INTERIOR_CHAMBER
            )
        )

        // VILLAGE has no centrepiece — its buildings come from the scatter pass in startBattle.
        val villageSeed = (1L..500L).first { BattlegroundContent.themeFor(it, 20) == BattlegroundTheme.VILLAGE }
        assertTrue(BattlegroundContent.objectsFor(villageSeed, 20, 2500f).isEmpty())
        // The open field is scattered scenery, not one structure.
        val fieldSeed = (1L..500L).first { BattlegroundContent.themeFor(it, 20) == BattlegroundTheme.FIELD }
        val fieldObjs = BattlegroundContent.objectsFor(fieldSeed, 20, 2500f)
        assertTrue(fieldObjs.isNotEmpty() && fieldObjs.all {
            it.type in listOf(
                BackgroundObjectType.FIELD_TREE, BackgroundObjectType.FIELD_GRASS,
                BackgroundObjectType.BUILDING_BOSHAM, BackgroundObjectType.BUILDING_MANOR
            )
        })
    }

    @Test
    fun `Hardrada receives the Stamford Bridge backdrop through standard objects`() {
        val objects = BattlegroundContent.objectsForBattle(1066L, 20, 2500f, 0f)
        assertEquals(listOf(BackgroundObjectType.STAMFORD_BRIDGE), objects.map { it.type })
        // Derived, not a magic 208: that literal pinned the old -150 parapet offset, which put a
        // defender's head off the top of the canvas. What actually matters is that the walkway
        // follows the constant, and that a man standing on it fits on screen — a figure is about
        // 190px tall from his feet, so his crown must stay below the top edge.
        assertEquals(
            200f + SiegeRules.GROUND_FEET_OFFSET + SiegeRules.PARAPET_ELEVATION_OFFSET,
            SiegeRules.parapetFeetY(1f),
            0f
        )
        assertTrue(
            "a defender on the wall must fit on screen: feet at ${SiegeRules.parapetFeetY(1f)}",
            SiegeRules.parapetFeetY(1f) - 190f > 0f
        )
    }
}
