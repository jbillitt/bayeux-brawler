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
    val type: ProjectileType,
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

    // Which weather just fired, and when — the renderer draws a brief flourish, then it lapses
    private val _weatherFlash = MutableStateFlow<Pair<DivineWeather, Long>?>(null)
    val weatherFlash: StateFlow<Pair<DivineWeather, Long>?> = _weatherFlash.asStateFlow()

    private var gameLoopJob: Job? = null
    private var pendingReinforcements = 0

    // New particles collect here and flush to the StateFlow once per tick —
    // per-hit list copies were the biggest allocation churn in the loop
    private val particleBuffer = mutableListOf<BloodParticle>()
    // 20kg: chainmail (12) + coif (2) rides fine; scale (16) + gauntlets/boots/coif (5.5) does not
    companion object {
        private const val MAX_PARTICLES = 250
        private const val ARMOR_WEIGHT_LIMIT = 20f
        private const val WEATHER_UNLOCK_LEVEL = 12
        private const val MAX_WEATHERS_HELD = 2

        /** Seconds before a spent weather charge is ready again. The border icons dim against it. */
        const val WEATHER_COOLDOWN = 60f
    }

    /**
     * Call down a held divine weather on the enemy host. No-op if it is still cooling down, if the
     * player never earned it, or if the battle is over.
     */
    fun triggerWeather(id: String) {
        val state = _uiState.value
        val weather = state.divineWeathers.firstOrNull { it.id == id } ?: return
        if (!state.isBattleActive || state.battleWon || state.battleLost) return
        if ((state.weatherCooldowns[id] ?: 0f) > 0f) return

        // Allies (fanatic, hag, the peasant) live in the enemies list under isPlayer=true — spare them
        val foes = _enemiesState.value.filter { !it.isPlayer && !it.isDead && !it.isDying }
        when (weather) {
            DivineWeather.LIGHTNING -> {
                foes.maxByOrNull { it.hp }?.let { biggest ->
                    addPopup("SMITTEN!", biggest.posX, 110f, Color(0xFFF2C14E))
                    engine.applyFlatDamage(150f, biggest, isPlayerSource = true)
                    _screenshake.value = 30f
                }
            }
            DivineWeather.FLOOD -> {
                // The 2-3 furthest downfield get taken by the water, horse and all
                foes.sortedByDescending { it.posX }.take(Random.nextInt(2, 4)).forEach { swept ->
                    swept.isMounted = false
                    swept.mountHp = 0f
                    engine.applyFlatDamage(9999f, swept, isPlayerSource = true)
                    swept.deathType = DeathType.KNOCKED_FLYING
                    swept.velocityX = 900f + Random.nextFloat() * 300f
                }
                addPopup("THE DELUGE!", (_playerState.value?.posX ?: 0f) + 200f, 110f, Color(0xFF3A6EA5))
                _screenshake.value = 25f
            }
            DivineWeather.HAIL -> {
                foes.forEach {
                    it.isCrumpled = true
                    it.crumpleDuration = 2.5f
                }
                addPopup("HAILSTORM!", (_playerState.value?.posX ?: 0f) + 200f, 110f, Color(0xFFDCE6EC))
                _screenshake.value = 18f
            }
            DivineWeather.FROST -> {
                foes.forEach {
                    it.slowDuration = 6f
                    if (Random.nextFloat() < 0.5f) {
                        it.isCrumpled = true
                        it.crumpleDuration = 1.2f
                    }
                }
                addPopup("KILLING FROST!", (_playerState.value?.posX ?: 0f) + 200f, 110f, Color(0xFF8FC1D4))
            }
        }
        _weatherFlash.value = weather to System.currentTimeMillis()
        _uiState.update { it.copy(weatherCooldowns = it.weatherCooldowns + (id to WEATHER_COOLDOWN)) }
    }

    // Combat rules live in CombatEngine; this context is its window into the battle state
    private val engine = CombatEngine(object : BattleContext {
        override val player get() = _playerState.value
        override val enemies get() = _enemiesState.value
        override val levelWidth get() = _uiState.value.levelWidth
        override val unlockedAncillaries get() = _uiState.value.unlockedAncillaries
        override val hasShieldbreaker get() = _uiState.value.hasShieldbreaker
        override val hasArmorPiercing get() = _uiState.value.hasArmorPiercing
        override fun spawnProjectile(p: Projectile) { _projectilesState.value = _projectilesState.value + p }
        override fun sound(type: SoundType) = MedievalAudioSynth.playSound(type)
        override fun popup(text: String, x: Float, y: Float, color: Color) = addPopup(text, x, y, color)
        override fun bloodParticles(x: Float, y: Float, count: Int) = addBloodParticles(x, y, count)
        override fun particle(p: BloodParticle) { particleBuffer.add(p) }
        override fun screenshake(amount: Float) { _screenshake.value = amount }
        override fun enemyKilled() { _uiState.value = _uiState.value.copy(totalKills = _uiState.value.totalKills + 1) }
    })

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
            val newWeathers = if (choice.type == "weather") state.divineWeathers + DivineWeather.values().first { it.id == choice.itemId } else state.divineWeathers
            val newExtensions = if (choice.type == "extension") state.handleExtensionCount + 1 else state.handleExtensionCount
            val newRangedUpgrades = if (choice.type == "ranged_upgrade") state.rangedUpgrades + choice.itemId else state.rangedUpgrades
            val newShieldUpgrades = if (choice.type == "shield_upgrade") state.shieldUpgrades + choice.itemId else state.shieldUpgrades
            val newBrawlerUpgrades = if (choice.type == "brawler_upgrade") state.brawlerUpgrades + choice.itemId else state.brawlerUpgrades
            val newHasSilkenGarments = state.hasSilkenGarments || choice.id == "silken_garments"
            val newHasShieldbreaker = state.hasShieldbreaker || choice.itemId == "counter_shieldbreaker"
            val newHasArmorPiercing = state.hasArmorPiercing || choice.itemId == "counter_armor_piercing"
            
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
                hasSilkenGarments = newHasSilkenGarments,
                divineWeathers = newWeathers,
                hasShieldbreaker = newHasShieldbreaker,
                hasArmorPiercing = newHasArmorPiercing,
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

    fun selectMount(a: Ancillary) {
        if (_uiState.value.isBattleActive) return
        _uiState.update { state ->
            state.copy(activeMount = a)
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
        val totalArmorMass = state.armor.mass + state.headgear.mass + state.extraArmors.sumOf { id -> com.example.game.GameData.ARMOR_PIECES.find { it.id == id }?.mass?.toDouble() ?: 0.0 }.toFloat()
        val currentMount = state.activeMount ?: state.unlockedAncillaries.lastOrNull { it.id.startsWith("anc_mount_") }
        val chariotCollapses = currentMount == com.example.game.Ancillary.CHARIOT && totalArmorMass > ARMOR_WEIGHT_LIMIT && !state.hasSilkenGarments
        val baseHp = 100f + state.totalHpBoost
        // Soften the size-HP penalty and give an extra evasion-HP buff so small builds stay viable
        val totalPlayerMaxHp = baseHp * (0.75f + 0.25f * state.characterSize) * (1f + (1f - state.characterSize).coerceAtLeast(0f) * 0.6f)
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
            isMounted = currentMount == Ancillary.WARHORSE || (currentMount == Ancillary.CHARIOT && !chariotCollapses) || currentMount == Ancillary.STILTS || state.isThroneMode,
            mountHp = if (state.isThroneMode) 100f else if (currentMount == Ancillary.STILTS) 40f else if (currentMount == Ancillary.CHARIOT && !chariotCollapses) 100f else if (currentMount == Ancillary.WARHORSE) 80f else 0f,
            isChariot = currentMount == Ancillary.CHARIOT && !chariotCollapses,
            isStilts = !state.isThroneMode && currentMount == Ancillary.STILTS,
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
            EnemyFactory.randomSaxon(index, state.level)
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

        // Curve counters: phase in with level, and record which ones the player has actually met so
        // the matching "out" card can join the reward pool afterwards.
        val metCounters = mutableSetOf<String>()
        if (state.level >= EnemyFactory.SHIELD_WALL_FROM_LEVEL && Random.nextFloat() < 0.20f) {
            enemies.addAll(EnemyFactory.shieldWallPair(enemiesCount, state.level))
            metCounters.add(EnemyFactory.COUNTER_SHIELD_WALL)
        }
        if (state.level >= EnemyFactory.BRUTE_FROM_LEVEL && Random.nextFloat() < 0.25f) {
            enemies.add(EnemyFactory.armouredBrute(enemiesCount + 2, state.level))
            metCounters.add(EnemyFactory.COUNTER_BRUTE)
        }
        if (state.level >= EnemyFactory.WAR_PRIEST_FROM_LEVEL && Random.nextFloat() < 0.15f) {
            enemies.add(EnemyFactory.warPriest(enemiesCount + 3, state.level))
            metCounters.add(EnemyFactory.COUNTER_WAR_PRIEST)
        }

        if (state.unlockedAncillaries.contains(Ancillary.PLAGUE_PEASANT)) {
            enemies.add(FighterState(
                // Dying already, so he simply runs at the foe and breathes on them until one of them drops
                id = "plague_peasant",
                name = "Wretched Aldwin",
                isPlayer = true,
                maxHp = 15f,
                hp = 15f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 60f,
                targetX = 60f,
                facingRight = true,
                size = 0.9f,
                speedBoost = 0.5f,
                hairColor = androidx.compose.ui.graphics.Color(0xFFC8B98A),
                hairStyle = "short",
                isContagious = true
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
        
        if (chariotCollapses) {
            bgObjects.add(BackgroundObject("broken_chariot", BackgroundObjectType.BROKEN_CHARIOT, 150f, 0f, 150f, 100f, 100f))
            addPopup("THE CHARIOT COLLAPSES!", 150f, 110f, androidx.compose.ui.graphics.Color.Red)
        }

        if (state.level == 1) {
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
                backgroundObjects = bgObjects,
                // Every weather charge is ready when the horns blow
                weatherCooldowns = it.divineWeathers.associate { w -> w.id to 0f },
                seenCounters = it.seenCounters + metCounters
            )
        }

        engine.reset() // drop any follow-up hits queued in a previous battle

        // Play battle start timpani roll
        MedievalAudioSynth.playSound(SoundType.DRUM_ROLL)

        // Launch game loop
        startGameLoop()
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
        // Age-filter survivors, merge this tick's new particles, cap total (drop oldest)
        val liveParticles = particles.filter { it.age < it.maxAge } + particleBuffer
        particleBuffer.clear()
        _particlesState.value = if (liveParticles.size > MAX_PARTICLES) liveParticles.takeLast(MAX_PARTICLES) else liveParticles

        if (_uiState.value.unlockedAncillaries.contains(Ancillary.MONK)) {
            addIncenseParticles(player.posX - (40f * player.size), 190f, count = 2)
        }

        // Weather charges come back over time
        if (_uiState.value.weatherCooldowns.any { it.value > 0f }) {
            _uiState.update { s ->
                s.copy(weatherCooldowns = s.weatherCooldowns.mapValues { (_, cd) -> (cd - dt).coerceAtLeast(0f) })
            }
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
        engine.tick(dt) // run any queued follow-up hits before this tick's new swings
        engine.updateFighter(player, closestEnemy, dt)
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
            engine.updateFighter(enemy, pTarget, dt)
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
            val reinforcement = EnemyFactory.randomSaxon(enemies.size + kotlin.random.Random.nextInt(10000), _uiState.value.level)
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
                                engine.applyFlatDamage(15f * f1.size, f2, f1.isPlayer)
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
                if (proj.type.isArrowLike) {
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
                        engine.applyProjectileDamage(proj, enemy)
                        hit = true
                        break
                    }
                }
            } else {
                // Hit test against player
                if (!player.isDead && abs(proj.posX - player.posX) < 30f && proj.posY in 100f..350f) {
                    engine.applyProjectileDamage(proj, player)
                    hit = true
                }
            }
        }

        // Boundary collision or hit
            if (!hit && proj.posX in -500f..(_uiState.value.levelWidth + 500f) && proj.posY < 350f) {
                remainingProjectiles.add(proj)
            } else if (hit) {
                MedievalAudioSynth.playSound(SoundType.THWACK)
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
                    damage = 12f, pierce = 8f, blunt = 2f, type = ProjectileType.ARROW,
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
                    damage = 25f, pierce = 20f, blunt = 10f, type = ProjectileType.BOLT,
                    sizeMultiplier = 1f, 
                    hasSpikes = false, launchedWeaponId = null,
                    isSplash = false, isPoisonous = false, isBallista = false
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
                damage = 4f, pierce = 2f, blunt = 1f, type = ProjectileType.ROCK,
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

    private fun addPopup(text: String, x: Float, y: Float, color: Color) {
        // Deliberate filter: word popups ("SUPLEX!", "POISON!") are authored throughout combat
        // but muted here — only numeric damage text renders. Delete this guard to enable them all.
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
        particleBuffer.addAll(newParticles)
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
        particleBuffer.addAll(newParticles)
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
                        val totalArmorMass = state.armor.mass + state.headgear.mass + state.extraArmors.sumOf { id -> com.example.game.GameData.ARMOR_PIECES.find { it.id == id }?.mass?.toDouble() ?: 0.0 }.toFloat()
                        if (totalArmorMass > ARMOR_WEIGHT_LIMIT && !state.hasSilkenGarments) {
                            pendingChoices.add(LevelUpChoice(
                                id = "silken_garments",
                                title = "Equip: Silken Garments",
                                description = "Lightens your armour below chariot weight while preserving armour level!",
                                type = "armor",
                                itemId = "silken_garments"
                            ))
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
                
                // 4b. Counter "outs" — the whole of the light guidance: if the player has met a
                // counter and lacks its answer, that answer joins the pool. Nothing is removed and
                // nothing is auto-picked; they can still walk past it.
                if (EnemyFactory.COUNTER_SHIELD_WALL in state.seenCounters && !state.hasShieldbreaker) {
                    pendingChoices.add(LevelUpChoice(
                        id = "counter_shieldbreaker",
                        title = "Shieldbreaker",
                        description = "Your blows splinter shields to kindling. Triple damage to shield hp.",
                        type = "counter",
                        itemId = "counter_shieldbreaker"
                    ))
                }
                if (EnemyFactory.COUNTER_BRUTE in state.seenCounters && !state.hasArmorPiercing) {
                    pendingChoices.add(LevelUpChoice(
                        id = "counter_armor_piercing",
                        title = "Armour-Piercing Stitch",
                        description = "A blessed needle-point edge. A third of your damage ignores armour.",
                        type = "counter",
                        itemId = "counter_armor_piercing"
                    ))
                }

                // 5. Divine weather — late-game, rare, and you may only hold two
                val unheldWeathers = DivineWeather.values().filter { it !in state.divineWeathers }
                if (state.level >= WEATHER_UNLOCK_LEVEL &&
                    state.divineWeathers.size < MAX_WEATHERS_HELD &&
                    unheldWeathers.isNotEmpty() &&
                    Random.nextFloat() < 0.25f
                ) {
                    val weather = unheldWeathers.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "weather_${weather.id}",
                        title = "Divine Favour: ${weather.label}",
                        description = "${weather.description} Call it down from the tapestry border once per minute of battle.",
                        type = "weather",
                        itemId = weather.id
                    ))
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
                // Invariant: head_bare (fists) must always pair with handle_fists — never a hilt.
                val newHead = GameData.WEAPON_HEADS.filter { it.id in initialGear }.random()
                val newHandle = if (newHead.id == "head_bare") {
                    GameData.WEAPON_HANDLES.first { it.id == "handle_fists" }
                } else {
                    GameData.WEAPON_HANDLES.filter { it.id in initialGear && it.id != "handle_fists" }.randomOrNull() ?: GameData.WEAPON_HANDLES[1]
                }
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
                    weaponHead = newHead,
                    weaponHandle = newHandle,
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






