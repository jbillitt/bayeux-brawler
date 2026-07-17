package com.example.game

import kotlin.random.Random

enum class BattlegroundTheme {
    FEASTING_HALL,
    FLEET_CROSSING,
    MONT_SAINT_MICHEL
}

object BattlegroundContent {
    fun themeFor(gameSeed: Long, level: Int): BattlegroundTheme {
        val pool = when {
            level < 7 -> listOf(BattlegroundTheme.FEASTING_HALL)
            level < 14 -> listOf(BattlegroundTheme.FEASTING_HALL, BattlegroundTheme.FLEET_CROSSING)
            else -> BattlegroundTheme.entries.toList()
        }
        return pool[Random(gameSeed + level).nextInt(pool.size)]
    }

    fun objectsFor(gameSeed: Long, level: Int, levelWidth: Float): List<BackgroundObject> {
        val theme = themeFor(gameSeed, level)
        val type = when (theme) {
            BattlegroundTheme.FEASTING_HALL -> BackgroundObjectType.FEASTING_HALL
            BattlegroundTheme.FLEET_CROSSING -> BackgroundObjectType.FLEET_CROSSING
            BattlegroundTheme.MONT_SAINT_MICHEL -> BackgroundObjectType.MONT_SAINT_MICHEL
        }
        val random = Random(gameSeed + level)
        val width = when (theme) {
            BattlegroundTheme.FEASTING_HALL -> 500f
            BattlegroundTheme.FLEET_CROSSING -> 560f
            BattlegroundTheme.MONT_SAINT_MICHEL -> 540f
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
