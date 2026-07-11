package com.example.game

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

// Represents a flying missile (arrow or sling stone)
data class Projectile(
    val id: String,
    val isPlayerOwned: Boolean,
    var posX: Float,
    var posY: Float,
    var velocityX: Float,
    var velocityY: Float,
    val damage: Float,
    val pierce: Float,
    val blunt: Float,
    val type: String, // "arrow" or "stone"
    val sizeMultiplier: Float = 1f,
    val hasSpikes: Boolean = false,
    val launchedWeaponId: String? = null,
    val isSplash: Boolean = false,
    val isPoisonous: Boolean = false,
    val isBallista: Boolean = false,
    val gravityMult: Float = 1f
)

class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BattleSimState())
    val uiState: StateFlow<BattleSimState> = _uiState.asStateFlow()

    // Active fighter list and projectile state inside the active battle
    private val _playerState = MutableStateFlow<FighterState?>(null)
    val playerState: StateFlow<FighterState?> = _playerState.asStateFlow()

    private val _enemiesState = MutableStateFlow<List<FighterState>>(emptyList())
    val enemiesState: StateFlow<List<FighterState>> = _enemiesState.asStateFlow()

    private val _projectilesState = MutableStateFlow<List<Projectile>>(emptyList())
    val projectilesState: StateFlow<List<Projectile>> = _projectilesState.asStateFlow()

    private val _popupsState = MutableStateFlow<List<CombatPopup>>(emptyList())
    val popupsState: StateFlow<List<CombatPopup>> = _popupsState.asStateFlow()

    private val _particlesState = MutableStateFlow<List<BloodParticle>>(emptyList())
    val particlesState: StateFlow<List<BloodParticle>> = _particlesState.asStateFlow()

    private val _gameTick = MutableStateFlow(0L)
    val gameTick: StateFlow<Long> = _gameTick.asStateFlow()

    private val _screenshake = MutableStateFlow(0f)
    val screenshake: StateFlow<Float> = _screenshake.asStateFlow()

    private var gameLoopJob: Job? = null

    init {
        // Initialize highscore and random starting gear
        val initialGear = mutableSetOf<String>()
        initialGear.add("head_bare")
        initialGear.add("handle_fists")
        initialGear.add("shield_none")
        initialGear.add("armor_none")
        initialGear.add("head_none")
        
        // Randomly unlock 2 more of each category to start
        initialGear.addAll(GameData.WEAPON_HEADS.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.WEAPON_HANDLES.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.ARMOR_PIECES.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.HEADGEAR_PIECES.shuffled().take(2).map { it.id })

        _uiState.update { it.copy(
            highscore = 0,
            unlockedGearIds = initialGear
        ) }
        randomizeGear()
        
        val sizes = listOf(0.85f, 1.0f, 1.15f)
        val hairColors = listOf(Color(0xFFE5C09F), Color(0xFFC08030), Color(0xFF2C2219), Color(0xFF5A442E))
        val styles = listOf("short", "long", "bald")
        updatePhysical(sizes.random(), hairColors.random(), styles.random())
    }

    fun selectLevelUpChoice(choiceId: String) {
        _uiState.update { state ->
            val choice = state.pendingLevelUpChoices.find { it.id == choiceId }
            if (choice == null) {
                val bonus = (250 * state.level * state.scoreMultiplier).toInt()
                return@update state.copy(
                    showLevelUpScreen = false,
                    pendingLevelUpChoices = emptyList(),
                    score = state.score + bonus,
                    pendingSkipBonus = bonus
                )
            }
            
            val newAttachments = if (choice.type == "attachment") state.extraAttachments + choice.itemId else state.extraAttachments
            val newArmors = if (choice.type == "armor") state.extraArmors + choice.itemId else state.extraArmors
            val newAncs = if (choice.type == "follower") state.unlockedAncillaries + choice.itemId else state.unlockedAncillaries
            val newExtensions = if (choice.type == "extension") state.handleExtensionCount + 1 else state.handleExtensionCount
            val newRangedUpgrades = if (choice.type == "ranged_upgrade") state.rangedUpgrades + choice.itemId else state.rangedUpgrades
            
            // Gain dynamic buffs based on items or ancillaries selected
            state.copy(
                extraAttachments = newAttachments,
                extraArmors = newArmors,
                unlockedAncillaries = newAncs,
                handleExtensionCount = newExtensions,
                rangedUpgrades = newRangedUpgrades,
                showLevelUpScreen = false,
                pendingLevelUpChoices = emptyList()
            )
        }
    }

    fun selectMusicMood(mood: String) {
        _uiState.update { state ->
            // ponytail: only apply mood 70% of the time to preserve variance
            val applied = if (mood != "No Change" && kotlin.random.Random.nextFloat() < 0.7f) state.appliedMusicMoods + mood else state.appliedMusicMoods
            state.copy(
                showMusicDecision = false,
                pendingMusicOptions = emptyList(),
                appliedMusicMoods = applied,
                showLevelUpScreen = true // proceed to reward after music choice
            )
        }
    }

    fun selectGear(item: GearItem) {
        if (_uiState.value.isBattleActive) return // Cannot change gear mid-battle

        _uiState.update { state ->
            var newState = when (item.type) {
                ItemType.WEAPON_HEAD -> state.copy(weaponHead = item)
                ItemType.WEAPON_HANDLE -> state.copy(weaponHandle = item)
                ItemType.SHIELD -> state.copy(shield = item, isDualWielding = if (item.id != "shield_none") false else state.isDualWielding)
                ItemType.ARMOR -> state.copy(armor = item)
                ItemType.HEADGEAR -> state.copy(headgear = item)
            }
            
            // Enforce two-handed rule
            val isTwoHanded = newState.weaponHead.id in listOf("head_claymore", "head_longbow", "head_halberd", "head_pike", "head_scythe", "head_bow")
            if (isTwoHanded && newState.shield.id != "shield_none") {
                newState = newState.copy(shield = GameData.SHIELDS.first { it.id == "shield_none" })
            }
            
            // Enforce crossbow rule
            if (newState.weaponHead.id == "head_crossbow" && newState.weaponHandle.id != "handle_fists") {
                if (item.type == ItemType.WEAPON_HEAD) {
                    newState = newState.copy(weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" })
                } else if (item.type == ItemType.WEAPON_HANDLE) {
                    newState = newState.copy(weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" })
                }
            }
            newState
        }
    }

    fun updatePhysical(size: Float, hairColor: Color, hairStyle: String) {
        if (_uiState.value.isBattleActive) return
        
        val rng = kotlin.random.Random.Default
        val lastName = if (hairStyle == "bald") {
            listOf("the Bald", "the Shorn", "the Smooth", "Bare-pate").random(rng)
        } else if (hairStyle == "long") {
            listOf("the Wild", "the Mane", "Long-Locks", "the Hairy", "the Untamed").random(rng)
        } else {
            when (hairColor) {
                Color(0xFF888888) -> listOf("the Grey", "the Hoary", "Silver-hair", "the Elder").random(rng)
                Color(0xFFC08030) -> listOf("the Red", "Fire-top", "the Bloody", "Rufus").random(rng)
                Color(0xFF2C2219) -> listOf("the Dark", "the Black", "Night-haired", "the Grim").random(rng)
                else -> listOf("the Brown", "the Muddy", "Earth-born", "the Common").random(rng)
            }
        }
        val currentFirstName = _uiState.value.playerName.split(" ").firstOrNull() ?: "Syr"
        val newName = "$currentFirstName $lastName"

        _uiState.update { state ->
            state.copy(
                characterSize = size,
                hairColor = hairColor,
                hairStyle = hairStyle,
                playerName = newName
                // Removed face rerolls here so they stay constant during customization
            )
        }
    }

    fun toggleDualWield() {
        if (_uiState.value.isBattleActive) return
        _uiState.update { 
            if (!it.isDualWielding) {
                val noShield = GameData.SHIELDS.first { s -> s.id == "shield_none" }
                it.copy(isDualWielding = true, shield = noShield)
            } else {
                it.copy(isDualWielding = false)
            }
        }
        MedievalAudioSynth.playSound(SoundType.SWOOSH)
    }

    fun randomizeGear() {
        if (_uiState.value.isBattleActive) return
        
        val state = _uiState.value
        val unlockedHeads = GameData.WEAPON_HEADS.filter { it.id in state.unlockedGearIds }
        val unlockedHandles = GameData.WEAPON_HANDLES.filter { it.id in state.unlockedGearIds }
        val unlockedShields = GameData.SHIELDS.filter { it.id in state.unlockedGearIds }
        val unlockedArmor = GameData.ARMOR_PIECES.filter { it.id in state.unlockedGearIds }
        val unlockedHeadgear = GameData.HEADGEAR_PIECES.filter { it.id in state.unlockedGearIds }

        val randomHead = if (unlockedHeads.isNotEmpty()) unlockedHeads.random() else GameData.WEAPON_HEADS[0]
        
        // If fist selected, select bare wrists
        val randomHandle = if (randomHead.id == "head_bare") {
            GameData.WEAPON_HANDLES[0]
        } else {
            val handlesWithoutFists = unlockedHandles.filter { it.id != "handle_fists" }
            if (handlesWithoutFists.isNotEmpty()) handlesWithoutFists.random() else GameData.WEAPON_HANDLES[1]
        }
        
        _uiState.update {
            var newState = it.copy(
                weaponHead = randomHead,
                weaponHandle = randomHandle,
                shield = if (unlockedShields.isNotEmpty()) unlockedShields.random() else GameData.SHIELDS[0],
                armor = if (unlockedArmor.isNotEmpty()) unlockedArmor.random() else GameData.ARMOR_PIECES[0],
                headgear = if (unlockedHeadgear.isNotEmpty()) unlockedHeadgear.random() else GameData.HEADGEAR_PIECES[0]
            )
            
            // Enforce two-handed rule
            val isTwoHanded = newState.weaponHead.id in listOf("head_claymore", "head_longbow", "head_halberd", "head_pike", "head_scythe", "head_bow")
            if (isTwoHanded) {
                newState = newState.copy(shield = GameData.SHIELDS.first { s -> s.id == "shield_none" })
            }
            newState
        }
        MedievalAudioSynth.playSound(SoundType.SWOOSH)
    }

    fun startBattle() {
        val state = _uiState.value
        if (state.isBattleActive) return

        // Create player state with complete roguelike upgrade state
        val baseHp = 100f + state.totalHpBoost
        val totalPlayerMaxHp = baseHp * state.characterSize
        val player = FighterState(
            id = "player_knight",
            name = state.playerName,
            isPlayer = true,
            maxHp = totalPlayerMaxHp,
            hp = totalPlayerMaxHp,
            weaponHead = state.weaponHead,
            weaponHandle = state.weaponHandle,
            shield = state.shield,
            armor = state.armor,
            headgear = state.headgear,
            isDualWielding = state.isDualWielding,
            posX = 150f,
            targetX = 150f,
            facingRight = true,
            size = state.characterSize,
            hairColor = state.hairColor,
            hairStyle = state.hairStyle,
            faceNoseShape = state.faceNoseShape,
            faceBiteShape = state.faceBiteShape,
            faceForehead = state.faceForehead,
            faceMustache = state.faceMustache,
            level = state.level,
            extraAttachments = state.extraAttachments.mapNotNull { id -> GameData.WEAPON_HEADS.find { it.id == id } },
            extraArmors = state.extraArmors.mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } },
            handleExtensionCount = state.handleExtensionCount,
            speedBoost = state.totalSpeedBoost,
            rangedUpgrades = state.rangedUpgrades,
            isMounted = state.unlockedAncillaries.contains("anc_mount_horse")
        )

        // Create Saxon enemies based on level
        // Difficulty scales with performance (kill speed + hp remaining)
        val perfBonus = ((state.performanceScore - 0.5f) * 2f).coerceIn(-0.3f, 0.5f)
        val enemiesCount = (1 + (state.level / 2) + Random.nextInt(0, 2) + (perfBonus * 2).toInt()).coerceAtLeast(1)
        val enemies = List(enemiesCount) { index ->
            generateRandomSaxon(index, state.level)
        }.toMutableList()

        if (state.unlockedAncillaries.contains("anc_fanatic")) {
            enemies.add(FighterState(
                id = "fanatic_boris",
                name = "Mad Boris",
                isPlayer = true,
                maxHp = 150f,
                hp = 150f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_axe" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 80f,
                targetX = 80f,
                facingRight = true,
                size = 1.05f,
                hairColor = androidx.compose.ui.graphics.Color(0xFFC02020),
                hairStyle = "long",
                isDualWielding = true
            ))
        }

        _playerState.value = player
        _enemiesState.value = enemies
        _projectilesState.value = emptyList()
        _popupsState.value = emptyList()
        _particlesState.value = emptyList() // clear blood from previous battle

        _uiState.update {
            it.copy(
                isBattleActive = true,
                battleWon = false,
                battleLost = false,
                playerHp = player.hp,
                playerMaxHp = player.maxHp
            )
        }

        // Play battle start timpani roll
        MedievalAudioSynth.playSound(SoundType.DRUM_ROLL)

        // Launch game loop
        startGameLoop()
    }

    private fun generateRandomSaxon(index: Int, level: Int): FighterState {
        val names = listOf(
            "Harold of Essex", "Gyrth Shield-Cleaver", "Leofwine", "Tostig Dunce", 
            "Aldred the Bald", "Godwin the Grumpy", "Sigurd Skull-Basher", "Ethelred the Unready",
            "Cerdic the Giant", "Wulfric", "Odo the Swift", "Aelfric", "Leofric", "Edric"
        )
        val saxonName = if (index < names.size) names[index] else "Saxon Foe ${index + 1}"

        val rng = kotlin.random.Random(System.currentTimeMillis() + index)
        val r = rng.nextFloat()
        
        val arch = if (level <= 2) {
            if (r < 0.3f) EnemyArchetype.PEASANT else if (r < 0.6f) EnemyArchetype.FYRD_LEVY else if (r < 0.8f) EnemyArchetype.SLINGER else EnemyArchetype.HOUSECARL
        } else if (level <= 4) {
            if (r < 0.2f) EnemyArchetype.PEASANT else if (r < 0.35f) EnemyArchetype.JAVELINEER else if (r < 0.5f) EnemyArchetype.MACEMAN else if (r < 0.65f) EnemyArchetype.ARCHER else if (r < 0.85f) EnemyArchetype.HOUSECARL else EnemyArchetype.SHIELD_WALL
        } else if (level <= 6) {
            if (r < 0.15f) EnemyArchetype.MACEMAN else if (r < 0.3f) EnemyArchetype.PIKEMAN else if (r < 0.45f) EnemyArchetype.SHIELD_WALL else if (r < 0.6f) EnemyArchetype.BERSERKER else if (r < 0.7f) EnemyArchetype.KNIGHT_DISMOUNTED else if (r < 0.8f) EnemyArchetype.CHARIOT_ARCHER else if (r < 0.95f) EnemyArchetype.CAVALRY else EnemyArchetype.LORD
        } else {
            if (r < 0.1f) EnemyArchetype.PIKEMAN else if (r < 0.2f) EnemyArchetype.SHIELD_WALL else if (r < 0.35f) EnemyArchetype.KNIGHT_DISMOUNTED else if (r < 0.5f) EnemyArchetype.BERSERKER else if (r < 0.65f) EnemyArchetype.CAVALRY else if (r < 0.75f) EnemyArchetype.CHARIOT_LANCER else if (r < 0.9f) EnemyArchetype.CHAMPION else EnemyArchetype.KING
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
        val baseHp = 50f + (level * 10f)
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
            weaponHead = gear[0],
            weaponHandle = gear[1],
            shield = gear[2],
            armor = gear[3],
            headgear = gear[4],
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
            speedBoost = if (isMounted && !isChariot) 0.45f else if (isChariot) 0.35f else 0f
        )
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            val dt = 0.033f // 30 FPS tick updates to keep state changes highly robust
            while (_uiState.value.isBattleActive) {
                delay(33) // ~30 FPS
                updateSimulation(dt)
            }
        }
    }

    private fun updateSimulation(dt: Float) {
        val player = _playerState.value ?: return
        val enemies = _enemiesState.value
        val projectiles = _projectilesState.value
        val popups = _popupsState.value

        // 1. Update Floating Combat Popups and Particles
        popups.forEach { it.age += dt }
        _popupsState.value = popups.filter { it.age < 1.2f }
        
        val particles = _particlesState.value
        particles.forEach { 
            it.age += dt 
            if (it.y < 350f || it.isSmoke) {
                it.x += it.vx * dt
                it.y += it.vy * dt
                if (!it.isSmoke) {
                    it.vy += 400f * dt // gravity for blood
                } else {
                    it.vx += (Random.nextFloat() * 10f - 5f) * dt // drifting wind
                }
            }
        }
        _particlesState.value = particles.filter { it.age < it.maxAge }

        if (_uiState.value.unlockedAncillaries.contains("anc_monk")) {
            addIncenseParticles(player.posX - (40f * player.size), 190f, count = 2)
        }

        // Decay screenshake
        if (_screenshake.value > 0f) {
            _screenshake.value = (_screenshake.value - dt * 45f).coerceAtLeast(0f)
        }

        // 2. Handle Game End Conditions
        if (player.isDead) {
            endBattle(won = false)
            return
        }

        val livingEnemies = enemies.filter { !it.isDead }
        if (livingEnemies.isEmpty()) {
            endBattle(won = true)
            return
        }
        val targetableEnemies = enemies.filter { !it.isDead && !it.isDying }

        // 3. Update Player Fighter State
        val closestEnemy = targetableEnemies.minByOrNull { kotlin.math.abs(it.posX - player.posX) }
        updateFighter(player, closestEnemy, dt)
        if (player.ghostHp > player.hp) {
            player.ghostHp -= 20f * dt
            if (player.ghostHp < player.hp) player.ghostHp = player.hp
        }

        // 4. Update Enemy Fighter States (and allied NPCs like Fanatic!)
        enemies.forEach { enemy ->
            val pTarget = if (enemy.isPlayer) {
                enemies.filter { !it.isDead && !it.isDying && !it.isPlayer }.minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
            } else {
                (enemies.filter { !it.isDead && !it.isDying && it.isPlayer } + listOfNotNull(if (!player.isDead && !player.isDying) player else null))
                    .minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
            }
            updateFighter(enemy, pTarget, dt)
            if (enemy.ghostHp > enemy.hp) {
                enemy.ghostHp -= 20f * dt
                if (enemy.ghostHp < enemy.hp) enemy.ghostHp = enemy.hp
            }
        }

        // Trample Logic
        val allFighters = listOf(player) + targetableEnemies
        for (f1 in allFighters) {
            if (f1.isMounted) {
                if (f1.trampleCooldown > 0f) f1.trampleCooldown -= dt
                if (f1.trampleCooldown <= 0f) {
                    for (f2 in allFighters) {
                        // Enemies shouldn't squash player; only player squashes enemies
                        val canSquash = (f1.isPlayer && !f2.isPlayer)
                        if (canSquash && f2.size < f1.size && !f2.isDead && !f2.isDying) {
                            if (abs(f1.posX - f2.posX) < 30f) {
                                applyFlatDamage(15f * f1.size, f2, f1.isPlayer)
                                addPopup("TRAMPLE!", f2.posX, 110f, Color(0xFF6E5536))
                                f1.trampleCooldown = 2.0f
                                break
                            }
                        }
                    }
                }
            }
        }

        // 5. Update Projectiles
        val remainingProjectiles = mutableListOf<Projectile>()
        projectiles.forEach { proj ->
            proj.posX += proj.velocityX * dt
            proj.posY += proj.velocityY * dt
            proj.velocityY += (130f * proj.gravityMult) * dt // Gravity pulling it downwards!

            var hit = false
            // Check collisions
            if (proj.isPlayerOwned) {
                // Hit test against enemies
                for (enemy in livingEnemies) {
                    if (abs(proj.posX - enemy.posX) < 30f && proj.posY in 100f..350f) {
                        applyProjectileDamage(proj, enemy)
                        hit = true
                        break
                    }
                }
            } else {
                // Hit test against player
                if (!player.isDead && abs(proj.posX - player.posX) < 30f && proj.posY in 100f..350f) {
                    applyProjectileDamage(proj, player)
                    hit = true
                }
            }

            // Boundary collision or hit
            if (!hit && proj.posX in -500f..1700f && proj.posY < 350f) {
                remainingProjectiles.add(proj)
            } else if (hit) {
                // Play sound
                if (proj.type == "arrow") {
                    MedievalAudioSynth.playSound(SoundType.THWACK)
                } else {
                    MedievalAudioSynth.playSound(SoundType.THWACK)
                }
            }
        }
        
        // 5b. Archer Entourage Fire
                // 5b. Archer & Crossbowman Entourage Fire
        val hasArcher = _uiState.value.unlockedAncillaries.contains("anc_archer")
        val hasCrossbow = _uiState.value.unlockedAncillaries.contains("anc_crossbowman")
        if (!player.isDead && !player.isDying) {
            val sortedAncs = _uiState.value.unlockedAncillaries.sorted()
            val archerIdx = sortedAncs.indexOf("anc_archer")
            val crossbowIdx = sortedAncs.indexOf("anc_crossbowman")
            
            if (hasArcher && Random.nextFloat() < dt * 0.4f) {
                val dir = if (player.facingRight) 1f else -1f
                val archerOffsetX = -80f - (archerIdx * 50f)
                val spawnX = player.posX + (archerOffsetX * dir)
                remainingProjectiles.add(Projectile(
                    id = "arch__",
                    isPlayerOwned = true, posX = spawnX, posY = 240f,
                    velocityX = dir * (400f + Random.nextFloat() * 80f), velocityY = -30f + (Random.nextFloat() * 10f),
                    damage = 12f, pierce = 8f, blunt = 2f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = false
                ))
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
            }
            if (hasCrossbow && Random.nextFloat() < dt * 0.25f) {
                val dir = if (player.facingRight) 1f else -1f
                val crossbowOffsetX = -80f - (crossbowIdx * 50f)
                val spawnX = player.posX + (crossbowOffsetX * dir)
                remainingProjectiles.add(Projectile(
                    id = "xbow__",
                    isPlayerOwned = true, posX = spawnX, posY = 230f,
                    velocityX = dir * (600f + Random.nextFloat() * 50f), velocityY = -5f,
                    damage = 25f, pierce = 20f, blunt = 10f, type = "arrow",
                    sizeMultiplier = 1f, hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = true
                ))
                MedievalAudioSynth.playSound(SoundType.THWACK)
            }
        }
        
        val newlySpawned = _projectilesState.value.filter { it !in projectiles }
        remainingProjectiles.addAll(newlySpawned)
        _projectilesState.value = remainingProjectiles

        val hasCupbearer = _uiState.value.unlockedAncillaries.contains("anc_cupbearer")
        if (hasCupbearer && Random.nextFloat() < dt * 0.5f && player.hp < player.maxHp) {
            player.hp = (player.hp + 2.5f).coerceAtMost(player.maxHp)
            // Visually, the renderer will animate him walking up
        }

        // Sync player HP to UI State for HUD bar
        _uiState.update { it.copy(playerHp = player.hp, playerMaxHp = player.maxHp) }
        
        _gameTick.value = System.currentTimeMillis()
    }

    private fun updateFighter(fighter: FighterState, target: FighterState?, dt: Float) {
        // Handle Death animation delay
        if (fighter.isDying) {
            fighter.animFrame += dt * 6f
            if (fighter.animFrame >= 6f) {
                fighter.isDying = false
                fighter.isDead = true
                fighter.posX = fighter.posX // Keep it lying on the ground!
            }
            return
        }
        if (fighter.isDead) return

        // Update damage indicators
        if (fighter.damageIndicator != null) {
            fighter.damageIndicatorTimer -= dt
            if (fighter.damageIndicatorTimer <= 0) {
                fighter.damageIndicator = null
            }
        }

        // Poison tick over time
        if (fighter.poisonDuration > 0f) {
            fighter.poisonDuration -= dt
            val poisonDmg = 8f * dt // deals 8 damage per second
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    addPopup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                    addBloodParticles(fighter.posX, 100f, count = 2) // tiny droplets
                }
                applyFlatDamage(poisonDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }
        
        // Bleed tick over time
        if (fighter.bleedDuration > 0f) {
            fighter.bleedDuration -= dt
            val bleedDmg = 12f * dt // deals 12 damage per second
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    addPopup("BLEED!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFFA62B2B))
                    addBloodParticles(fighter.posX, 100f, count = 3)
                }
                applyFlatDamage(bleedDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }

        // Cooldown tick
        if (fighter.attackCooldown > 0) {
            val cooldownRate = if (!fighter.isPlayer && fighter.armor.id == "armor_bare") 1.3f else 1f
            fighter.attackCooldown -= dt * cooldownRate
        }

        // Update Swing Progress
        if (fighter.isAttacking) {
            fighter.swingProgress += dt * (1.2f / fighter.attackSpeedDelay)
            val chainDelay = if (fighter.weaponHandle.id == "handle_chain") 0.15f else 0f
            val effectiveSwingProgress = (fighter.swingProgress - chainDelay).coerceAtLeast(0f)
            
            // Damage connects halfway through the swing visually, or near the end for heavy/chain windups
            val isHeavyOrChain = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe") || fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
            val strikeThreshold = if (isHeavyOrChain) 0.85f else 0.5f

            if (effectiveSwingProgress >= strikeThreshold && !fighter.hasLandedStrike) {
                fighter.hasLandedStrike = true
                if (target != null) {
                    performStrike(fighter, target)
                }
            }
            
            if (effectiveSwingProgress >= 1f) {
                fighter.isAttacking = false
                fighter.hasLandedStrike = false
                fighter.swingProgress = 0f
            }
        }

        // Decide movement & actions
        if (target != null && !target.isDead) {
            val dist = abs(fighter.posX - target.posX)
            val reachPixels = fighter.reach * 40f + 40f // generous hitbox
            val optimalDistance = if (fighter.isRanged) reachPixels * 0.8f else reachPixels
            val isShieldWall = !fighter.isPlayer && fighter.shield.id == "shield_tower"

            fighter.facingRight = target.posX > fighter.posX

            if (dist > optimalDistance) {
                // Walk closer
                val direction = if (target.posX > fighter.posX) 1f else -1f
                val moveMult = if (isShieldWall) 0.6f else 1f
                fighter.posX += direction * fighter.moveSpeed * moveMult * dt
                // Desync animations slightly based on maxHp to avoid identical marching
                fighter.animFrame = (fighter.animFrame + dt * (9f + (fighter.maxHp % 3f))) % 4f 
            } else if (dist < optimalDistance * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                val retreatSpeed = if (fighter.isRanged) fighter.moveSpeed else (fighter.moveSpeed * 0.45f)
                fighter.posX += direction * retreatSpeed * dt
                fighter.animFrame = (fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))) % 4f 
                
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            } else {
                // Wield weapon/Attack!
                fighter.animFrame = 0f // stand
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                    triggerAttack(fighter)
                }
            }
            
            // Clamp position to screen bounds
            fighter.posX = fighter.posX.coerceIn(30f, 970f)

        }
    }

    private fun triggerAttack(fighter: FighterState) {
        fighter.isAttacking = true
        fighter.swingProgress = 0f
        fighter.attackCooldown = fighter.attackSpeedDelay

        // Play melee/ranged swing swoosh sound at start of attack animation
        MedievalAudioSynth.playSound(SoundType.SWOOSH)
    }

    private fun performStrike(attacker: FighterState, defender: FighterState) {
        if (attacker.isRanged) {
            // Spawn arrow or stone projectile
            val isPlayer = attacker.isPlayer
            val dir = if (attacker.facingRight) 1f else -1f
            val startX = attacker.posX + (dir * 25f)
            val startY = 230f
            
            // Stats based on weapon head
            val isSlingshot = attacker.weaponHead.id == "head_slingshot"
            val isJavelin = attacker.weaponHead.id == "head_javelin"
            val projType = if (isSlingshot) "stone" else if (isJavelin) "javelin" else "arrow"
            
            var sizeMult = 1f
            var spikes = false
            var launchedWep: String? = null
            var splash = false
            var poison = false
            var ballista = false
            var finalDmg = attacker.baseDamage
            var finalPierce = attacker.damagePierce
            var finalBlunt = attacker.damageBlunt
            var velY = -55f // slightly arched trajectory
            var velX = dir * 350f
            var gravMult = 1f

            if (isSlingshot) {
                if (attacker.rangedUpgrades.contains("slingshot_bigger")) {
                    sizeMult = 2.2f
                    finalDmg += 10f
                    finalBlunt += 10f
                }
                if (attacker.rangedUpgrades.contains("slingshot_spikes")) {
                    spikes = true
                    finalPierce += 8f
                }
                if (attacker.rangedUpgrades.contains("slingshot_weapon_heads")) {
                    val heads = listOf("head_axe", "head_sword", "head_morningstar", "head_claymore", "head_halberd")
                    launchedWep = heads.random()
                    finalDmg *= 1.5f
                }
                if (attacker.rangedUpgrades.contains("slingshot_splash")) {
                    splash = true
                }
                if (attacker.rangedUpgrades.contains("slingshot_poison")) {
                    poison = true
                }
            } else if (isJavelin) {
                gravMult = 0.4f // glide!
                velY = -30f
                velX = dir * 380f
            } else {
                // Bow performance variance
                val varianceMult = Random.nextFloat() * 0.9f + 0.6f // 0.6 to 1.5
                finalDmg *= varianceMult
                finalPierce *= varianceMult
                velY = -55f + (Random.nextFloat() * 30f - 15f)
                velX = dir * (320f + Random.nextFloat() * 60f)

                if (attacker.isPlayer) {
                    if (varianceMult < 0.8f) {
                        addPopup("POOR DRAW!", attacker.posX, 120f, Color.Gray)
                    } else if (varianceMult > 1.2f) {
                        addPopup("PERFECT RELEASE!", attacker.posX, 120f, Color(0xFF4C613D))
                    } else {
                        addPopup("STEADY AIM!", attacker.posX, 120f, Color(0xFF265063))
                    }
                }

                if (attacker.rangedUpgrades.contains("bow_bigger")) {
                    ballista = true
                    sizeMult = 2.5f
                    finalDmg += 15f
                    velX *= 1.2f
                }
                if (attacker.rangedUpgrades.contains("bow_spikes")) {
                    spikes = true
                    finalPierce *= 1.8f
                }
                if (attacker.rangedUpgrades.contains("bow_weapon_heads")) {
                    val heads = listOf("head_axe", "head_sword", "head_morningstar")
                    launchedWep = heads.random()
                    finalDmg *= 1.4f
                }
            }

            val proj = Projectile(
                id = "proj_${System.currentTimeMillis()}_${Random.nextInt(100)}",
                isPlayerOwned = isPlayer,
                posX = startX,
                posY = startY,
                velocityX = velX,
                velocityY = velY,
                damage = finalDmg,
                pierce = finalPierce,
                blunt = finalBlunt,
                type = projType,
                sizeMultiplier = sizeMult,
                hasSpikes = spikes,
                launchedWeaponId = launchedWep,
                isSplash = splash,
                isPoisonous = poison,
                isBallista = ballista
            )
            _projectilesState.value = _projectilesState.value + proj
        } else {
            // Melee hit
            val reachPixels = attacker.reach * 40f + 40f // generous hitbox
            val isPiercingWeapon = attacker.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd")
            
            // Gather all targets in a line if we are using a piercing weapon
            val targets = if (attacker.isPlayer && isPiercingWeapon) {
                val dir = if (attacker.facingRight) 1f else -1f
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && abs(attacker.posX - it.posX) <= reachPixels && 
                    ((dir > 0 && it.posX >= attacker.posX) || (dir < 0 && it.posX <= attacker.posX))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer && attacker.weaponHandle.id == "handle_double_ended") {
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && abs(attacker.posX - it.posX) <= reachPixels 
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer) {
                val dir = if (attacker.facingRight) 1f else -1f
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && abs(attacker.posX - it.posX) <= reachPixels && 
                    ((dir > 0 && it.posX >= attacker.posX) || (dir < 0 && it.posX <= attacker.posX))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else {
                listOf(defender)
            }
            
            if (targets.isEmpty() || (!targets.contains(defender) && abs(attacker.posX - defender.posX) > reachPixels)) {
                // Missed!
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                return
            }
            
            // Dual Wield miss chance (20% for faster attacks)
            if (attacker.isDualWielding && Random.nextFloat() < 0.2f) {
                addPopup("MISS!", defender.posX, 140f, Color.Gray)
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                return
            }
            
            var damageFalloff = 1f

            for (currTarget in targets) {
                var blockChance = currTarget.shield.defense / 100f
                if (!currTarget.isPlayer && currTarget.shield.id == "shield_tower") blockChance = 0.8f
                
                val shieldBypass = if (attacker.weaponHead.id == "head_flail" || attacker.weaponHead.id == "head_war_flail" || attacker.weaponHandle.id == "handle_flail_chain") 0.4f else 0f
                blockChance *= (1f - shieldBypass)
                
                val isBlocked = currTarget.shield.id != "shield_none" && Random.nextFloat() < blockChance
                
                if (isBlocked) {
                    // Blocked by shield!
                    MedievalAudioSynth.playSound(SoundType.CLANG)
                    
                    // Still take minimal blunt impact damage
                    val blockDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                    if (blockDamage > 5f && kotlin.random.Random.nextBoolean()) MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    applyFlatDamage(blockDamage, currTarget, attacker.isPlayer)
                } else {
                    // Full hit!
                    val distToTarget = abs(attacker.posX - currTarget.posX)
                    val attachmentDmgMultiplier = if (attacker.isRanged && distToTarget > 80f) 0f else 1f
                    val attachSlash = attacker.extraAttachments.sumOf { it.slash.toDouble() * 0.5 }.toFloat()
                    val attachPierce = attacker.extraAttachments.sumOf { it.pierce.toDouble() * 0.5 }.toFloat()
                    val attachBlunt = attacker.extraAttachments.sumOf { it.blunt.toDouble() * 0.5 }.toFloat()
                    val scaleLvl = if (attacker.isPlayer) 1.0f + (attacker.level - 1) * 0.12f else 1.0f
                    val effectiveSlash = ((attacker.damageSlash - attachSlash * scaleLvl) + attachSlash * scaleLvl * attachmentDmgMultiplier) * damageFalloff
                    val effectivePierce = ((attacker.damagePierce - attachPierce * scaleLvl) + attachPierce * scaleLvl * attachmentDmgMultiplier) * damageFalloff
                    val effectiveBlunt = ((attacker.damageBlunt - attachBlunt * scaleLvl) + attachBlunt * scaleLvl * attachmentDmgMultiplier) * damageFalloff
                    val slash = effectiveSlash
                    val pierce = effectivePierce
                    val blunt = effectiveBlunt

                    // Calculate damage reduction based on defender armor
                    // Armor reduces slash and pierce, but blunt damage partially ignores armor
                    val armorFactor = (1f - (currTarget.totalArmor / 100f)).coerceIn(0.1f, 1f)
                    
                    var totalDamage = (slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt
                    
                    // Cupbearer strength bonus!
                    if (attacker.isPlayer && _uiState.value.unlockedAncillaries.contains("anc_cupbearer")) {
                        totalDamage *= 1.25f // 25% strength boost from wine!
                    }

                    applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)

                    // Knock off helmet randomly!
                    if (kotlin.random.Random.nextFloat() < 0.10f && currTarget.headgear.id != "helm_none") {
                        currTarget.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" }
                        MedievalAudioSynth.playSound(SoundType.CLANG)
                        addPopup("HELM LOST!", currTarget.posX, 120f, androidx.compose.ui.graphics.Color.LightGray)
                        
                        // Spawn a particle for the helmet flying off
                        val px = currTarget.posX
                        val py = 80f
                        _particlesState.value = _particlesState.value + BloodParticle(x = px, y = py, vx = (kotlin.random.Random.nextFloat() * 100f - 50f), vy = -200f - kotlin.random.Random.nextFloat() * 100f, color = androidx.compose.ui.graphics.Color.Gray, isSmoke = false)
                    }

                    // Play hit sounds & comedically yell in latin!
                    if (totalDamage > 0f) {
                        val isCrunch = blunt > 15f && kotlin.random.Random.nextFloat() < 0.4f
                        if (isCrunch) {
                            MedievalAudioSynth.playSound(SoundType.CRUNCH)
                        } else {
                            MedievalAudioSynth.playSound(SoundType.THWACK)
                        }
                        
                        // Random blood particles
                        val px = currTarget.posX + (Random.nextFloat() * 20f - 10f)
                        val py = 120f + (Random.nextFloat() * 60f - 30f)
                        _particlesState.value = _particlesState.value + BloodParticle(x = px, y = py, vx = (Random.nextFloat() * 200f - 100f), vy = -150f - Random.nextFloat() * 150f, color = Color(0xFF8B0000))
                    }

                    // Limb loss mechanic! (heavy slash)
                    val canLoseArm = !currTarget.isPlayer || (currTarget.hp / currTarget.maxHp < 0.10f)
                    if (slash > 18f && kotlin.random.Random.nextFloat() < 0.2f && !currTarget.missingArm && canLoseArm) {
                        currTarget.missingArm = true
                        // Disarm off-hand/shield logically
                        if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                            currTarget.isDualWielding = false
                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it.id == "shield_none" }?.let { currTarget.shield = it }
                        }
                        applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)
                        addPopup("-${totalDamage.toInt()}", currTarget.posX, 140f, androidx.compose.ui.graphics.Color.Red)
                        
                        MedievalAudioSynth.playSound(SoundType.THWACK)
                        addPopup("ARM SEVERED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.Red)
                        
                        val px = currTarget.posX
                        val py = 140f
                        _particlesState.value = _particlesState.value + BloodParticle(x = px, y = py, vx = (Random.nextFloat() * 100f - 50f), vy = -200f - Random.nextFloat() * 100f, color = Color(0xFF8B0000), isSmoke = false)
                    }
                    
                    // Crumple mechanic! (heavy blunt)
                    if (blunt > 18f && kotlin.random.Random.nextFloat() < 0.25f && !currTarget.isCrumpled) {
                        currTarget.isCrumpled = true
                        MedievalAudioSynth.playSound(SoundType.CRUNCH)
                        addPopup("CRUMPLED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.DarkGray)
                    }

                    // Splash damage for big heavy weapons!
                    if (attacker.totalMass > 5.0f && attacker.damageSlash > 10f) { // heavy weapon like axe or claymore
                        val splashDmg = (totalDamage * 0.4f).coerceAtLeast(2f)
                        if (attacker.isPlayer) {
                            _enemiesState.value.forEach { enemy ->
                                if (enemy != currTarget && !enemy.isDead && !enemy.isDying && abs(enemy.posX - currTarget.posX) < 100f) {
                                    applyFlatDamage(splashDmg, enemy, attacker.isPlayer)
                                    addPopup("CLEAVE!", enemy.posX, 120f, Color(0xFF9E3624))
                                }
                            }
                        }
                    }
                }
                
                if (isPiercingWeapon) {
                    damageFalloff *= 0.5f // Halve damage for each enemy it passes through
                }
            }
        }
    }

    private fun applyProjectileDamage(proj: Projectile, defender: FighterState) {
        // Ranged hit calculation
        val isBlocked = defender.shield.id != "shield_none" && Random.nextFloat() < (defender.shield.defense / 110f)

        if (isBlocked) {
            MedievalAudioSynth.playSound(SoundType.CLANG)
            if (proj.type == "arrow" || proj.type == "bolt" || proj.type == "javelin") {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, true))
            }
        } else {
            val armorFactor = (1f - (defender.totalArmor / 100f)).coerceIn(0.15f, 1f)
            val totalDamage = (proj.damage * armorFactor) + (proj.blunt * 0.6f)
            applyFlatDamage(totalDamage, defender, proj.isPlayerOwned)

            if (proj.type == "arrow" || proj.type == "bolt" || proj.type == "javelin") {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, false))
            }
            // Apply Poison Upgrade
            if (proj.isPoisonous) {
                defender.poisonDuration = 5.0f
                addPopup("+POISONED+", defender.posX, 120f, Color(0xFF2E7D32))
            }
            
            // Apply Spikes Bleed
            if (proj.hasSpikes) {
                defender.bleedDuration = 4.0f
                addPopup("+BLEEDING+", defender.posX, 120f, Color(0xFFA62B2B))
            }

            // Apply Splash Upgrade
            if (proj.isSplash) {
                addPopup("SPLASH SPLIT!", defender.posX, 110f, Color(0xFF8A7156))
                val splashDmg = (totalDamage * 0.5f).coerceAtLeast(2f)
                if (proj.isPlayerOwned) {
                    _enemiesState.value.forEach { enemy ->
                        if (enemy != defender && !enemy.isDead && !enemy.isDying && abs(enemy.posX - defender.posX) < 120f) {
                            applyFlatDamage(splashDmg, enemy, proj.isPlayerOwned)
                            addPopup("SPLASH!", enemy.posX, 140f, Color(0xFF9E3624))
                        }
                    }
                } else {
                    _playerState.value?.let { player ->
                        if (player != defender && !player.isDead && !player.isDying && abs(player.posX - defender.posX) < 120f) {
                            applyFlatDamage(splashDmg, player, proj.isPlayerOwned)
                            addPopup("SPLASH!", player.posX, 140f, Color(0xFF9E3624))
                        }
                    }
                }
            }
        }
    }

    private fun applyFlatDamage(dmg: Float, defender: FighterState, isPlayerSource: Boolean = false) {
        if (defender.isDead || defender.isDying) return
        var finalDmg = dmg
        if (!defender.isPlayer && defender.armor.id == "armor_bare") {
            finalDmg *= 1.5f
        }
        val finalDmgInt = finalDmg.coerceAtLeast(1f).toInt().toFloat()
        defender.hp = (defender.hp - finalDmgInt).coerceAtLeast(0f)
        defender.damageIndicator = "-${finalDmgInt.toInt()}"
        defender.damageIndicatorTimer = 0.5f
        
        // Trigger screenshake on hit! (Reduced unless absolute unit)
        val player = _playerState.value
        val shakeMultiplier = if ((player?.size ?: 1.0f) > 1.2f) 1.5f else 0.4f
        _screenshake.value = (finalDmgInt * shakeMultiplier).coerceIn(4f, 35f)
        
        // Spawn blood particles based on damage
        addBloodParticles(defender.posX, 160f * defender.size, count = (finalDmgInt / 2).toInt().coerceIn(5, 20))

        if (defender.hp <= 0f) {
            if (defender.isMounted && Random.nextFloat() < 0.5f) {
                defender.hp = 1f
                defender.isMounted = false
                addPopup("DISMOUNTED!", defender.posX, 130f, Color.Gray)
                if (isPlayerSource) _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
                return
            }
            defender.isDying = true
            defender.animFrame = 0f
            defender.deathType = kotlin.random.Random.nextInt(0, 6) // 0 to 5 for different ragdolls
            defender.deathTime = System.currentTimeMillis()
            MedievalAudioSynth.playSound(SoundType.OUCH)
            val deathShout = if (defender.isPlayer) "VÆ MIHI MORTIS!" else "AARRGGHH!"
            addPopup(deathShout, defender.posX, 130f, Color.DarkGray)
            if (isPlayerSource && !defender.isPlayer) {
                _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
            }
        }
    }

    private fun addPopup(text: String, x: Float, y: Float, color: Color) {
        // Disabled per user request to eliminate visual clutter
    }

    private fun addBloodParticles(x: Float, y: Float, count: Int = 10) {
        val newParticles = List(count) {
            BloodParticle(
                x = x + Random.nextInt(-15, 15),
                y = y + Random.nextInt(-20, 10),
                vx = Random.nextFloat() * 240f - 120f,
                vy = Random.nextFloat() * -220f - 80f
            )
        }
        _particlesState.value = _particlesState.value + newParticles
    }

    private fun addIncenseParticles(x: Float, y: Float, count: Int = 2) {
        val newParticles = List(count) {
            BloodParticle(
                x = x + Random.nextInt(-5, 5),
                y = y - Random.nextInt(0, 10),
                vx = Random.nextFloat() * 40f - 20f, // Drift slightly left/right
                vy = Random.nextFloat() * -50f - 20f, // Drift upwards faster
                color = androidx.compose.ui.graphics.Color(0xFFE0E0E0), // Grey/White smoke
                isSmoke = true,
                maxAge = 3.0f + Random.nextFloat() * 2.0f
            )
        }
        _particlesState.value = _particlesState.value + newParticles
    }

    private fun endBattle(won: Boolean) {
        gameLoopJob?.cancel()
        
        _uiState.update { state ->
            val finalMultiplier = state.scoreMultiplier
            val scoreEarned = if (won) (100 * state.level * finalMultiplier).toInt() else 0
            val newScore = state.score + scoreEarned
            val newHighscore = kotlin.math.max(state.highscore, newScore)
            val nextLevel = if (won) state.level + 1 else state.level

            val pendingChoices = mutableListOf<LevelUpChoice>()
            var showLevelUp = false
            var newPerf = state.performanceScore
            if (won) {
                // Compute performance from hp ratio + kill rate
                val hpRatio = (state.playerHp / state.playerMaxHp).coerceIn(0f, 1f)
                val killRate = (state.totalKills.toFloat() / (state.level.toFloat() + 1f)).coerceIn(0f, 1f)
                newPerf = (hpRatio * 0.6f + killRate * 0.4f).coerceIn(0f, 1f)

                val triggerMusicDecision = (state.level % 5 == 0)
                // 1. Follower option
                val availableAncs = GameData.ANCILLARIES.filter { it.id !in state.unlockedAncillaries }
                if (availableAncs.isNotEmpty()) {
                    val anc = availableAncs.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "follower_${anc.id}",
                        title = "Rally: ${anc.name} the ${anc.role}",
                        description = "${anc.description} (Entourage follower: Max HP +${anc.hpBoost.toInt()}, speed +${(anc.speedBoost * 100).toInt()}%)",
                        type = "follower",
                        itemId = anc.id
                    ))
                } else {
                    // Fallback boost if all followers are hired
                    pendingChoices.add(LevelUpChoice(
                        id = "boost_hp",
                        title = "Drill: Follower Vitality Boost",
                        description = "Train your squires to carry extra medical wine flagons (+25 Max HP).",
                        type = "follower",
                        itemId = "anc_squire"
                    ))
                }

                // 2. Weapon Attachment option (only for melee weapons)
                if (!state.weaponHead.isRanged) {
                    val attachmentHeads = GameData.WEAPON_HEADS.filter { 
                        it.id !in listOf("head_bare", "head_bow", "head_longbow", "head_slingshot") 
                    }
                    val weaponHead = attachmentHeads.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "attach_${weaponHead.id}",
                        title = "Attach Head: ${weaponHead.name}",
                        description = "${weaponHead.description} Attached dynamically to weapon, adding +50% of its base damage!",
                        type = "attachment",
                        itemId = weaponHead.id
                    ))
                }

                // 3. Handle Extension or Layered Armor option
                if (Random.nextBoolean()) {
                    pendingChoices.add(LevelUpChoice(
                        id = "extension",
                        title = "Haft Upgrade: Handle Extension",
                        description = "Lash an additional 1.5-foot wood shaft extension to your grip. Drastically increases reach (+0.35m) and supports more attachments!",
                        type = "extension",
                        itemId = ""
                    ))
                } else {
                    val armorOptions = GameData.ARMOR_PIECES.filter { it.id != "armor_bare" }
                    val armorPiece = armorOptions.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "armor_${armorPiece.id}",
                        title = "Layer Armor: ${armorPiece.name}",
                        description = "Drape ${armorPiece.name} layered directly on top of your current armor, gaining +${armorPiece.defense.toInt()} Defense!",
                        type = "armor",
                        itemId = armorPiece.id
                    ))
                }

                // 4. Ranged Upgrades (only if current weapon is ranged!)
                if (state.weaponHead.isRanged) {
                    val isSlingshot = state.weaponHead.id == "head_slingshot"
                    val possibleUpgrades = if (isSlingshot) {
                        listOf(
                            LevelUpChoice("ranged_slingshot_bigger", "Sling: Giant Cobble-Stones", "Hurl massive river boulders instead of pebbles! Deals +10 blunt damage and increases projectile size.", "ranged_upgrade", "slingshot_bigger"),
                            LevelUpChoice("ranged_slingshot_spikes", "Sling: Barb-Wrapped Stones", "Wrap your lead shots in rusty iron barbs for +8 piercing damage and bleeding.", "ranged_upgrade", "slingshot_spikes"),
                            LevelUpChoice("ranged_slingshot_weapon_heads", "Sling: Weapon-Head Launcher", "Why hurl stones when you can sling mini battle-axes and morningstars?! Fires random weapon head shapes for massive hybrid damage!", "ranged_upgrade", "slingshot_weapon_heads"),
                            LevelUpChoice("ranged_slingshot_splash", "Sling: Shrapnel Stones", "Stones explode into sharp flint splinters upon hitting, dealing splash damage to nearby foes!", "ranged_upgrade", "slingshot_splash"),
                            LevelUpChoice("ranged_slingshot_poison", "Sling: Swamp-Mud Poison", "Dip your stones in venomous Hastings swamp slime. Poisons foes, dealing damage over time!", "ranged_upgrade", "slingshot_poison")
                        )
                    } else {
                        listOf(
                            LevelUpChoice("ranged_bow_bigger", "Bow: Ballista Spears", "Launch thick spear-shafts instead of arrows! High velocity, +15 damage, and knocks foes back.", "ranged_upgrade", "bow_bigger"),
                            LevelUpChoice("ranged_bow_spikes", "Bow: Bodkin Barb-Points", "Solder razor-sharp steel claws to your arrowheads. Ignores 50% armor and deals bleeding.", "ranged_upgrade", "bow_spikes"),
                            LevelUpChoice("ranged_bow_weapon_heads", "Bow: Weapon-Tipped Shafts", "Fletch actual miniature iron morningstars and axes onto your arrows. Complete comedic over-engineering!", "ranged_upgrade", "bow_weapon_heads")
                        )
                    }
                    
                    val availableRanged = possibleUpgrades.filter { it.itemId !in state.rangedUpgrades }
                    if (availableRanged.isNotEmpty()) {
                        pendingChoices.add(availableRanged.random())
                    }
                }
                
                showLevelUp = true
            }

            state.copy(
                isBattleActive = true, // Keep it active so the tapestry remains drawn with overlays
                battleWon = won,
                battleLost = !won,
                score = newScore,
                highscore = newHighscore,
                level = nextLevel,
                pendingLevelUpChoices = pendingChoices,
                showLevelUpScreen = showLevelUp,
                performanceScore = newPerf,
                showMusicDecision = if (won && (state.level % 5 == 0)) true else state.showMusicDecision,
                pendingMusicOptions = if (won && (state.level % 5 == 0)) {
                    val allMusic = listOf("More Tempo", "Merrier", "More Solemn", "Wilder", "Nobler")
                    allMusic.shuffled().take(2) + "No Change"
                } else state.pendingMusicOptions
            )
        }

        if (won) {
            // Victory voice clip will play via MainActivity
        } else {
            // reset score on defeat so they start over
            _uiState.update { it.copy(score = 0) }
        }
    }

    fun dismissBattleResult() {
        _uiState.update { state ->
            val isGameOver = state.battleLost
            if (isGameOver) {
                // Generate a new song seed for the next run!
                MedievalHarpPlayer.newGame()
                val initialGear = mutableSetOf<String>()
                initialGear.add("head_bare")
                initialGear.add("handle_fists")
                initialGear.add("shield_none")
                initialGear.add("armor_bare")
                initialGear.add("helm_none")
                initialGear.addAll(GameData.WEAPON_HEADS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.WEAPON_HANDLES.shuffled().take(3).map { it.id })
                initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.ARMOR_PIECES.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.HEADGEAR_PIECES.shuffled().take(2).map { it.id })
                
                val rng = kotlin.random.Random.Default
                val size = state.characterSize
                val firstNames = if (size > 1.1f) {
                    listOf("Guillaume", "Hugo", "Rollo", "Thorold", "Drogo", "Godfrey", "Taillefer", "Balduin", "Ranulf", "Fulk", "Goliath", "Gros-Jean", "Robert", "Richard", "Odo", "William", "Geoffrey", "Eustace")
                } else if (size < 0.9f) {
                    listOf("Pippin", "Leofric", "Giles", "Alan", "Eustace", "Aethelred", "Wimund", "Bodo", "Osbern", "Wulfric", "Little John", "Alberic", "Berengar", "Drogo", "Erfast")
                } else {
                    listOf("Arthur", "Lancelot", "Gawain", "Percival", "Bors", "Gareth", "Tristan", "Bedivere", "Galahad", "Kay", "Odo", "William", "Robert", "Richard", "Roger", "Hugh")
                }
                val firstName = firstNames.random(rng)
                val lastName = state.playerName.split(" ").drop(1).joinToString(" ").ifEmpty { "the Unknown" }
                val newName = "$firstName $lastName"

                // Completely random starter gear for the next attempt (each attempt starts fresh and unique!)
                state.copy(
                    isBattleActive = false,
                    battleWon = false,
                    battleLost = false,
                    level = 1,
                    gameCount = state.gameCount + 1,
                    totalKills = 0,
                    playerName = newName,
                    faceNoseShape = (0..3).random(rng),
                    faceBiteShape = (0..3).random(rng),
                    faceForehead = (0..2).random(rng),
                    faceMustache = (0..3).random(rng),
                    unlockedGearIds = initialGear,
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    rangedUpgrades = emptyList(),
                    unlockedAncillaries = emptySet(),
                    weaponHead = GameData.WEAPON_HEADS.filter { it.id in initialGear }.random(),
                    weaponHandle = GameData.WEAPON_HANDLES.filter { it.id in initialGear && it.id != "handle_fists" }.randomOrNull() ?: GameData.WEAPON_HANDLES[1],
                    shield = GameData.SHIELDS.filter { it.id in initialGear }.random(),
                    armor = GameData.ARMOR_PIECES.filter { it.id in initialGear }.random(),
                    headgear = GameData.HEADGEAR_PIECES.filter { it.id in initialGear }.random()
                )
            } else {
                state.copy(
                    isBattleActive = false,
                    battleWon = false,
                    battleLost = false
                )
            }
        }
    }


    fun clearSkipBonus() {
        _uiState.update { it.copy(pendingSkipBonus = 0) }
    }

    override fun onCleared() {
        gameLoopJob?.cancel()
        super.onCleared()
    }
}

// Utility extension
fun Float.coerceIn(min: Float, max: Float): Float = if (this < min) min else if (this > max) max else this
