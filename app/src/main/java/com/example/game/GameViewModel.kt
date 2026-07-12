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
    private var pendingReinforcements = 0

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
        initialGear.addAll(GameData.ARMOR_PIECES.filter { it.id !in listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_jester") }.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.HEADGEAR_PIECES.filter { it.id != "helm_jester" }.shuffled().take(2).map { it.id })

        _uiState.update { it.copy(
            highscore = 0,
            unlockedGearIds = initialGear,
            hasThroneOption = kotlin.random.Random.nextFloat() < 0.2f
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
            val newArmors = if (choice.type == "armor" || choice.type == "comedy") state.extraArmors + choice.itemId else state.extraArmors
            val newAncs = if (choice.type == "follower") state.unlockedAncillaries + GameData.ANCILLARIES.first { it.id == choice.itemId } else state.unlockedAncillaries
            val newExtensions = if (choice.type == "extension") state.handleExtensionCount + 1 else state.handleExtensionCount
            val newRangedUpgrades = if (choice.type == "ranged_upgrade") state.rangedUpgrades + choice.itemId else state.rangedUpgrades
            val newShieldUpgrades = if (choice.type == "shield_upgrade") state.shieldUpgrades + choice.itemId else state.shieldUpgrades
            val newBrawlerUpgrades = if (choice.type == "brawler_upgrade") state.brawlerUpgrades + choice.itemId else state.brawlerUpgrades
            
            var newHeadgear = state.headgear
            if (choice.itemId == "armor_jester") {
                newHeadgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_jester" }
            }
            
            // Gain dynamic buffs based on items or ancillaries selected
            state.copy(
                headgear = newHeadgear,
                extraAttachments = newAttachments,
                extraArmors = newArmors,
                unlockedAncillaries = newAncs,
                handleExtensionCount = newExtensions,
                rangedUpgrades = newRangedUpgrades,
                shieldUpgrades = newShieldUpgrades,
                brawlerUpgrades = newBrawlerUpgrades,
                showLevelUpScreen = false,
                pendingLevelUpChoices = emptyList()
            )
        }
    }

    fun selectMusicMood(mood: String) {
        _uiState.update { state ->
            val applied = if (mood != "No Change") state.appliedMusicMoods + mood else state.appliedMusicMoods
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
                ItemType.WEAPON_HEAD -> state.copy(weaponHead = item as GameData.WeaponHead)
                ItemType.WEAPON_HANDLE -> state.copy(weaponHandle = item as GameData.WeaponHandle)
                ItemType.SHIELD -> state.copy(shield = item as GameData.Shield, isDualWielding = if (item.id != "shield_none") false else state.isDualWielding)
                ItemType.ARMOR -> state.copy(armor = item as GameData.ArmorPiece)
                ItemType.HEADGEAR -> state.copy(headgear = item as GameData.HeadgearPiece)
            }
            
            // Enforce two-handed rule
            val isTwoHanded = newState.weaponHead in listOf(GameData.WeaponHead.CLAYMORE, GameData.WeaponHead.LONGBOW, GameData.WeaponHead.HALBERD, GameData.WeaponHead.PIKE, GameData.WeaponHead.SCYTHE, GameData.WeaponHead.BOW)
            if (isTwoHanded && newState.shield != GameData.Shield.NONE) {
                newState = newState.copy(shield = GameData.SHIELDS.first { it == GameData.Shield.NONE })
            }
            
            // Enforce no dual wield for bows
            if (newState.weaponHead in listOf(GameData.WeaponHead.BOW, GameData.WeaponHead.LONGBOW)) {
                newState = newState.copy(isDualWielding = false)
            }
            
            // Enforce handle removal for ranged (except javelin) and fists
            val isRangedNoJavelin = newState.weaponHead.isRanged && newState.weaponHead != GameData.WeaponHead.JAVELIN
            val isBareFists = newState.weaponHead == GameData.WeaponHead.BARE
            if ((isRangedNoJavelin || isBareFists) && newState.weaponHandle != GameData.WeaponHandle.FISTS) {
                if (item.type == ItemType.WEAPON_HEAD) {
                    newState = newState.copy(weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" })
                } else if (item.type == ItemType.WEAPON_HANDLE) {
                    // if they are trying to select a handle while holding a bow, remove the bow
                    newState = newState.copy(weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" })
                }
            } else if (!isRangedNoJavelin && !isBareFists && newState.weaponHandle.id == "handle_fists") {
                // Auto-select a handle if switching from fists/ranged to a standard melee weapon
                val availableHandles = GameData.WEAPON_HANDLES.filter { it.id in state.unlockedGearIds && it.id != "handle_fists" }
                val fallbackHandle = if (availableHandles.isNotEmpty()) availableHandles.first() else GameData.WEAPON_HANDLES.first { it.id == "handle_short" }
                newState = newState.copy(weaponHandle = fallbackHandle)
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
        val currentHead = _uiState.value.weaponHead.id
        if (!_uiState.value.isDualWielding && (currentHead == "head_bow" || currentHead == "head_longbow")) {
            return // Cannot dual wield bows!
        }
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
            val isTwoHanded = newState.weaponHead in listOf(GameData.WeaponHead.CLAYMORE, GameData.WeaponHead.LONGBOW, GameData.WeaponHead.HALBERD, GameData.WeaponHead.PIKE, GameData.WeaponHead.SCYTHE, GameData.WeaponHead.BOW)
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
        // Soften the size-HP penalty so small builds stay viable (0.8 size → ~0.95x HP, not 0.8x)
        val totalPlayerMaxHp = baseHp * (0.75f + 0.25f * state.characterSize)
        val player = FighterState(
            id = "player_knight",
            name = state.playerName,
            isPlayer = true,
            maxHp = totalPlayerMaxHp,
            hp = totalPlayerMaxHp,
            // In throne mode the lord doesn't fight — his gear goes to the front pallbearers
            weaponHead = if (state.isThroneMode) GameData.WEAPON_HEADS.first { it.id == "head_bare" } else state.weaponHead,
            weaponHandle = if (state.isThroneMode) GameData.WEAPON_HANDLES.first { it.id == "handle_fists" } else state.weaponHandle,
            shield = if (state.isThroneMode) GameData.SHIELDS.first { it.id == "shield_none" } else state.shield,
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
            shieldUpgrades = state.shieldUpgrades,
            brawlerUpgrades = state.brawlerUpgrades,
            shieldHp = state.shield.defense * 2f + if (state.shieldUpgrades.contains("oak_reinforcing")) 50f else 0f + if (state.shieldUpgrades.contains("iron_plating")) 100f else 0f + if (state.shieldUpgrades.contains("shield_helmet")) 40f else 0f,
            isMounted = state.unlockedAncillaries.contains(Ancillary.WARHORSE) || state.unlockedAncillaries.contains(Ancillary.CHARIOT) || state.unlockedAncillaries.contains(Ancillary.STILTS) || state.isThroneMode,
            mountHp = if (state.isThroneMode) 100f else if (state.unlockedAncillaries.contains(Ancillary.STILTS)) 40f else if (state.unlockedAncillaries.contains(Ancillary.CHARIOT)) 100f else if (state.unlockedAncillaries.contains(Ancillary.WARHORSE)) 80f else 0f,
            isChariot = state.unlockedAncillaries.contains(Ancillary.CHARIOT),
            isStilts = !state.isThroneMode && state.unlockedAncillaries.contains(Ancillary.STILTS),
            isLord = state.isThroneMode,
            bandagesCount = state.bandagesCount
        )
        
        if (state.isThroneMode) {
            player.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
            player.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }
            player.shield = GameData.SHIELDS.first { it.id == "shield_none" }
        }

        // Create Saxon enemies based on level
        // Difficulty scales with performance (kill speed + hp remaining)
        val perfBonus = ((state.performanceScore - 0.5f) * 2f).coerceIn(-0.3f, 0.5f)
        val rawEnemiesCount = (1 + (state.level / 2) + Random.nextInt(0, 2) + (perfBonus * 2).toInt()).coerceAtLeast(1)
        val enemiesCount = rawEnemiesCount.coerceAtMost(10)
        // Overflow beyond the on-screen cap arrives as reinforcements from the right once
        // the battle scrolls past dead foes — longer battles instead of inflated HP.
        pendingReinforcements = (rawEnemiesCount - enemiesCount).coerceIn(0, 8)
        val enemies = List(enemiesCount) { index ->
            generateRandomSaxon(index, state.level)
        }.toMutableList()

        if (state.unlockedAncillaries.contains(Ancillary.FANATIC)) {
            enemies.add(FighterState(
                id = "fanatic_boris",
                name = "Mad Boris",
                isPlayer = true,
                maxHp = 150f,
                hp = 150f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_axe" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it == GameData.Shield.NONE },
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

        if (state.unlockedAncillaries.contains(Ancillary.HAG)) {
            enemies.add(FighterState(
                // head_slingshot makes her isRanged, so the AI kites at range and lobs mud instead of rushing to melee
                id = "hag", name = "Local Hag", isPlayer = true, maxHp = 40f, hp = 40f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 20f, targetX = 20f, facingRight = true, size = 0.8f, hairColor = androidx.compose.ui.graphics.Color(0xFFAAAAAA), hairStyle = "long", isDualWielding = false
            ))
        }

        if (state.unlockedAncillaries.contains(Ancillary.TROJAN_HORSE)) {
            enemies.add(FighterState(
                id = "trojan_horse", name = "Trojan Horse", isPlayer = true, maxHp = 200f, hp = 200f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 180f, targetX = 180f, facingRight = true, size = 1.8f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = false
            ))
        }
        
        if (state.unlockedAncillaries.contains(Ancillary.WARDOG)) {
            enemies.add(FighterState(
                id = "wardog", name = "Buster", isPlayer = true, maxHp = 75f, hp = 75f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 70f, targetX = 70f, facingRight = true, size = 1.1f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = true,
                speedBoost = 1.0f
            ))
        }

        if (state.unlockedAncillaries.contains(Ancillary.RAVEN)) {
            enemies.add(FighterState(
                id = "raven", name = "Munin", isPlayer = true, maxHp = 20f, hp = 20f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 50f, targetX = 50f, facingRight = true, size = 0.35f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = true,
                speedBoost = 1.2f
            ))
        }
        
        if (state.isThroneMode) {
            // Dedicated retinue of four. Front pair (0,1) inherit the lord's gear and fight:
            // one wields his weapon, the other carries his shield (or the weapon again when
            // dual-wielding). Rear pair (2,3) only carry the throne.
            for (i in 0 until 4) {
                val isFront = i < 2
                enemies.add(FighterState(
                    id = "pallbearer_$i", name = "Pallbearer", isPlayer = true,
                    maxHp = 70f, hp = 70f,
                    weaponHead = when {
                        i == 0 -> state.weaponHead
                        i == 1 && state.isDualWielding -> state.weaponHead
                        else -> GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                    },
                    weaponHandle = if (isFront) state.weaponHandle else GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                    shield = if (i == 1 && !state.isDualWielding) state.shield else GameData.SHIELDS.first { it.id == "shield_none" },
                    armor = state.armor,
                    headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                    posX = player.posX, targetX = player.posX, facingRight = true, size = 0.95f,
                    hairColor = androidx.compose.ui.graphics.Color(0xFF5A442E), hairStyle = "short",
                    isDualWielding = false,
                    pallbearerIndex = i
                ))
            }
        }

        _playerState.value = player
        _enemiesState.value = enemies
        _projectilesState.value = emptyList()
        _popupsState.value = emptyList()
        _particlesState.value = emptyList() // clear blood from previous battle

        // Generate Environment
        val levelWidth = if (state.level == 1) 1500f else if (state.level >= 5) 2500f else 1000f + (Random.nextFloat() * 500f)
        val bgObjects = mutableListOf<BackgroundObject>()
        
        if (state.level == 1) {
            // Level 1: Ship at spawn
            // Boat hugs the bottom-left edge (hull draws ~±190 around posX); player lands on the beach beside it
            bgObjects.add(BackgroundObject("ship_0", BackgroundObjectType.SHIP, -40f, 0f, 400f, 500f, 500f))
            player.posX = 220f
            player.targetX = 220f
        } else {
            // Generate some range cover buildings or forts
            val numBuildings = if (state.level >= 5) Random.nextInt(1, 4) else Random.nextInt(0, 2)
            for (i in 0 until numBuildings) {
                val bx = 300f + Random.nextFloat() * (levelWidth - 600f)
                val type = if (state.level >= 5 && i == 0) {
                    listOf(
                        BackgroundObjectType.FORT_DINAN, BackgroundObjectType.FORT_PALACE,
                        BackgroundObjectType.FORT_TOWER, BackgroundObjectType.FORT_MOTTE
                    ).random()
                } else {
                    if (Random.nextBoolean()) BackgroundObjectType.BUILDING_BOSHAM else BackgroundObjectType.BUILDING_MANOR
                }
                val hp = if (type in listOf(BackgroundObjectType.FORT_DINAN, BackgroundObjectType.FORT_PALACE, BackgroundObjectType.FORT_TOWER, BackgroundObjectType.FORT_MOTTE)) 1000f else 300f
                bgObjects.add(BackgroundObject("bg_$i", type, bx, 0f, 300f, hp, hp))
            }
        }

        _uiState.update {
            it.copy(
                isBattleActive = true,
                battleWon = false,
                battleLost = false,
                playerHp = player.hp,
                playerMaxHp = player.maxHp,
                levelWidth = levelWidth,
                cameraX = 0f,
                backgroundObjects = bgObjects
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
        var enemies = _enemiesState.value
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

        if (_uiState.value.unlockedAncillaries.contains(Ancillary.MONK)) {
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

        val livingEnemies = enemies.filter { !it.isDead && !it.isPlayer }
        if (livingEnemies.isEmpty()) {
            endBattle(won = true)
            return
        }
        val targetableEnemies = enemies.filter { !it.isDead && !it.isDying && !it.isPlayer }

        // 3. Update Player Fighter State
        val closestEnemy = targetableEnemies.minByOrNull { kotlin.math.abs(it.posX - player.posX) }
        updateFighter(player, closestEnemy, dt)
        if (player.ghostHp > player.hp) {
            player.ghostHp -= 20f * dt
            if (player.ghostHp < player.hp) player.ghostHp = player.hp
        }

        val newEnemiesToSpawn = mutableListOf<FighterState>()
        // 4. Update Enemy Fighter States (and allied NPCs like Fanatic!)
        enemies.forEach { enemy ->
            val wasDead = enemy.isDead
            val pTarget = if (enemy.isPlayer) {
                enemies.filter { !it.isDead && !it.isDying && !it.isPlayer }.minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
            } else {
                // Enemies ignore the trojan horse decoy until it has rolled past them
                (enemies.filter { !it.isDead && !it.isDying && it.isPlayer && (it.id != "trojan_horse" || it.posX > enemy.posX) } + listOfNotNull(if (!player.isDead && !player.isDying) player else null))
                    .minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
            }
            updateFighter(enemy, pTarget, dt)
            if (enemy.ghostHp > enemy.hp) {
                enemy.ghostHp -= 20f * dt
                if (enemy.ghostHp < enemy.hp) enemy.ghostHp = enemy.hp
            }
            
            // Trojan Horse death spawn
            if (!wasDead && enemy.isDead && enemy.id == "trojan_horse") {
                for (i in 0 until 3) {
                    newEnemiesToSpawn.add(FighterState(
                        id = "trojan_knight_${System.currentTimeMillis()}_$i", name = "Trojan Spearman", isPlayer = true,
                        maxHp = 45f, hp = 45f,
                        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" },
                        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                        shield = GameData.SHIELDS.first { it.id == "shield_none" },
                        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
                        posX = enemy.posX + Random.nextInt(-40, 40),
                        targetX = enemy.posX, facingRight = true, size = 0.95f, hairColor = Color.Black, hairStyle = "short", isDualWielding = false
                    ))
                }
                MedievalAudioSynth.playSound(SoundType.CRUNCH)
            }
        }
        // Reinforcements: once dead foes have scrolled off the left edge and the field has
        // thinned, feed in the overflow enemies from the right so later battles run longer.
        val camX = _uiState.value.cameraX
        if (pendingReinforcements > 0 &&
            enemies.count { !it.isDead && !it.isDying && !it.isPlayer } < 5 &&
            enemies.any { it.isDead && !it.isPlayer && it.posX < camX - 30f }
        ) {
            val reinforcement = generateRandomSaxon(enemies.size + kotlin.random.Random.nextInt(10000), _uiState.value.level)
            reinforcement.posX = (camX + 1080f).coerceAtMost(_uiState.value.levelWidth - 20f)
            reinforcement.targetX = reinforcement.posX
            newEnemiesToSpawn.add(reinforcement)
            pendingReinforcements--
        }

        if (newEnemiesToSpawn.isNotEmpty()) {
            enemies = enemies + newEnemiesToSpawn
            _enemiesState.value = enemies
        }
        
        // --- Cleanup offscreen dead enemies and background objects to fix lag ---
        val offscreenLeft = camX - 350f
        val cleanEnemies = enemies.toMutableList()
        val toRemove = mutableSetOf<FighterState>()
        
        val deadEnemies = cleanEnemies.filter { it.isDead && !it.isPlayer }
        val piles = mutableMapOf<Int, MutableList<FighterState>>()
        for (dead in deadEnemies) {
            if (dead.posX < offscreenLeft) {
                toRemove.add(dead)
                continue
            }
            val bucket = (dead.posX / 40).toInt()
            piles.getOrPut(bucket) { mutableListOf() }.add(dead)
        }
        for (pile in piles.values) {
            if (pile.size > 5) {
                toRemove.addAll(pile.take(pile.size - 5))
            }
        }
        if (toRemove.isNotEmpty()) {
            cleanEnemies.removeAll(toRemove)
            enemies = cleanEnemies
            _enemiesState.value = enemies
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
        val bgObjects = _uiState.value.backgroundObjects
        
        projectiles.forEach { proj ->
            proj.posX += proj.velocityX * dt
            proj.posY += proj.velocityY * dt
            proj.velocityY += (130f * proj.gravityMult) * dt // Gravity pulling it downwards!

            var hit = false
            // Check building collisions
            // Ship is pure background scenery — it never blocks projectiles
            val bgHit = bgObjects.firstOrNull { !it.isDestroyed && it.type != BackgroundObjectType.SHIP && proj.posX in (it.posX - 100f)..(it.posX + 100f) && proj.posY > 100f }
            if (bgHit != null && proj.posY < 350f) {
                hit = true
                bgHit.hp -= proj.damage
                if (proj.type == "arrow" || proj.type == "bolt" || proj.type == "javelin") {
                    if (proj.velocityX > 0) bgHit.stuckArrowsFromLeft++ else bgHit.stuckArrowsFromRight++
                }
                if (bgHit.hp <= 0) bgHit.isDestroyed = true
            }
            
            // Check entity collisions if not hit building
            if (!hit) {
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
        }

        // Boundary collision or hit
            if (!hit && proj.posX in -500f..(_uiState.value.levelWidth + 500f) && proj.posY < 350f) {
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
        val hasArcher = _uiState.value.unlockedAncillaries.contains(Ancillary.ARCHER)
        val hasCrossbow = _uiState.value.unlockedAncillaries.contains(Ancillary.CROSSBOWMAN)
        if (!player.isDead && !player.isDying) {
            val sortedAncs = _uiState.value.unlockedAncillaries.sortedBy { it.name }
            val archerIdx = sortedAncs.indexOf(Ancillary.ARCHER)
            val crossbowIdx = sortedAncs.indexOf(Ancillary.CROSSBOWMAN)
            
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

        val hasCupbearer = _uiState.value.unlockedAncillaries.contains(Ancillary.CUPBEARER)
        if (hasCupbearer && Random.nextFloat() < dt * 0.5f && player.hp < player.maxHp) {
            player.hp = (player.hp + 2.5f).coerceAtMost(player.maxHp)
            // Visually, the renderer will animate him walking up
        }

        // Surgeon is a non-combatant follower (drawn via drawAncillaries), heal is passive
        val hasSurgeon = _uiState.value.unlockedAncillaries.contains(Ancillary.SURGEON)
        if (hasSurgeon && player.hp < player.maxHp) {
            player.hp = (player.hp + 4.0f * dt).coerceAtMost(player.maxHp)
            if (Random.nextFloat() < dt * 0.8f) {
                addPopup("+HEAL+", player.posX + Random.nextInt(-20, 20), 100f, Color.Green)
            }
        }

        // Buster barks every now and then mid-battle (rare, for comedy)
        if (enemies.any { it.id == "wardog" && !it.isDead && !it.isDying } && Random.nextFloat() < dt * 0.2f) {
            MedievalAudioSynth.playDogBark()
        }

        val hasLilGuy = _uiState.value.unlockedAncillaries.contains(Ancillary.LIL_GUY)
        if (hasLilGuy && !player.isDead && Random.nextFloat() < dt * 0.7f) {
            val dir = if (player.facingRight) 1f else -1f
            val spawnX = player.posX - (15f * dir) // shoot from player's back
            remainingProjectiles.add(Projectile(
                id = "lilguy_${System.currentTimeMillis()}_${Random.nextInt(100)}",
                isPlayerOwned = true, posX = spawnX, posY = 150f,
                velocityX = dir * (300f + Random.nextFloat() * 80f), velocityY = -25f,
                damage = 4f, pierce = 2f, blunt = 1f, type = "rock",
                sizeMultiplier = 0.5f, hasSpikes = false, launchedWeaponId = null,
                isSplash = false, isPoisonous = false, isBallista = false
            ))
            MedievalAudioSynth.playSound(SoundType.SWOOSH)
        }

        // Sync player HP to UI State for HUD bar
        val screenWidth = 1000f // Game canvas width
        val newCameraX = (player.posX - screenWidth / 2f).coerceIn(0f, kotlin.math.max(0f, _uiState.value.levelWidth - screenWidth))
        
        _uiState.update { it.copy(playerHp = player.hp, playerMaxHp = player.maxHp, cameraX = newCameraX) }
        
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

        // Trojan horse never fights: it rolls right past the enemy line, then bursts open
        if (fighter.id == "trojan_horse") {
            val foes = _enemiesState.value.filter { !it.isDead && !it.isDying && !it.isPlayer }
            if (foes.any { it.posX > fighter.posX - 60f } && fighter.posX < _uiState.value.levelWidth - 80f) {
                fighter.posX += fighter.moveSpeed * 0.7f * dt
                fighter.animFrame += dt * 6f
                fighter.facingRight = true
            } else {
                // Behind every foe on the field — the belly bursts open
                fighter.isDying = true
                fighter.animFrame = 0f
                fighter.deathType = 0
                fighter.deathTime = System.currentTimeMillis()
            }
            return
        }

        // Ease out any stale wrestling lift (attacker died/switched targets mid-move);
        // an active lift re-sets this every tick so it wins over the decay
        if (fighter.visualOffsetY != 0f) {
            val decay = 250f * dt
            fighter.visualOffsetY = if (abs(fighter.visualOffsetY) <= decay) 0f
                else fighter.visualOffsetY + if (fighter.visualOffsetY < 0f) decay else -decay
        }

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
            val poisonDmg = 4f * dt // deals 4 damage per second (halved)
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) { // occasionally show green "+POISON+" popup
                    addPopup("POISON!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFF2E7D32))
                }
                applyFlatDamage(poisonDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }
        
        // Bleed tick over time
        if (fighter.bleedDuration > 0f) {
            fighter.bleedDuration -= dt
            val bleedDmg = 3f * dt // 3 damage per second — 6/s melted enemies too fast
            if (fighter.hp > 0f) {
                if (Random.nextFloat() < dt * 1.5f) {
                    addPopup("BLEED!", fighter.posX + Random.nextInt(-10, 10), 130f, Color(0xFFA62B2B))
                    addBloodParticles(fighter.posX, 100f, count = 3)
                }
                applyFlatDamage(bleedDmg, fighter, isPlayerSource = !fighter.isPlayer)
            }
        }

        // Slow tick over time
        if (fighter.slowDuration > 0f) {
            fighter.slowDuration -= dt
        }

        // Crumple tick over time
        if (fighter.crumpleDuration > 0f) {
            fighter.crumpleDuration -= dt
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
            val isChain = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain") || fighter.weaponHead.id in listOf("head_flail", "head_war_flail")
            val isHeavy = fighter.weaponHead.id in listOf("head_claymore", "head_maul", "head_axe", "head_lucerne", "head_saber")
            val isChokeSlam = fighter.activeWrestlingMove == "choke_slam"
            val isSuplex = fighter.activeWrestlingMove == "suplex"
            val strikeThreshold = if (isChokeSlam) 0.7f else if (isChain) 0.65f else if (isHeavy) 0.85f else 0.5f

            if (fighter.weaponHandle.id == "handle_fists" && fighter.weaponHead.id == "head_bare" && target != null && !target.isDead) {
                // Apply visual lift for wrestling moves
                val distToTarget = kotlin.math.abs(fighter.posX - target.posX)
                if (distToTarget < fighter.reach * 40f + 60f) {
                    val p = effectiveSwingProgress.coerceIn(0f, 1f)
                    val liftMax = if (isChokeSlam) -140f else if (isSuplex) -90f else if (fighter.activeWrestlingMove == "body_throw") -70f else 0f
                    if (liftMax != 0f) {
                        target.visualOffsetY = if (p < strikeThreshold) {
                            liftMax * (p / strikeThreshold)
                        } else {
                            liftMax * (1f - (p - strikeThreshold) / (1f - strikeThreshold))
                        }
                    }
                }
            }

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
                // Ensure a slammed enemy never sticks mid-air if the move was interrupted
                if (fighter.activeWrestlingMove != null) {
                    target?.visualOffsetY = 0f
                    fighter.activeWrestlingMove = null
                }
            }
        }
        // Pallbearer lock
        if (fighter.pallbearerIndex >= 0) {
            val player = _playerState.value
            if (player != null) {
                fighter.facingRight = player.facingRight
                val offset = when(fighter.pallbearerIndex) {
                    0 -> 45f
                    1 -> 35f
                    2 -> -35f
                    3 -> -45f
                    else -> 0f
                }
                fighter.posX = player.posX + offset
                fighter.animFrame = player.animFrame * 1.5f // Walk in sync with the throne
                
                // Only the gear-bearing front pair fight; the rear pair just carry
                if (fighter.pallbearerIndex < 2 && target != null && !target.isDead) {
                    val dist = kotlin.math.abs(fighter.posX - target.posX)
                    val reachPixels = fighter.reach * 40f + 40f
                    if (dist <= reachPixels && fighter.attackCooldown <= 0 && !fighter.isAttacking) {
                        triggerAttack(fighter)
                    }
                }
                return
            }
        }

        // Decide movement & actions
        if (target != null && !target.isDead && fighter.crumpleDuration <= 0f) {
            val dist = abs(fighter.posX - target.posX)
            val reachPixels = fighter.reach * 40f + 40f // generous hitbox
            val rangeMult = if (!fighter.isPlayer && fighter.level > 5) 0.8f + (fighter.level - 5) * 0.05f else 0.8f
            val optimalDistance = if (fighter.isRanged) reachPixels * rangeMult else reachPixels
            val isShieldWall = !fighter.isPlayer && fighter.shield.id == "shield_tower"

            fighter.facingRight = target.posX > fighter.posX

            if (dist > optimalDistance) {
                // Walk closer
                val direction = if (target.posX > fighter.posX) 1f else -1f
                val moveMult = if (isShieldWall) 0.6f else 1f
                fighter.posX += direction * fighter.moveSpeed * moveMult * dt
                // Desync animations slightly based on maxHp to avoid identical marching
                fighter.animFrame = fighter.animFrame + dt * (9f + (fighter.maxHp % 3f))
            } else if (dist < optimalDistance * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                val retreatSpeed = if (fighter.isRanged) fighter.moveSpeed else (fighter.moveSpeed * 0.45f)
                fighter.posX += direction * retreatSpeed * dt
                fighter.animFrame = fighter.animFrame - dt * (6f + (fighter.maxHp % 3f))
                
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking && !fighter.isLord) {
                    triggerAttack(fighter)
                }
            } else {
                // Wield weapon/Attack!
                // Settle to the nearest sine-zero instead of snapping to 0, so the walk bob lands smoothly
                val piF = Math.PI.toFloat()
                val nearestRest = kotlin.math.round(fighter.animFrame / piF) * piF
                fighter.animFrame += (nearestRest - fighter.animFrame).coerceIn(-8f * dt, 8f * dt)
                if (fighter.attackCooldown <= 0 && !fighter.isAttacking && !fighter.isLord) {
                    triggerAttack(fighter)
                }
            }
            
            // Clamp position to level bounds
            val lw = _uiState.value.levelWidth
            fighter.posX = fighter.posX.coerceIn(30f, lw - 30f)

        }
    }

    private fun triggerAttack(fighter: FighterState) {
        fighter.isAttacking = true
        fighter.swingProgress = 0f
        
        if (fighter.weaponHandle.id == "handle_fists" && fighter.weaponHead.id == "head_bare") {
            if (fighter.missingArm) {
                fighter.activeWrestlingMove = null
            } else {
                val rand = kotlin.random.Random.nextFloat()
                // Default is always the plain punch; wrestling moves are the occasional special.
                fighter.activeWrestlingMove = when {
                    fighter.isDualWielding && rand < 0.25f -> "choke_slam"
                    fighter.isDualWielding && rand < 0.4f -> "body_throw"
                    !fighter.isDualWielding && fighter.brawlerUpgrades.contains("champion_belt") && rand < 0.35f -> "suplex"
                    // One free hand is enough to grab a throat — shield-and-fist builds slam too, just rarer
                    !fighter.isDualWielding && rand < 0.12f -> "choke_slam"
                    !fighter.isDualWielding && rand < 0.2f -> "body_throw"
                    else -> null
                }
            }
        }
        
        var cooldown = fighter.attackSpeedDelay
        if (!fighter.isPlayer && fighter.isRanged && fighter.level > 15) {
            cooldown *= 0.7f // Ranged Escalation: Faster attack speed
        }
        fighter.attackCooldown = cooldown

        // Play melee/ranged swing swoosh sound at start of attack animation
        MedievalAudioSynth.playSound(SoundType.SWOOSH)
    }

    private fun performStrike(attacker: FighterState, defender: FighterState) {
        if (attacker.isRanged) {
            val isDualWielding = attacker.isDualWielding && attacker.shield.id == "shield_none"
            var hitCount = if (isDualWielding) 2 else 1
            
            if (!attacker.isPlayer && attacker.level > 20) {
                hitCount += 1 // Ranged Escalation: Multishot
            }
            
            viewModelScope.launch {
                for (hitIdx in 0 until hitCount) {
                    if (hitIdx > 0) kotlinx.coroutines.delay(160)
                    if (attacker.isDead) return@launch

                    val isPlayer = attacker.isPlayer
                    val dir = if (attacker.facingRight) 1f else -1f
                    var startX = attacker.posX + (dir * 25f)
                    var startY = 230f
                    
                    if (hitIdx == 1) {
                        startX -= (dir * 15f)
                        startY -= 25f
                    }
            
            // Stats based on weapon head
            val isSlingshot = attacker.weaponHead.id == "head_slingshot"
            val isJavelin = attacker.weaponHead.id == "head_javelin"
            var projType = if (isSlingshot) "stone" else if (isJavelin) "javelin" else "arrow"
            
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
            
            if (!attacker.isPlayer && attacker.level > 25) {
                velX *= 1.5f // Ranged Escalation: Projectile speed
                velY *= 1.2f
            }

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

            var projId = "proj_${System.currentTimeMillis()}_${Random.nextInt(100)}"
            if (attacker.id == "hag") {
                projId = "hag_mud_${System.currentTimeMillis()}_${Random.nextInt(100)}"
                finalDmg = 5f
                splash = true
                projType = "rock"
            }

            val proj = Projectile(
                id = projId,
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
                }
            }
        } else {
            // Melee hit
            val reachPixels = attacker.reach * 40f + 40f // generous hitbox
            val isPiercingWeapon = attacker.weaponHead.id in listOf("head_spear", "head_pike", "head_halberd")
            
            // Gather all targets in a line if we are using a piercing weapon
            val targets = if (attacker.isPlayer && isPiercingWeapon) {
                val dir = if (attacker.facingRight) 1f else -1f
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels && 
                    ((dir > 0 && it.posX >= attacker.posX - 30f) || (dir < 0 && it.posX <= attacker.posX + 30f))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer && attacker.weaponHandle.id == "handle_double_ended") {
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels 
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else if (attacker.isPlayer) {
                val dir = if (attacker.facingRight) 1f else -1f
                _enemiesState.value.filter { 
                    !it.isDead && !it.isDying && it.isPlayer != attacker.isPlayer && abs(attacker.posX - it.posX) <= reachPixels && 
                    ((dir > 0 && it.posX >= attacker.posX - 30f) || (dir < 0 && it.posX <= attacker.posX + 30f))
                }.sortedBy { abs(attacker.posX - it.posX) }
            } else {
                listOf(defender)
            }
            
            if (targets.isEmpty() || (!targets.contains(defender) && abs(attacker.posX - defender.posX) > reachPixels)) {
                // Missed!
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                return
            }
            
            // Dual Wield miss chance (higher for dual wield in general)
            if (attacker.isDualWielding && Random.nextFloat() < 0.35f) {
                addPopup("MISS!", defender.posX, 140f, Color.Gray)
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                return
            }
            
            val hitCount = if (attacker.weaponHandle.id == "handle_double_ended") {
                if (attacker.isDualWielding) 4 else 2
            } else {
                1
            }
            val dmgScale = if (hitCount == 4) 0.3f else if (hitCount == 2) 0.6f else 1.0f
            
            // Brawler abilities
            val isBrawler = attacker.weaponHead.id == "head_bare" && attacker.weaponHandle.id == "handle_fists"
            if (isBrawler && targets.isNotEmpty()) {
                val target = targets.first()
                
                if (target.weaponHead.id != "head_bare" && kotlin.random.Random.nextFloat() < 0.05f) {
                    // Steal their weapon!
                    attacker.weaponHead = target.weaponHead
                    attacker.weaponHandle = target.weaponHandle
                    attacker.isDualWielding = false
                    
                    target.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
                    target.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }
                    
                    addPopup("STOLEN!", target.posX, 130f, Color.Yellow)
                    MedievalAudioSynth.playSound(SoundType.CLANG)
                    // We continue into the regular attack loop below to hit them with their own weapon!
                } else if (attacker.activeWrestlingMove == "suplex") {
                    val secondTarget = targets.drop(1).firstOrNull() ?: target
                    applyFlatDamage(60f, target, attacker.isPlayer)
                    applyFlatDamage(60f, secondTarget, attacker.isPlayer)
                    if (!target.isPlayer) target.crumpleDuration = 3.5f
                    if (!secondTarget.isPlayer) secondTarget.crumpleDuration = 3.5f
                    addPopup("SUPLEX!", target.posX, 120f, Color.Red)
                    MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == "body_throw") {
                    // Hurl the grabbed enemy down the line — if he lands on a mate, both go down
                    val dir = if (attacker.facingRight) 1f else -1f
                    applyFlatDamage(45f, target, attacker.isPlayer)
                    if (!target.isPlayer) {
                        target.crumpleDuration = 3f
                        target.posX += dir * 140f
                        target.targetX = target.posX
                    }
                    val second = targets.drop(1).firstOrNull { kotlin.math.abs(it.posX - target.posX) < 60f }
                    if (second != null) {
                        applyFlatDamage(30f, second, attacker.isPlayer)
                        if (!second.isPlayer) second.crumpleDuration = 3f
                        addPopup("BOWLED OVER!", second.posX, 120f, Color.Red)
                    }
                    addPopup("HURLED!", target.posX, 120f, Color.Red)
                    MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    return
                } else if (attacker.activeWrestlingMove == "choke_slam") {
                    applyFlatDamage(attacker.baseDamage * 3.5f, target, attacker.isPlayer)
                    if (!target.isPlayer) target.crumpleDuration = 2.5f
                    addPopup("-CHOKE SLAM-", target.posX, 120f, Color.Red) // Using hyphens so it passes word filter
                    MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    return
                }
            }

            viewModelScope.launch {
                for (hitIdx in 0 until hitCount) {
                    if (hitIdx > 0) {
                        kotlinx.coroutines.delay(160)
                    }

                    if (attacker.isDead) return@launch // Don't hit if we died during the windup!
                    var damageFalloff = 1f

                    for (currTarget in targets) {
                        if (currTarget.isDead || currTarget.isDying) continue

                // Speed Advantage: Capped melee dodge chance
                val meleeDodgeChance = (currTarget.moveSpeed * 0.0015f).coerceIn(0f, 0.25f)
                if (kotlin.random.Random.nextFloat() < meleeDodgeChance) {
                    addPopup("DODGE!", currTarget.posX, 140f, Color.Gray)
                    MedievalAudioSynth.playSound(SoundType.SWOOSH)
                    continue
                }
                
                // Speed Advantage: Interrupt slow enemy attack progress
                if (currTarget.isAttacking && (attacker.moveSpeed > currTarget.moveSpeed * 1.2f || attacker.size < currTarget.size * 0.95f) && !currTarget.isPlayer) {
                    currTarget.isAttacking = false
                    currTarget.swingProgress = 0f
                    addPopup("INTERRUPT!", currTarget.posX, 150f, Color.Gray)
                }

                var blockChance = currTarget.shield.defense / 100f
                if (!currTarget.isPlayer && currTarget.shield.id == "shield_tower") blockChance = 0.8f
                
                val shieldBypass = if (attacker.weaponHead.id == "head_flail" || attacker.weaponHead.id == "head_war_flail" || attacker.weaponHandle.id == "handle_flail_chain") 0.4f else 0f
                blockChance *= (1f - shieldBypass)
                
                val isBlocked = currTarget.shield.id != "shield_none" && Random.nextFloat() < blockChance
                
                if (isBlocked) {
                    // Blocked by shield!
                    MedievalAudioSynth.playSound(SoundType.CLANG)
                    
                    // Breakable Shields logic
                    val armorFactor = (1f - (currTarget.totalArmor / 100f)).coerceIn(0.1f, 1f)
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
                    val blockDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale
                    
                    if (currTarget.shieldHp > 0f) {
                        currTarget.shieldHp -= blockDamage * 0.5f // Shield takes half damage
                        if (currTarget.shieldHp <= 0f) {
                            currTarget.shieldHp = 0f
                            currTarget.shield = GameData.SHIELDS.first { it.id == "shield_none" }
                            MedievalAudioSynth.playSound(SoundType.CRUNCH)
                            addPopup("SHIELD BROKEN!", currTarget.posX, 160f, Color.LightGray)
                            // Add shattering particles
                            val px = currTarget.posX
                            val py = 140f
                            _particlesState.value = _particlesState.value + List(5) {
                                BloodParticle(x = px + Random.nextInt(-10, 10), y = py + Random.nextInt(-10, 10), vx = (Random.nextFloat() * 100f - 50f), vy = -100f - Random.nextFloat() * 50f, color = Color(0xFF6E5536), isSmoke = false)
                            }
                        }
                    }

                    // Still take minimal blunt impact damage
                    val bluntDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                    if (bluntDamage > 5f && kotlin.random.Random.nextBoolean()) MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    applyFlatDamage(bluntDamage, currTarget, attacker.isPlayer)
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
                    
                    var totalDamage = ((slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt) * dmgScale
                    
                    // Cupbearer strength bonus!
                    if (attacker.isPlayer && _uiState.value.unlockedAncillaries.contains(Ancillary.CUPBEARER)) {
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
                    
                    // Brawler Bleeding (Spiked Wraps)
                    if (attacker.brawlerUpgrades.contains("spiked_wraps") && totalDamage > 0f && kotlin.random.Random.nextFloat() < 0.5f) {
                        currTarget.bleedDuration = 4.0f
                        addPopup("+BLEEDING+", currTarget.posX, 120f, Color(0xFFA62B2B))
                    }

                    // Limb loss mechanic! (heavy slash)
                    val canLoseArm = !currTarget.isPlayer || (currTarget.hp / currTarget.maxHp < 0.10f)
                    if (slash > 18f && kotlin.random.Random.nextFloat() < 0.2f && !currTarget.missingArm && canLoseArm) {
                        currTarget.missingArm = true
                        // Disarm off-hand/shield logically
                        if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                            currTarget.isDualWielding = false
                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it == GameData.Shield.NONE }?.let { currTarget.shield = it }
                        }
                        applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)
                        addPopup("-${totalDamage.toInt()}", currTarget.posX, 140f, androidx.compose.ui.graphics.Color.Red)
                        
                        MedievalAudioSynth.playSound(SoundType.THWACK)
                        addPopup("ARM SEVERED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.Red)
                        
                        val px = currTarget.posX
                        val py = 140f
                        _particlesState.value = _particlesState.value + BloodParticle(x = px, y = py, vx = (Random.nextFloat() * 100f - 50f), vy = -200f - Random.nextFloat() * 100f, color = Color(0xFF8B0000), isSmoke = false)
                    }
                    
                    // Crumple mechanic! (heavy blunt) - enemies can't knock the player down, only the reverse
                    if (blunt > 18f && attacker.id != "raven" && kotlin.random.Random.nextFloat() < 0.25f && currTarget.crumpleDuration <= 0f && !currTarget.isPlayer) {
                        currTarget.crumpleDuration = 2.5f
                        MedievalAudioSynth.playSound(SoundType.CRUNCH)
                        addPopup("-CRUMPLED-", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.DarkGray)
                    }

                    // Wardog trip mechanic!
                    if (attacker.id == "wardog" && kotlin.random.Random.nextFloat() < 0.25f && currTarget.crumpleDuration <= 0f && !currTarget.isPlayer) {
                        currTarget.crumpleDuration = 2f
                        MedievalAudioSynth.playSound(SoundType.CRUNCH)
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
                        // Pike handle keeps most of its force as it skewers down the line
                        damageFalloff *= if (attacker.weaponHandle.id == "handle_pike_long") 0.8f else 0.5f
                    }
                }
            }
        }
    }
    }

    private fun applyProjectileDamage(proj: Projectile, defender: FighterState) {
        // Speed Advantage: Ranged deflection based on speed
        val deflectionChance = (defender.moveSpeed * 0.002f).coerceIn(0f, 0.35f)
        if (kotlin.random.Random.nextFloat() < deflectionChance) {
            MedievalAudioSynth.playSound(SoundType.SWOOSH)
            addPopup("DEFLECT!", defender.posX, 120f, Color.Gray)
            return
        }
        
        // Ranged hit calculation
        val isBlocked = defender.shield.id != "shield_none" && Random.nextFloat() < (defender.shield.defense / 110f)

        if (isBlocked) {
            MedievalAudioSynth.playSound(SoundType.CLANG)
            if (proj.type == "arrow" || proj.type == "bolt" || proj.type == "javelin") {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, true, proj.isBallista))
            }
            
            if (defender.shieldHp > 0f) {
                defender.shieldHp -= proj.damage * 0.3f
                if (defender.shieldHp <= 0f) {
                    defender.shieldHp = 0f
                    defender.shield = GameData.SHIELDS.first { it.id == "shield_none" }
                    MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    addPopup("SHIELD BROKEN!", defender.posX, 160f, Color.LightGray)
                    val px = defender.posX
                    val py = 140f
                    _particlesState.value = _particlesState.value + List(5) {
                        BloodParticle(x = px + Random.nextInt(-10, 10), y = py + Random.nextInt(-10, 10), vx = (Random.nextFloat() * 100f - 50f), vy = -100f - Random.nextFloat() * 50f, color = Color(0xFF6E5536), isSmoke = false)
                    }
                }
            }
        } else {
            val armorFactor = (1f - (defender.totalArmor / 100f)).coerceIn(0.15f, 1f)
            val totalDamage = (proj.damage * armorFactor) + (proj.blunt * 0.6f)
            applyFlatDamage(totalDamage, defender, proj.isPlayerOwned)

            if (proj.type == "arrow" || proj.type == "bolt" || proj.type == "javelin") {
                defender.stuckProjectiles.add(StuckProj(proj.type, proj.sizeMultiplier, proj.velocityX, proj.velocityY, false, proj.isBallista))
            }
            // Apply Poison Upgrade
            if (proj.isPoisonous) {
                defender.poisonDuration = 5.0f
                addPopup("+POISONED+", defender.posX, 120f, Color(0xFF2E7D32))
            }
            
            // Hag Mud effect
            if (proj.id.startsWith("hag_mud_")) {
                defender.slowDuration = 3.0f
                defender.poisonDuration = 3.0f
                addPopup("SLIMED!", defender.posX, 140f, Color(0xFF384033))
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
        
        if (defender.isMounted && defender.mountHp > 0f) {
            defender.mountHp -= finalDmgInt
            if (defender.mountHp <= 0f) {
                defender.mountHp = 0f
                defender.isMounted = false
                addPopup("MOUNT SHATTERED!", defender.posX, 130f, Color.Gray)
                MedievalAudioSynth.playSound(SoundType.CRUNCH)
                // Throne collapse: the whole retinue is crushed, the lord fights on bare-handed
                if (defender.isLord) {
                    defender.isLord = false
                    _enemiesState.value.filter { it.pallbearerIndex >= 0 && !it.isDead && !it.isDying }.forEach { bearer ->
                        bearer.pallbearerIndex = -1
                        bearer.isDying = true
                        bearer.animFrame = 0f
                        bearer.deathType = kotlin.random.Random.nextInt(0, 5)
                        bearer.deathTime = System.currentTimeMillis()
                    }
                    addPopup("THE THRONE FALLS!", defender.posX, 110f, Color.Red)
                }
            }
        } else {
            defender.hp = (defender.hp - finalDmgInt).coerceAtLeast(0f)
            val rx = kotlin.random.Random.nextFloat() * 14f - 7f
            val ry = kotlin.random.Random.nextFloat() * 20f - 10f
            defender.bloodDecals.add(Triple(rx, ry, kotlin.random.Random.nextInt(6)))
        }
        
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
            // Killed while already crumpled on the ground → die where they lie (type 99),
            // no standing back up. Otherwise 0..7 ragdolls (5 = decapitation).
            defender.deathType = if (defender.crumpleDuration > 0f) 99 else kotlin.random.Random.nextInt(0, 8)
            defender.deathTime = System.currentTimeMillis()
            MedievalAudioSynth.playSound(SoundType.OUCH)
            val deathShout = if (defender.isPlayer) "VÆ MIHI MORTIS!" else "AARRGGHH!"
            addPopup(deathShout, defender.posX, 130f, Color.DarkGray)
            if (isPlayerSource && !defender.isPlayer) {
                _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1)
            }
        } else if (kotlin.random.Random.nextBoolean()) {
            MedievalAudioSynth.playSound(SoundType.OUCH)
        }
    }

    private fun addPopup(text: String, x: Float, y: Float, color: Color) {
        // Only allow damage numbers or specific numeric text, no words!
        if (text.any { it.isLetter() }) return
        _popupsState.value = _popupsState.value + CombatPopup(text, x, y, 0f, color)
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
            
            // Only a notably rough fight earns a new bandage, and they stop piling up past a handful
            val tookHeavyDamage = state.playerHp < state.playerMaxHp * 0.6f
            val newBandagesCount = if (won && tookHeavyDamage && state.bandagesCount < 4) state.bandagesCount + 1 else state.bandagesCount

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
                val availableAncs = GameData.ANCILLARIES.filter { it !in state.unlockedAncillaries }
                if (availableAncs.isNotEmpty()) {
                    val anc = availableAncs.random()
                    val isObject = anc in listOf(com.example.game.Ancillary.WARHORSE, com.example.game.Ancillary.CHARIOT, com.example.game.Ancillary.STILTS, com.example.game.Ancillary.TROJAN_HORSE)
                    val titlePrefix = if (isObject) "Acquire" else "Rally"
                    val titleSuffix = if (isObject) "" else " the ${anc.role}"
                    pendingChoices.add(LevelUpChoice(
                        id = "follower_${anc.id}",
                        title = "$titlePrefix: ${anc.ancillaryName}$titleSuffix",
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

                val isUnarmed = state.weaponHead.id == "head_bare" && state.weaponHandle.id == "handle_fists"

                // 2. Weapon Attachment or Brawler option
                if (isUnarmed) {
                    val possibleBrawler = listOf(
                        LevelUpChoice("brawler_brass_knuckles", "Brawler: Brass Knuckles", "Reinforce your fists with heavy brass! Massive blunt damage.", "brawler_upgrade", "brass_knuckles"),
                        LevelUpChoice("brawler_spiked_wraps", "Brawler: Spiked Wraps", "Wrap your hands in leather and rusty nails. Causes bleeding!", "brawler_upgrade", "spiked_wraps"),
                        LevelUpChoice("brawler_wrestling_belt", "Brawler: Champion Belt", "Increases grapple strength. Wrestle foes to the ground!", "brawler_upgrade", "champion_belt")
                    )
                    val availableBrawler = possibleBrawler.filter { it.itemId !in state.brawlerUpgrades }
                    if (availableBrawler.isNotEmpty()) {
                        pendingChoices.add(availableBrawler.random())
                    }
                } else if (!state.weaponHead.isRanged) {
                    val attachmentHeads = GameData.WEAPON_HEADS.filter { 
                        it.id !in listOf("head_bare", "head_bow", "head_longbow", "head_slingshot") 
                    }
                    val weaponHead = attachmentHeads.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "attach_${weaponHead.id}",
                        title = "Attach Head: ${weaponHead.itemName}",
                        description = "${weaponHead.description} Attached dynamically to weapon, adding +50% of its base damage!",
                        type = "attachment",
                        itemId = weaponHead.id
                    ))
                }

                // 3. Handle Extension or Layered Armor or Shield Upgrade option
                val rndVal = Random.nextFloat()
                if (rndVal < 0.33f && !isUnarmed) {
                    pendingChoices.add(LevelUpChoice(
                        id = "extension",
                        title = "Haft Upgrade: Handle Extension",
                        description = "Lash an additional 1.5-foot wood shaft extension to your grip. Drastically increases reach (+0.35m) and supports more attachments!",
                        type = "extension",
                        itemId = ""
                    ))
                } else {
                    // Shield upgrade if that slot rolled and one is available; otherwise armor.
                    // (Armor used to be unreachable — it was nested behind "all shield upgrades taken".)
                    val shieldChoices = listOf(
                        LevelUpChoice("shield_oak", "Shield: Oak Reinforcing", "Bolt heavy oak planks onto your shield. Massively boosts shield durability!", "shield_upgrade", "oak_reinforcing"),
                        LevelUpChoice("shield_iron", "Shield: Iron Plating", "Rivet iron sheets across your shield. Indestructible but very heavy.", "shield_upgrade", "iron_plating"),
                        LevelUpChoice("shield_helmet", "Shield: Shield Helmet", "Why wear a helmet on your head when you can strap it to your shield? Excellent durability boost.", "shield_upgrade", "shield_helmet")
                    )
                    val availableShieldUpgrades = shieldChoices.filter { it.itemId !in state.shieldUpgrades }
                    if (rndVal < 0.66f && state.shield.id != "shield_none" && availableShieldUpgrades.isNotEmpty()) {
                        pendingChoices.add(availableShieldUpgrades.random())
                    } else {
                        val baseArmorOptions = GameData.ARMOR_PIECES.filter { it.id != "armor_bare" && it.id !in listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_jester") }
                        val highLevelArmorOptions = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_gauntlets", "armor_boots", "armor_coif") }

                        val armorOptions = mutableListOf<GearItem>()
                        armorOptions.addAll(baseArmorOptions)
                        if (state.level > 10) {
                            armorOptions.addAll(highLevelArmorOptions)
                        }
                        if (state.level > 3) {
                            armorOptions.add(GameData.ARMOR_PIECES.first { it.id == "armor_jester" })
                        }

                        val armorPiece = armorOptions.random()
                        val isComedy = armorPiece.id == "armor_jester"
                        val typeCat = if (isComedy) "comedy" else "armor"
                        val titlePrefix = if (isComedy) "Joke Item" else if (armorPiece.id in listOf("armor_gauntlets", "armor_boots", "armor_coif")) "Equip" else "Layer Armor"
                        pendingChoices.add(LevelUpChoice(
                            id = "armor_${armorPiece.id}",
                            title = "$titlePrefix: ${armorPiece.itemName}",
                            description = if (isComedy) "A joke item! Removes all armor protection but gives a massive score multiplier." else "Add ${armorPiece.itemName} to your loadout, gaining +${armorPiece.defense.toInt()} Defense!",
                            type = typeCat,
                            itemId = armorPiece.id
                        ))
                    }
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
                
                // 5. Brawler Upgrades (removed from here, moved to slot 2)
                
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
                bandagesCount = newBandagesCount,
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
                initialGear.addAll(GameData.ARMOR_PIECES.filter { it.id !in listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_jester") }.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.HEADGEAR_PIECES.filter { it.id != "helm_jester" }.shuffled().take(2).map { it.id })
                
                val rng = kotlin.random.Random.Default
                val size = state.characterSize
                val firstNames = if (size > 1.1f) {
                    listOf("William", "Robert", "Henry", "Richard", "Hugh", "Odo", "Fulk", "Alan", "Amaury", "Drogo", "Tancred", "Bernard", "Reginald", "Herbert", "Arnulf", "Guillaume", "Hugo", "Rollo", "Thorold", "Godfrey", "Taillefer", "Balduin", "Ranulf", "Goliath", "Gros-Jean", "Geoffrey", "Eustace")
                } else if (size < 0.9f) {
                    listOf("Ive", "Ives", "Eudo", "Eudes", "Odo", "Hamo", "Hamon", "Milo", "Milon", "Wido", "Widon", "Pippin", "Leofric", "Giles", "Alan", "Eustace", "Aethelred", "Wimund", "Bodo", "Osbern", "Wulfric", "Little John", "Alberic", "Berengar", "Drogo", "Erfast")
                } else {
                    listOf("Roger", "Walter", "Ralph", "Geoffrey", "Gilbert", "Baldwin", "Humphrey", "Eustace", "Miles", "Guy", "Achard", "Aimery", "Engenulf", "Gerelm", "Goubert", "Ilbert", "Ivon", "Mauger", "Osmund", "Pain", "Serlo", "Turold", "Turstin", "Vital", "Wadard", "Arthur", "Lancelot", "Gawain", "Percival", "Bors", "Gareth", "Tristan", "Bedivere", "Galahad", "Kay", "Odo", "William", "Robert", "Richard", "Hugh")
                }
                
                val weakBynames = listOf("Weak-feet", "Soft-bread", "The Timid", "The Bastard", "The Infirm")
                val firstName = firstNames.random(rng)
                
                val currentLastName = state.playerName.split(" ").drop(1).joinToString(" ")
                val lastName = if (size < 0.9f) {
                    weakBynames.random(rng)
                } else {
                    currentLastName.ifEmpty { "the Unknown" }
                }
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
                    // Preselect hair like gear — always one of the start-screen palette options
                    hairColor = listOf(
                        androidx.compose.ui.graphics.Color(0xFF888888),
                        androidx.compose.ui.graphics.Color(0xFFC08030),
                        androidx.compose.ui.graphics.Color(0xFF5A442E),
                        androidx.compose.ui.graphics.Color(0xFF2C2219)
                    ).random(rng),
                    hairStyle = listOf("short", "long", "bald").random(rng),
                    unlockedGearIds = initialGear,
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    rangedUpgrades = emptyList(),
                    shieldUpgrades = emptyList(),
                    brawlerUpgrades = emptyList(),
                    hasThroneOption = kotlin.random.Random.nextFloat() < 0.2f,
                    isThroneMode = false,
                    unlockedAncillaries = emptySet(),
                    bandagesCount = 0, // bandages are veterancy marks earned within a run, never at level 1
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




    fun toggleThroneMode() {
        _uiState.update { it.copy(isThroneMode = !it.isThroneMode) }
    }

    fun clearSkipBonus() {
        _uiState.update { it.copy(pendingSkipBonus = 0) }
    }

    override fun onCleared() {
        gameLoopJob?.cancel()
        super.onCleared()
    }
}
