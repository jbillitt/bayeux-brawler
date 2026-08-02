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
            id = FighterId("shieldwall_${index}_$i"),
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
            id = FighterId("brute_$index"),
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
            id = FighterId("war_priest_$index"),
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

    /** Explicit archetype construction for authored encounters (sieges, bosses and tests). */
    fun createArchetype(archetype: EnemyArchetype, index: Int, level: Int): FighterState {
        val kit = when (archetype) {
            EnemyArchetype.WALL_ARCHER, EnemyArchetype.ARCHER ->
                listOf("head_longbow", "handle_fists", "shield_none", "armor_leather", "helm_kettle")
            // A brand in the hand, not a sling. He is the one enemy who can set the player
            // alight, and that must be something you can see coming and back away from — a
            // ranged igniter is unavoidable chip damage, which is not a fight.
            EnemyArchetype.TORCH_BEARER ->
                // He holds an actual lit brand now. He was kitted with a club and the smoke was
                // pinned on by archetype instead, which is why you saw a man swinging a cudgel
                // with fire coming out of his fist.
                listOf("head_torch", "handle_short", "shield_none", "armor_padded", "helm_none")
            EnemyArchetype.DANE_AXE_EXECUTIONER ->
                listOf("head_axe", "handle_pike_long", "shield_none", "armor_chainmail", "helm_conical")
            EnemyArchetype.MONK_MILITIA ->
                listOf("head_club", "handle_short", "shield_none", "armor_bare", "helm_none")
            EnemyArchetype.NORMAN_LOYALIST ->
                listOf("head_broadsword", "handle_medium", "shield_kite", "armor_chainmail", "helm_conical")
            EnemyArchetype.CYNOCEPHALUS ->
                listOf("head_spear", "handle_medium", "shield_none", "armor_fur", "helm_none")
            EnemyArchetype.REBEL_SNAIL -> // it bites; the shell is the armor
                listOf("head_bare", "handle_fists", "shield_none", "armor_bare", "helm_none")
            EnemyArchetype.HOUSECARL ->
                listOf("head_axe", "handle_medium", "shield_none", "armor_chainmail", "helm_conical")
            EnemyArchetype.BERSERKER ->
                listOf("head_axe", "handle_iron", "shield_none", "armor_bare", "helm_none")
            // 25 blunt on the maul clears the >18 threshold that floors a man, which is the
            // whole point of him: he is the host's answer to a player horde.
            EnemyArchetype.HAMMER_SERJEANT ->
                listOf("head_maul", "handle_long", "shield_none", "armor_chainmail", "helm_conical")
            // Nothing heavy anywhere on him. He is here to be past your line before it turns.
            EnemyArchetype.OUTRIDER ->
                listOf("head_dagger", "handle_short", "shield_none", "armor_leather", "helm_none")
            EnemyArchetype.BOMBARDIER ->
                listOf("head_slingshot", "handle_fists", "shield_none", "armor_padded", "helm_none")
            else ->
                listOf("head_sword", "handle_medium", "shield_buckler", "armor_padded", "helm_none")
        }
        var hp = baseHpFor(level)
        val size = when (archetype) {
            EnemyArchetype.DANE_AXE_EXECUTIONER -> 1.25f
            EnemyArchetype.NORMAN_LOYALIST -> 1.15f
            EnemyArchetype.MONK_MILITIA -> 0.9f
            // Damage goes as size squared, so this is most of "dog-heads hit harder" — 1.05 to
            // 1.25 is a third again on every bite.
            EnemyArchetype.CYNOCEPHALUS -> 1.25f
            EnemyArchetype.REBEL_SNAIL -> 1.35f
            EnemyArchetype.HAMMER_SERJEANT -> 1.35f
            EnemyArchetype.OUTRIDER -> 0.9f
            else -> 1f
        }
        hp *= when (archetype) {
            EnemyArchetype.DANE_AXE_EXECUTIONER -> 1.65f
            EnemyArchetype.NORMAN_LOYALIST -> 1.5f
            EnemyArchetype.MONK_MILITIA -> 0.65f
            EnemyArchetype.CYNOCEPHALUS -> 1.25f
            EnemyArchetype.HAMMER_SERJEANT -> 1.6f
            // Fast and fragile, or he is simply better than everything else on the field.
            EnemyArchetype.OUTRIDER -> 0.55f
            EnemyArchetype.BOMBARDIER -> 0.7f
            // The marginalia knight's true nightmare: it does not die. It turns up late, by which
            // point the player is fully snowballed, so it needs to be a wall rather than a joke.
            EnemyArchetype.REBEL_SNAIL -> 18f
            else -> 1f
        }
        val shield = safeShield(kit[2])
        return FighterState(
            id = FighterId("${archetype.name.lowercase()}_$index"),
            name = when (archetype) {
                EnemyArchetype.WALL_ARCHER -> "Archer of the Wall"
                EnemyArchetype.TORCH_BEARER -> "Osric the Incendiary"
                EnemyArchetype.DANE_AXE_EXECUTIONER -> "Hakon Long-Axe"
                EnemyArchetype.MONK_MILITIA -> "Brother Cuthbert"
                EnemyArchetype.NORMAN_LOYALIST -> "Knight of William"
                EnemyArchetype.CYNOCEPHALUS -> "Dog-Head of the East"
                EnemyArchetype.REBEL_SNAIL -> "The Rebel Snail"
                EnemyArchetype.HAMMER_SERJEANT -> "Serjeant of the Maul"
                EnemyArchetype.OUTRIDER -> "Outrider"
                EnemyArchetype.BOMBARDIER -> "Bombardier of Cathay"
                else -> archetype.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            },
            isPlayer = false,
            maxHp = hp, hp = hp,
            weaponHead = safeHead(kit[0]), weaponHandle = safeHandle(kit[1]),
            shield = shield, armor = safeArmor(kit[3]), headgear = safeHelm(kit[4]),
            posX = spawnX(index), targetX = spawnX(index), facingRight = false,
            size = size, hairColor = Color(0xFF5A442E),
            hairStyle = if (archetype == EnemyArchetype.MONK_MILITIA) "bald" else "short",
            speedBoost = if (archetype == EnemyArchetype.REBEL_SNAIL) -0.75f else 0f,
            level = level, shieldHp = shield.defense * 2f, archetype = archetype
        )
    }

    fun createBoss(type: BossType, level: Int, tier: BossTier = BossTier.LIVING): FighterState {
        val isGiant = type == BossType.GOG || type == BossType.MAGOG
        val base = when (type) {
            BossType.HAROLD_GODWINSON -> createArchetype(EnemyArchetype.HOUSECARL, 90, level)
            BossType.HARALD_HARDRADA, BossType.GOG, BossType.MAGOG -> createArchetype(EnemyArchetype.BERSERKER, 90, level)
            BossType.WILLIAM_THE_BASTARD -> createArchetype(EnemyArchetype.NORMAN_LOYALIST, 90, level)
        }
        val scaling = 1f + (level - type.level).coerceAtLeast(0) * 0.05f
        val hp = baseHpFor(level) * (if (isGiant) 13f else 9.5f) * scaling * tier.hpScale
        return base.copy(
            id = FighterId("boss_${type.name.lowercase()}" + if (tier == BossTier.LIVING) "" else "_${tier.name.lowercase()}"),
            name = tier.titlePrefix + when (type) {
                BossType.HAROLD_GODWINSON -> "Harold Godwinson"
                BossType.HARALD_HARDRADA -> "Harald Hardrada"
                BossType.WILLIAM_THE_BASTARD -> "William the Bastard"
                BossType.GOG -> "Gog, Giant of Albion"
                BossType.MAGOG -> "Magog, Giant of Albion"
            },
            maxHp = hp, hp = hp,
            size = if (isGiant) 2.3f else if (type == BossType.HARALD_HARDRADA) 1.75f else 1.6f,
            // The giants swing a whole tree stump capped with a mallet head, bare-headed and woaded.
            weaponHead = if (isGiant) safeHead("head_maul") else base.weaponHead,
            weaponHandle = if (isGiant) safeHandle("handle_stump") else base.weaponHandle,
            warPaint = if (isGiant) 1 else base.warPaint,
            hairStyle = if (isGiant) "long" else base.hairStyle,
            headgear = if (type == BossType.HARALD_HARDRADA || isGiant) safeHelm("helm_none") else safeHelm("helm_crown"),
            shield = if (type == BossType.HAROLD_GODWINSON) safeShield("shield_tower") else base.shield,
            shieldHp = if (type == BossType.HAROLD_GODWINSON) safeShield("shield_tower").defense * 5f else base.shieldHp,
            posX = 1250f, targetX = 1250f, bossType = type, bossTier = tier,
            // Grave-pallor: the risen keep their gear but not their colour.
            hairColor = when {
                tier != BossTier.LIVING -> Color(0xFF9AA88C)
                type == BossType.GOG -> Color(0xFF4A5D23)
                type == BossType.MAGOG -> Color(0xFF384048)
                else -> base.hairColor
            },
            // Late-run bosses punch through the player's stacked armour: welded lucerne beaks add
            // pierce (attachments contribute half their stats to every swing).
            extraAttachments = when {
                level >= 40 -> listOf(safeHead("head_lucerne"), safeHead("head_lucerne"))
                level >= 20 -> listOf(safeHead("head_lucerne"))
                else -> emptyList()
            },
            arrowEyeCritWindow = if (type == BossType.HAROLD_GODWINSON) 2.5f else 0f,
            isCombatInactive = type == BossType.HARALD_HARDRADA
        )
    }

    fun createBossEncounter(type: BossType, level: Int, tier: BossTier = BossTier.LIVING): MutableList<FighterState> {
        val boss = createBoss(type, level, tier)
        val retinueType = when (type) {
            BossType.HAROLD_GODWINSON -> EnemyArchetype.HOUSECARL
            BossType.HARALD_HARDRADA -> EnemyArchetype.BERSERKER
            BossType.WILLIAM_THE_BASTARD -> EnemyArchetype.NORMAN_LOYALIST
            // The giants march with a pack of dog-headed men
            BossType.GOG, BossType.MAGOG -> EnemyArchetype.CYNOCEPHALUS
        }
        val count = if (type == BossType.HARALD_HARDRADA) 8 else if (type == BossType.GOG || type == BossType.MAGOG) 6 else 5
        val retinue = List(count) { index ->
            val base = createArchetype(retinueType, index, level)
            val eliteHp = base.maxHp * 1.9f
            base.copy(
                id = FighterId("boss_retinue_${type.name.lowercase()}_$index"),
                maxHp = eliteHp, hp = eliteHp, isBossRetinue = true,
                posX = 850f + index * 85f, targetX = 850f + index * 85f,
                isCombatInactive = type == BossType.HARALD_HARDRADA && index >= 2
            )
        }
        return (retinue + boss).toMutableList()
    }

    fun randomSaxon(
        index: Int,
        level: Int,
        rng: kotlin.random.Random = kotlin.random.Random.Default
    ): FighterState {
        val saxonName = if (index < NAMES.size) NAMES[index] else "Saxon Foe ${index + 1}"

        val r = rng.nextFloat()

        val arch = if (level == 1) {
            // Landing beach: green rabble, plus the odd shepherd slinger. Early ranged fire is
            // slowed (EARLY_RANGED_LEVEL) and slingers stumble as they kite, so they harry rather
            // than cheese the opening rounds.
            if (r < 0.5f) EnemyArchetype.PEASANT else if (r < 0.78f) EnemyArchetype.FYRD_LEVY else EnemyArchetype.SLINGER
        } else if (level <= 2) {
            if (r < 0.35f) EnemyArchetype.PEASANT else if (r < 0.6f) EnemyArchetype.FYRD_LEVY else if (r < 0.8f) EnemyArchetype.HOUSECARL else EnemyArchetype.SLINGER
        } else if (level <= 4) {
            if (r < 0.2f) EnemyArchetype.PEASANT else if (r < 0.35f) EnemyArchetype.JAVELINEER else if (r < 0.5f) EnemyArchetype.MACEMAN else if (r < 0.65f) EnemyArchetype.ARCHER else if (r < 0.85f) EnemyArchetype.HOUSECARL else EnemyArchetype.SHIELD_WALL
        } else if (level <= 6) {
            if (r < 0.15f) EnemyArchetype.MACEMAN else if (r < 0.3f) EnemyArchetype.PIKEMAN else if (r < 0.45f) EnemyArchetype.SHIELD_WALL else if (r < 0.6f) EnemyArchetype.BERSERKER else if (r < 0.7f) EnemyArchetype.KNIGHT_DISMOUNTED else if (r < 0.8f) EnemyArchetype.CHARIOT_ARCHER else if (r < 0.95f) EnemyArchetype.CAVALRY else EnemyArchetype.LORD
        } else {
            when {
                // Rare, and late. He answers a player who has stopped being one man and become a
                // horde: a maul that puts followers on their backs, and legs that get behind them.
                level >= 40 && r < 0.03f -> EnemyArchetype.BOMBARDIER
                level >= 25 && r < 0.10f -> EnemyArchetype.HAMMER_SERJEANT
                level >= 25 && r < 0.17f -> EnemyArchetype.OUTRIDER
                level >= 30 && r < 0.05f -> EnemyArchetype.NORMAN_LOYALIST
                r < 0.12f -> EnemyArchetype.ARCHER
                r < 0.20f -> EnemyArchetype.TORCH_BEARER
                level >= 25 && r < 0.30f -> EnemyArchetype.CYNOCEPHALUS
                r < 0.37f -> EnemyArchetype.DANE_AXE_EXECUTIONER
                r < 0.48f -> EnemyArchetype.SHIELD_WALL
                r < 0.58f -> EnemyArchetype.BERSERKER
                r < 0.70f -> EnemyArchetype.CAVALRY
                r < 0.80f -> EnemyArchetype.CHARIOT_ARCHER
                r < 0.92f -> EnemyArchetype.CHAMPION
                else -> EnemyArchetype.KING
            }
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
            EnemyArchetype.WALL_ARCHER -> listOf(
                safeHead("head_longbow"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_leather"), safeHelm("helm_kettle")
            )
            EnemyArchetype.TORCH_BEARER -> listOf(
                safeHead("head_torch"), safeHandle("handle_short"), safeShield("shield_none"), safeArmor("armor_padded"), safeHelm("helm_none")
            )
            EnemyArchetype.DANE_AXE_EXECUTIONER -> listOf(
                safeHead("head_axe"), safeHandle("handle_pike_long"), safeShield("shield_none"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.MONK_MILITIA -> listOf(
                safeHead("head_club"), safeHandle("handle_short"), safeShield("shield_none"), safeArmor("armor_bare"), safeHelm("helm_none")
            )
            EnemyArchetype.NORMAN_LOYALIST -> listOf(
                safeHead("head_broadsword"), safeHandle("handle_medium"), safeShield("shield_kite"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.CYNOCEPHALUS -> listOf(
                safeHead("head_spear"), safeHandle("handle_medium"), safeShield("shield_none"), safeArmor("armor_fur"), safeHelm("helm_none")
            )
            EnemyArchetype.REBEL_SNAIL -> listOf(
                safeHead("head_bare"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_bare"), safeHelm("helm_none")
            )
            EnemyArchetype.HAMMER_SERJEANT -> listOf(
                safeHead("head_maul"), safeHandle("handle_long"), safeShield("shield_none"), safeArmor("armor_chainmail"), safeHelm("helm_conical")
            )
            EnemyArchetype.OUTRIDER -> listOf(
                safeHead("head_dagger"), safeHandle("handle_short"), safeShield("shield_none"), safeArmor("armor_leather"), safeHelm("helm_none")
            )
            EnemyArchetype.BOMBARDIER -> listOf(
                safeHead("head_slingshot"), safeHandle("handle_fists"), safeShield("shield_none"), safeArmor("armor_padded"), safeHelm("helm_none")
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
        // Widened from short/long/bald. Every one of these is already drawn by TapestryRenderer
        // for player unlocks; the host had simply never been allowed any of them, so a wave of
        // Saxons was three haircuts between them.
        val hairStyles = listOf(
            "short", "long", "bald", "hair_braids", "hair_topknot",
            "hair_germanic", "hair_tonsure_norman"
        )

        val startX = spawnX(index)

        return FighterState(
            id = FighterId("saxon_$index"),
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
            faceNoseShape = rng.nextInt(6),
            faceBiteShape = rng.nextInt(6),
            faceForehead = rng.nextInt(6),
            faceMustache = rng.nextInt(4),
            // Old blood in the Saxon line: about one in seven daubs himself in woad before a fight
            warPaint = if (rng.nextFloat() < 0.15f) 1 else 0,
            level = level,
            archetype = arch,
            isMounted = isMounted,
            isChariot = isChariot,
            isLord = isLord,
            speedBoost = if (isMounted && !isChariot) 0.5f else if (isChariot) 0.5f else 0f,
            shieldHp = (gear[2] as GameData.Shield).defense * 2f,
            bandagesCount = if (level > 4 && rng.nextFloat() < 0.35f) 1 else 0,
            // Saxons weld junk to their weapons too, from level 10 — and the deeper the run goes,
            // the more of them do it and the more junk they weld. The player's ludicrous tree of
            // attachments still outgrows them, but the late host keeps pace.
            extraAttachments = if (level >= 10 && rng.nextFloat() < (0.3f + (level - 10) * 0.015f).coerceAtMost(0.85f)) {
                val maxPieces = (2 + (level - 18).coerceAtLeast(0) / 15).coerceAtMost(4)
                List(rng.nextInt(1, maxPieces + 1)) { GameData.WEAPON_HEADS.random(rng) }
            } else emptyList(),
            // From level 25 the host layers on extra iron as well: gauntlets, coifs, boots.
            extraArmors = if (level >= 25 && rng.nextFloat() < (0.3f + (level - 25) * 0.01f).coerceAtMost(0.7f)) {
                GameData.ARMOR_PIECES.filter { it.id in listOf("armor_gauntlets", "armor_boots", "armor_coif") }
                    .shuffled(rng).take(1 + rng.nextInt(2))
            } else emptyList()
        )
    }
}
