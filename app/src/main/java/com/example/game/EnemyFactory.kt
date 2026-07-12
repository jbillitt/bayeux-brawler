package com.example.game

import androidx.compose.ui.graphics.Color

/** Builds Saxon foes for a given level. Pure: no ViewModel, no audio, no flows. */
object EnemyFactory {

    private val NAMES = listOf(
        "Harold of Essex", "Gyrth Shield-Cleaver", "Leofwine", "Tostig Dunce",
        "Aldred the Bald", "Godwin the Grumpy", "Sigurd Skull-Basher", "Ethelred the Unready",
        "Cerdic the Giant", "Wulfric", "Odo the Swift", "Aelfric", "Leofric", "Edric"
    )

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
        fun safeHead(id: String) = GameData.WEAPON_HEADS.firstOrNull { it.id == id } ?: GameData.WEAPON_HEADS.first()
        fun safeHandle(id: String) = GameData.WEAPON_HANDLES.firstOrNull { it.id == id } ?: GameData.WEAPON_HANDLES.first()
        fun safeShield(id: String) = GameData.SHIELDS.firstOrNull { it.id == id } ?: GameData.SHIELDS.first()
        fun safeArmor(id: String) = GameData.ARMOR_PIECES.firstOrNull { it.id == id } ?: GameData.ARMOR_PIECES.first()
        fun safeHelm(id: String) = GameData.HEADGEAR_PIECES.firstOrNull { it.id == id } ?: GameData.HEADGEAR_PIECES.first()

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
        val baseHp = (if (level == 1) 32f else 50f) + (level * 7f) + (level - 15).coerceAtLeast(0) * 8f
        var enemyHp = baseHp * sizeMultiplier
        if (arch in listOf(EnemyArchetype.CHAMPION, EnemyArchetype.LORD)) enemyHp *= 1.5f
        if (arch == EnemyArchetype.KING) enemyHp *= 3f

        val hairColors = listOf(Color(0xFFC08030), Color(0xFF5A442E), Color(0xFF8A7156), Color(0xFF2C2219), Color(0xFF888888))
        val hairStyles = listOf("short", "long", "bald")

        val startX = 850f + (index * 130f)

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
            level = level,
            isMounted = isMounted,
            isChariot = isChariot,
            isLord = isLord,
            speedBoost = if (isMounted && !isChariot) 0.5f else if (isChariot) 0.5f else 0f,
            shieldHp = (gear[2] as GameData.Shield).defense * 2f,
            bandagesCount = if (level > 4 && rng.nextFloat() < 0.35f) 1 else 0,
            extraAttachments = if (level >= 15 && rng.nextFloat() < 0.5f) List(rng.nextInt(1, 2 + (level - 14) / 5)) { GameData.WEAPON_HEADS.random(rng) } else emptyList()
        )
    }
}
