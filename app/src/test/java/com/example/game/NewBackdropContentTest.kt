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
                    id = "wall", name = "wall", isPlayer = false, maxHp = 1f, hp = 1f,
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
            assertEquals(BattlegroundTheme.FEASTING_HALL, BattlegroundContent.themeFor(42L, level))
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
        val expected = mapOf(
            BattlegroundTheme.FEASTING_HALL to BackgroundObjectType.FEASTING_HALL,
            BattlegroundTheme.FLEET_CROSSING to BackgroundObjectType.FLEET_CROSSING,
            BattlegroundTheme.MONT_SAINT_MICHEL to BackgroundObjectType.MONT_SAINT_MICHEL
        )
        val seen = (1L..500L)
            .map { BattlegroundContent.themeFor(it, 20) }
            .toSet()
        assertEquals(expected.keys, seen)
        expected.forEach { (theme, type) ->
            val seed = (1L..500L).first { BattlegroundContent.themeFor(it, 20) == theme }
            assertEquals(type, BattlegroundContent.objectsFor(seed, 20, 2500f).single().type)
        }
    }

    @Test
    fun `Hardrada receives the Stamford Bridge backdrop through standard objects`() {
        val objects = BattlegroundContent.objectsForBattle(1066L, 20, 2500f, 0f)
        assertEquals(listOf(BackgroundObjectType.STAMFORD_BRIDGE), objects.map { it.type })
        assertEquals(238f, SiegeRules.parapetFeetY(1f), 0f)
    }
}
