package com.example.game

import androidx.compose.ui.graphics.Color

/** Builds Saxon foes for a given level. Pure: no ViewModel, no audio, no flows. */
object EnemyFactory {

    private val NAMES = listOf(
        "Harold of Essex", "Gyrth Shield-Cleaver", "Leofwine", "Tostig Dunce",
        "Aldred the Bald", "Godwin the Grumpy", "Sigurd Skull-Basher", "Ethelred the Unready",
        "Cerdic the Giant", "Wulfric", "Odo the Swift", "Aelfric", "Leofric", "Edric"
    )

    // Counter ids, recorded on BattleSimState.seenCounters so the matching "out" card can be offered
    const val COUNTER_SHIELD_WALL = "counter_shield_wall"
    const val COUNTER_BRUTE = "counter_brute"
    const val COUNTER_WAR_PRIEST = "counter_war_priest"

    const val SHIELD_WALL_FROM_LEVEL = 12
    const val BRUTE_FROM_LEVEL = 15
    const val WAR_PRIEST_FROM_LEVEL = 18

    private fun safeHead(id: String) = GameData.WEAPON_HEADS.firstOrNull { it.id == id } ?: GameData.WEAPON_HEADS.first()
    private fun safeHandle(id: String) = GameData.WEAPON_HANDLES.firstOrNull { it.id == id } ?: GameData.WEAPON_HANDLES.first()
    private fun safeShield(id: String) = GameData.SHIELDS.firstOrNull { it.id == id } ?: GameData.SHIELDS.first()
    private fun safeArmor(id: String) = GameData.ARMOR_PIECES.firstOrNull { it.id == id } ?: GameData.ARMOR_PIECES.first()
    private fun safeHelm(id: String) = GameData.HEADGEAR_PIECES.firstOrNull { it.id == id } ?: GameData.HEADGEAR_PIECES.first()

    /** The same curve randomSaxon uses, so counter enemies scale with the rest of the host. */
    private fun baseHpFor(level: Int) = (if (level == 1) 32f else 50f) + (level * 7f) + (level - 15).coerceAtLeast(0) * 8f

    private fun spawnX(index: Int) = 850f + (index * 130f)

    /**
     * A locked pair of tower-shield spearmen with three times the usual shield hp — the wall the
     * Shieldbreaker card exists to answer.
     */
    fun shieldWallPair(index: Int, level: Int): List<FighterState> = List(2) { i ->
        val shield = safeShield("shield_tower")
        val hp = baseHpFor(level) * 1.1f
        FighterState(
            id = "shieldwall_${index}_$i",
            name = if (i == 0) "Wall of Wessex" else "Wall of Kent",
            isPlayer = false,
            maxHp = hp, hp = hp,
            weaponHead = safeHead("head_spear"),
            weaponHandle = safeHandle("handle_medium"),
            shield = shield,
            armor = safeArmor("armor_chainmail"),
            headgear = safeHelm("helm_conical"),
            posX = spawnX(index) + i * 70f,
            targetX = spawnX(index) + i * 70f,
            facingRight = false,
            size = 1.1f,
            hairColor = Color(0xFF5A442E),
            hairStyle = "short",
            level = level,
            shieldHp = shield.defense * 6f // 3x the usual defense*2
        )
    }

    /** A slab of a man in iron scale. Armour-Piercing is the out. */
    fun armouredBrute(index: Int, level: Int): FighterState {
        val hp = baseHpFor(level) * 2.2f
        return FighterState(
            id = "brute_$index",
            name = "Cerdic the Immovable",
            isPlayer = false,
            maxHp = hp, hp = hp,
            weaponHead = safeHead("head_maul"),
            weaponHandle = safeHandle("handle_iron"),
            shield = safeShield("shield_none"),
            armor = safeArmor("armor_scale"),
            headgear = safeHelm("helm_great"),
            posX = spawnX(index),
            targetX = spawnX(index),
            facingRight = false,
            size = 1.4f,
            hairColor = Color(0xFF2C2219),
            hairStyle = "short",
            level = level,
            extraArmors = listOf(GameData.ARMOR_PIECES.first { it.id == "armor_coif" })
        )
    }

    /** Robed, unarmed, and mending his friends faster than you can cut them down. Kill him first. */
    fun warPriest(index: Int, level: Int): FighterState {
        val hp = baseHpFor(level) * 0.8f
        return FighterState(
            id = "war_priest_$index",
            name = "Brother Aethelwulf",
            isPlayer = false,
            maxHp = hp, hp = hp,
            weaponHead = safeHead("head_bare"),
            weaponHandle = safeHandle("handle_fists"),
            shield = safeShield("shield_none"),
            armor = safeArmor("armor_bare"),
            headgear = safeHelm("helm_none"),
            posX = spawnX(index) + 120f, // hangs back behind his flock
            targetX = spawnX(index) + 120f,
            facingRight = false,
            size = 1.0f,
            hairColor = Color(0xFF8A7156),
            hairStyle = "bald",
            level = level,
            isWarPriest = true
        )
    }

    fun randomSaxon(index: Int, level: Int): FighterState {
        val saxonName = if (index < NAMES.size) NAMES[index] else "Saxon Foe ${index + 1}"

        // Random.Default, not a millis-based seed: same-tick spawns were getting near-identical rolls
        val rng = kotlin.random.Random.Default
        val r = rng.nextFloat()

        val arch = if (level == 1) {
            // Level 1 is the landing beach — greenest rabble only
            if (r < 0.55f) EnemyArchetype.PEASANT else if (r < 0.9f) EnemyArchetype.FYRD_LEVY else EnemyArchetype.SLINGER
        } else if (level <= 2) {
            if (r < 0.3f) EnemyArchetype.PEASANT else if (r < 0.6f) EnemyArchetype.FYRD_LEVY else if (r < 0.8f) EnemyArchetype.SLINGER else EnemyArchetype.HOUSECARL
        } else if (level <= 4) {
            if (r < 0.2f) EnemyArchetype.PEASANT else if (r < 0.35f) EnemyArchetype.JAVELINEER else if (r < 0.5f) EnemyArchetype.MACEMAN else if (r < 0.65f) EnemyArchetype.ARCHER else if (r < 0.85f) EnemyArchetype.HOUSECARL else EnemyArchetype.SHIELD_WALL
        } else if (level <= 6) {
            if (r < 0.15f) EnemyArchetype.MACEMAN else if (r < 0.3f) EnemyArchetype.PIKEMAN else if (r < 0.45f) EnemyArchetype.SHIELD_WALL else if (r < 0.6f) EnemyArchetype.BERSERKER else if (r < 0.7f) EnemyArchetype.KNIGHT_DISMOUNTED else if (r < 0.8f) EnemyArchetype.CHARIOT_ARCHER else if (r < 0.95f) EnemyArchetype.CAVALRY else EnemyArchetype.LORD
        } else {
            if (r < 0.15f) EnemyArchetype.ARCHER else if (r < 0.25f) EnemyArchetype.JAVELINEER else if (r < 0.4f) EnemyArchetype.SHIELD_WALL else if (r < 0.5f) EnemyArchetype.BERSERKER else if (r < 0.65f) EnemyArchetype.CAVALRY else if (r < 0.75f) EnemyArchetype.CHARIOT_ARCHER else if (r < 0.9f) EnemyArchetype.CHAMPION else EnemyArchetype.KING
        }

        fun <T> List<T>.safeRandom(fallback: T): T = if (this.isEmpty()) fallback else this.random(rng)

        val gear = when (arch) {
            EnemyArchetype.PEASANT -> listOf(
                safeHead("head_pitchfork"), safeHandle("handle_long"), safeShield("shield_none"), safeArmor("armor_bare"), safeHelm("helm_none")
            )
            EnemyArchetype.FYRD_LEVY -> listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_spear", "head_dagger", "head_bare", "head_club") }.safeRandom(safeHead("head_bare")),
                safeHandle("handle_short"), safeShield(if (rng.nextFloat() < 0.3f) "shield_buckler" else "shield_none"), safeArmor("armor_padded"), safeHelm("helm_none")
            )
            EnemyArchetype.SLINGER -> listOf(
                safeHead("head_slingshot"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_bare"), safeHelm("helm_none")
            )
            EnemyArchetype.JAVELINEER -> listOf(
                safeHead("head_javelin"), safeHandle("handle_fists"), safeShield("shield_buckler"), safeArmor("armor_leather"), safeHelm("helm_none")
            )
            EnemyArchetype.MACEMAN -> listOf(
                safeHead("head_mace"), safeHandle("handle_medium"), safeShield("shield_heater"), safeArmor("armor_leather"), safeHelm("helm_conical")
            )
            EnemyArchetype.HOUSECARL -> listOf(
                safeHead("head_axe"), safeHandle("handle_medium"), safeShield("shield_none"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.ARCHER -> listOf(
                safeHead("head_bow"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_leather"), safeHelm("helm_none")
            )
            EnemyArchetype.SHIELD_WALL -> listOf(
                safeHead("head_spear"), safeHandle("handle_medium"), safeShield("shield_tower"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.PIKEMAN -> listOf(
                safeHead("head_pike"), safeHandle("handle_long"), safeShield("shield_none"), safeArmor("armor_scale"), safeHelm("helm_conical")
            )
            EnemyArchetype.BERSERKER -> listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_axe", "head_sword", "head_flail", "head_mace", "head_war_flail") }.safeRandom(safeHead("head_axe")),
                safeHandle("handle_short"), safeShield("shield_none"), safeArmor("armor_bare"), safeHelm("helm_none")
            )
            EnemyArchetype.KNIGHT_DISMOUNTED -> listOf(
                safeHead("head_broadsword"), safeHandle("handle_medium"), safeShield("shield_heater"), safeArmor("armor_scale"), safeHelm("helm_great")
            )
            EnemyArchetype.CAVALRY -> listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_pike", "head_sword", "head_javelin") }.safeRandom(safeHead("head_sword")),
                safeHandle("handle_medium"), safeShield("shield_heater"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.CHARIOT_ARCHER -> listOf(
                safeHead("head_crossbow"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.CHARIOT_LANCER -> listOf(
                safeHead("head_pike"), safeHandle("handle_long"), safeShield("shield_tower"), safeArmor("armor_scale"), safeHelm("helm_great")
            )
            EnemyArchetype.CHAMPION -> listOf(
                GameData.WEAPON_HEADS.filter { it.id in listOf("head_claymore", "head_maul", "head_war_flail", "head_halberd") }.safeRandom(safeHead("head_maul")),
                safeHandle("handle_iron"), safeShield("shield_none"), safeArmor("armor_scale"), safeHelm("helm_great")
            )
            EnemyArchetype.LORD -> listOf(
                safeHead("head_broadsword"), safeHandle("handle_medium"), safeShield("shield_heater"), safeArmor("armor_scale"), safeHelm("helm_none") // will draw crown instead
            )
            EnemyArchetype.KING -> listOf(
                safeHead("head_claymore"), safeHandle("handle_iron"), safeShield("shield_none"), safeArmor("armor_scale"), safeHelm("helm_none") // will draw crown instead
            )
        }

        val isMounted = arch in listOf(EnemyArchetype.CAVALRY, EnemyArchetype.CHAMPION) || (arch == EnemyArchetype.CHAMPION && rng.nextFloat() < 0.5f) || arch in listOf(EnemyArchetype.CHARIOT_ARCHER, EnemyArchetype.CHARIOT_LANCER)
        val isChariot = arch in listOf(EnemyArchetype.CHARIOT_ARCHER, EnemyArchetype.CHARIOT_LANCER)
        val isLord = arch in listOf(EnemyArchetype.LORD, EnemyArchetype.KING)

        val sizeMultiplier = if (arch in listOf(EnemyArchetype.CHAMPION, EnemyArchetype.LORD, EnemyArchetype.KING)) 1.25f else if (arch == EnemyArchetype.BERSERKER) 1.1f else rng.nextFloat() * 0.4f + 0.9f
        // Gentler early curve; only ramps hard again at very high levels
        var enemyHp = baseHpFor(level) * sizeMultiplier
        if (arch in listOf(EnemyArchetype.CHAMPION, EnemyArchetype.LORD)) enemyHp *= 1.5f
        if (arch == EnemyArchetype.KING) enemyHp *= 3f

        // Wider palette so a wave stops looking like one man cloned: ginger, flaxen, ash, soot, grey,
        // chestnut, tow-head, rust
        val hairColors = listOf(
            Color(0xFFC08030), Color(0xFF5A442E), Color(0xFF8A7156), Color(0xFF2C2219), Color(0xFF888888),
            Color(0xFF6E4B22), Color(0xFFD8C08A), Color(0xFF9C4A1E)
        )
        val hairStyles = listOf("short", "long", "bald")

        val startX = spawnX(index)

        return FighterState(
            id = "saxon_$index",
            name = saxonName,
            isPlayer = false,
            maxHp = enemyHp,
            hp = enemyHp,
            weaponHead = gear[0] as GameData.WeaponHead,
            weaponHandle = gear[1] as GameData.WeaponHandle,
            shield = gear[2] as GameData.Shield,
            armor = gear[3] as GameData.ArmorPiece,
            headgear = gear[4] as GameData.HeadgearPiece,
            posX = startX,
            targetX = startX,
            facingRight = false,
            size = sizeMultiplier,
            hairColor = hairColors.random(rng),
            hairStyle = hairStyles.random(rng),
            faceNoseShape = rng.nextInt(4),
            faceBiteShape = rng.nextInt(4),
            faceForehead = rng.nextInt(3),
            faceMustache = rng.nextInt(4),
            // Old blood in the Saxon line: about one in seven daubs himself in woad before a fight
            warPaint = if (rng.nextFloat() < 0.15f) 1 else 0,
            level = level,
            isMounted = isMounted,
            isChariot = isChariot,
            isLord = isLord,
            speedBoost = if (isMounted && !isChariot) 0.5f else if (isChariot) 0.5f else 0f,
            shieldHp = (gear[2] as GameData.Shield).defense * 2f,
            bandagesCount = if (level > 4 && rng.nextFloat() < 0.35f) 1 else 0,
            // Saxons weld junk to their weapons too, from level 10 — but at most two pieces, and only
            // sometimes. The player's ludicrous tree of attachments has to stay the winning edge.
            extraAttachments = if (level >= 10 && rng.nextFloat() < 0.3f) {
                List(rng.nextInt(1, if (level >= 18) 3 else 2)) { GameData.WEAPON_HEADS.random(rng) }
            } else emptyList()
        )
    }
}
