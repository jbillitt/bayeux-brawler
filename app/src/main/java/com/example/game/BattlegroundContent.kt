package com.example.game

import kotlin.random.Random

enum class BattlegroundTheme {
    // INTERIOR replaces the old free-standing FEASTING_HALL theme: one procedurally chosen room
    // (feasting hall, kitchen or chamber) with no exterior buildings spawning inside it.
    INTERIOR,
    /** The classic open map: scattered buildings/forts only, no trees, no hall. */
    VILLAGE,
    MONT_SAINT_MICHEL,
    FIELD
}

object BattlegroundContent {
    fun themeFor(gameSeed: Long, level: Int): BattlegroundTheme {
        // Fleet crossings are gone — the maps read as broken. The landing ship on level 1 stays.
        // VILLAGE is weighted double and INTERIOR down to ~1 in 5: rooms are a change of scene,
        // not the default scene.
        val pool = when {
            level < 14 -> listOf(
                BattlegroundTheme.FIELD, BattlegroundTheme.FIELD,
                BattlegroundTheme.VILLAGE, BattlegroundTheme.VILLAGE,
                BattlegroundTheme.INTERIOR
            )
            else -> listOf(
                BattlegroundTheme.FIELD, BattlegroundTheme.FIELD,
                BattlegroundTheme.VILLAGE, BattlegroundTheme.VILLAGE,
                BattlegroundTheme.INTERIOR, BattlegroundTheme.MONT_SAINT_MICHEL
            )
        }
        return pool[Random(gameSeed + level).nextInt(pool.size)]
    }

    fun objectsFor(gameSeed: Long, level: Int, levelWidth: Float): List<BackgroundObject> {
        val theme = themeFor(gameSeed, level)
        // The open field is scattered trees + long grass, not one big structure.
        if (theme == BattlegroundTheme.FIELD) return FieldScenery.objectsFor(gameSeed, level, levelWidth)
        // The village has no centrepiece — its buildings come from the scatter pass in startBattle.
        if (theme == BattlegroundTheme.VILLAGE) return emptyList()
        val random = Random(gameSeed + level)
        val type = when (theme) {
            BattlegroundTheme.INTERIOR -> listOf(
                BackgroundObjectType.FEASTING_HALL,
                BackgroundObjectType.INTERIOR_KITCHEN,
                BackgroundObjectType.INTERIOR_CHAMBER
            )[random.nextInt(3)]
            BattlegroundTheme.MONT_SAINT_MICHEL -> BackgroundObjectType.MONT_SAINT_MICHEL
            else -> BackgroundObjectType.FIELD_TREE // unreachable; handled above
        }
        val width = when (theme) {
            BattlegroundTheme.INTERIOR -> 500f
            else -> 540f
        }
        return listOf(
            BackgroundObject(
                id = "battleground_${theme.name.lowercase()}",
                type = type,
                posX = levelWidth * (0.48f + random.nextFloat() * 0.12f),
                width = width,
                hp = 1000f,
                maxHp = 1000f,
                seed = random.nextInt()
            )
        )
    }

    fun siegeObjects(level: Int, gateHp: Float): List<BackgroundObject> = listOf(
        BackgroundObject(
            id = "siege_wall",
            type = BackgroundObjectType.CASTLE_WALL,
            posX = 1800f,
            width = 780f,
            hp = 2000f,
            maxHp = 2000f,
            seed = level * 1066
        ),
        BackgroundObject(
            id = "siege_motte",
            type = BackgroundObjectType.MOTTE,
            posX = 2220f,
            width = 360f,
            hp = 1500f,
            maxHp = 1500f,
            seed = level * 1066 + 1
        ),
        BackgroundObject(
            id = "siege_gate",
            type = BackgroundObjectType.CASTLE_GATE,
            posX = 1800f,
            width = 120f,
            hp = gateHp,
            maxHp = gateHp,
            seed = level * 1066 + 2
        )
    )

    fun objectsForBattle(
        gameSeed: Long,
        level: Int,
        levelWidth: Float,
        gateHp: Float
    ): List<BackgroundObject> = when {
        BossSchedule.forLevel(level) == BossType.HARALD_HARDRADA -> listOf(
            BackgroundObject(
                id = "stamford_bridge",
                type = BackgroundObjectType.STAMFORD_BRIDGE,
                posX = levelWidth * 0.62f,
                width = 560f,
                hp = 1000f,
                maxHp = 1000f,
                seed = (gameSeed + level).toInt()
            )
        )
        SiegeSchedule.isSiegeLevel(gameSeed, level) -> siegeObjects(level, gateHp)
        else -> objectsFor(gameSeed, level, levelWidth)
    }
}
