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
    private var incenseTick = 0
    // 20kg: chainmail (12) + coif (2) rides fine; scale (16) + gauntlets/boots/coif (5.5) does not
    companion object {
        /**
         * Followers you may rally more than once, fielding one body per copy: pets AND the on-field
         * retinue (hag, fanatic, greaser, plague peasant) — three Mad Borises is three madmen on the
         * field, not a stat line. Uniques, mounts, the throne and the trojan horse stay out (two
         * Trojan Horses or a second mount is not funny).
         */
        val STACKABLE_ANCILLARIES = setOf(
            Ancillary.WARDOG, Ancillary.RAVEN, Ancillary.LIL_GUY,
            Ancillary.HAG, Ancillary.FANATIC, Ancillary.GREASER, Ancillary.PLAGUE_PEASANT
        )

        // Every live particle is a draw call per frame, so this is a frame-budget number, not a
        // taste one. 120 still reads as a gout of blood; 250 was costing frames on mid devices.
        private const val MAX_PARTICLES = 120
        private const val ARMOR_WEIGHT_LIMIT = 20f
        private const val WEATHER_UNLOCK_LEVEL = 12
        private const val MAX_WEATHERS_HELD = 2
        /** Buildings draw ~300px wide and were spawning on top of each other. */
        private const val MIN_BUILDING_GAP = 340f

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
                // Buffed: forks to the two toughest foes and hits harder.
                foes.sortedByDescending { it.hp }.take(2).forEach { biggest ->
                    engine.applyFlatDamage(190f, biggest, isPlayerSource = true)
                }
                _screenshake.value = 36f
            }
            DivineWeather.FLOOD -> {
                // The 2-3 furthest downfield get taken by the water, horse and all
                foes.sortedByDescending { it.posX }.take(Random.nextInt(3, 6)).forEach { swept ->
                    swept.isMounted = false
                    swept.mountHp = 0f
                    engine.applyFlatDamage(9999f, swept, isPlayerSource = true)
                    swept.deathType = DeathType.KNOCKED_FLYING
                    swept.velocityX = 900f + Random.nextFloat() * 300f
                }
                _screenshake.value = 25f
            }
            DivineWeather.HAIL -> {
                // Buffed: hail now bruises as well as knocks down, and holds them longer.
                foes.forEach {
                    it.isCrumpled = true
                    it.crumpleDuration = 3.5f
                    engine.applyFlatDamage(25f, it, isPlayerSource = true)
                }
                _screenshake.value = 22f
            }
            DivineWeather.FROST -> {
                // Buffed: longer freeze and more of them go down.
                foes.forEach {
                    it.slowDuration = 8f
                    if (Random.nextFloat() < 0.65f) {
                        it.isCrumpled = true
                        it.crumpleDuration = 1.6f
                    }
                }
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
        // Must match the start-screen hair swatches exactly, or the preselected colour highlights no
        // swatch and reads as "nothing selected". Same four as MainActivity + the newRun preselect.
        val hairColors = HAIR_COLORS
        val styles = listOf("short", "long", "bald")
        val startSize = sizes.random()
        val rng = kotlin.random.Random.Default
        _uiState.update { it.copy(
            honorific = randomHonorific(rng),
            givenName = givenNamesFor(startSize).random(rng)
        ) }
        updatePhysical(startSize, hairColors.random(), styles.random())
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
            // Followers stack: duplicates in the list sum their hpBoost/speedBoost. A plain rally
            // occasionally turns up a surprise second body ("twins!"); the Thrice-Blessed card takes
            // one you already have to three.
            val newAncs = when (choice.type) {
                "follower" -> {
                    val anc = GameData.ANCILLARIES.first { it.id == choice.itemId }
                    val twins = Random.nextFloat() < 0.15f
                    state.unlockedAncillaries + List(if (twins) 2 else 1) { anc }
                }
                "follower_multiply" -> {
                    val anc = GameData.ANCILLARIES.first { it.id == choice.itemId }
                    state.unlockedAncillaries + List(2) { anc } // already own one → three total
                }
                else -> state.unlockedAncillaries
            }
            val newWeathers = if (choice.type == "weather") state.divineWeathers + DivineWeather.values().first { it.id == choice.itemId } else state.divineWeathers
            val newExtensions = if (choice.type == "extension") state.handleExtensionCount + 1 else state.handleExtensionCount
            val newRangedUpgrades = if (choice.type == "ranged_upgrade") state.rangedUpgrades + choice.itemId else state.rangedUpgrades
            val newShieldUpgrades = if (choice.type == "shield_upgrade") state.shieldUpgrades + choice.itemId else state.shieldUpgrades
            val newBrawlerUpgrades = if (choice.type == "brawler_upgrade") state.brawlerUpgrades + choice.itemId else state.brawlerUpgrades
            val newHasSilkenGarments = state.hasSilkenGarments || choice.id == "silken_garments"
            val newHasRetinuePanoply = state.hasRetinuePanoply || choice.type == "panoply"
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
                hasRetinuePanoply = newHasRetinuePanoply,
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
        // Picking a mount means dismounting the throne — you cannot ride two things at once
        _uiState.update { it.copy(activeMount = a, isThroneMode = false) }
    }

    /** The throne is a mount too, once you have one. */
    fun selectThrone() {
        if (_uiState.value.isBattleActive) return
        _uiState.update { it.copy(activeMount = null, isThroneMode = true, hasTakenThrone = true) }
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

    /**
     * Norman and English styles of address, as the tapestry's own titulus would give them. Blank is
     * in the pot on purpose — plenty of men in the embroidery are named with no title at all.
     */
    private fun randomHonorific(rng: kotlin.random.Random): String = listOf(
        "", "", "", "", "",
        "Syr", "Sire", "Ser", "Messire", "Seigneur", "Sieur",
        "Lord", "Baron", "Earl", "Count", "Vicomte", "Duke",
        "Thegn", "Ealdorman", "Reeve", "Marshal", "Steward", "Chevalier"
    ).random(rng)

    /** Given names only — a byname is appended separately. Big men and runts are named differently. */
    private fun givenNamesFor(size: Float): List<String> = when {
        size > 1.1f -> listOf("William", "Robert", "Henry", "Richard", "Hugh", "Odo", "Fulk", "Alan", "Amaury", "Drogo", "Tancred", "Bernard", "Reginald", "Herbert", "Arnulf", "Guillaume", "Hugo", "Rollo", "Thorold", "Godfrey", "Taillefer", "Balduin", "Ranulf", "Goliath", "Gros-Jean", "Geoffrey", "Eustace")
        size < 0.9f -> listOf("Ive", "Ives", "Eudo", "Eudes", "Odo", "Hamo", "Hamon", "Milo", "Milon", "Wido", "Widon", "Pippin", "Leofric", "Giles", "Alan", "Eustace", "Aethelred", "Wimund", "Bodo", "Osbern", "Wulfric", "Little John", "Alberic", "Berengar", "Drogo", "Erfast")
        else -> listOf("Roger", "Walter", "Ralph", "Geoffrey", "Gilbert", "Baldwin", "Humphrey", "Eustace", "Miles", "Guy", "Achard", "Aimery", "Engenulf", "Gerelm", "Goubert", "Ilbert", "Ivon", "Mauger", "Osmund", "Pain", "Serlo", "Turold", "Turstin", "Vital", "Wadard", "Arthur", "Lancelot", "Gawain", "Percival", "Bors", "Gareth", "Tristan", "Bedivere", "Galahad", "Kay", "Odo", "William", "Robert", "Richard", "Hugh")
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
                Color(0xFFE8D9A0) -> listOf("the Fair", "Flaxen-head", "the Golden", "Sun-mane").random(rng)
                Color(0xFF8B2500) -> listOf("Rust-pate", "the Foxy", "Ember-head", "the Copper").random(rng)
                else -> listOf("the Brown", "the Muddy", "Earth-born", "the Common").random(rng)
            }
        }
        _uiState.update { state ->
            state.copy(
                characterSize = size,
                hairColor = hairColor,
                hairStyle = hairStyle,
                // Only the byname tracks the hair. The given name and title are his to keep — they
                // used to be re-parsed out of the display string here, and quietly lost.
                byname = lastName
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
            hasSilkenGarments = state.hasSilkenGarments,
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

        // Retinue bodies spawn one per copy (Twins/Thrice-Blessed) — ids get "#i" and everything
        // that cares matches via isKind(). posX jittered per copy or they stand inside each other.
        repeat(state.unlockedAncillaries.count { it == Ancillary.FANATIC }) { i ->
            enemies.add(FighterState(
                id = "fanatic_boris#$i",
                name = "Mad Boris",
                isPlayer = true,
                maxHp = 150f,
                hp = 150f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_axe" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it == GameData.Shield.NONE },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 80f + i * 30f,
                targetX = 80f + i * 30f,
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

        repeat(state.unlockedAncillaries.count { it == Ancillary.PLAGUE_PEASANT }) { i ->
            enemies.add(FighterState(
                // Dying already, so he simply runs at the foe and breathes on them until one of them drops
                id = "plague_peasant#$i",
                name = "Wretched Aldwin",
                isPlayer = true,
                maxHp = 15f,
                hp = 15f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 60f + i * 24f,
                targetX = 60f + i * 24f,
                facingRight = true,
                size = 0.9f,
                speedBoost = 0.5f,
                hairColor = androidx.compose.ui.graphics.Color(0xFFC8B98A),
                hairStyle = "short",
                isContagious = true
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.GREASER }) { i ->
            enemies.add(FighterState(
                // Same trick as the hag: head_slingshot marks him isRanged, so the AI holds the
                // backline and lobs instead of charging in. His pots trip rather than wound.
                id = "greaser#$i", name = "Slippery Sam", isPlayer = true, maxHp = 35f, hp = 35f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 40f + i * 22f, targetX = 40f + i * 22f, facingRight = true, size = 0.85f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF6B4A1F), hairStyle = "short", isDualWielding = false
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.HAG }) { i ->
            enemies.add(FighterState(
                // head_slingshot makes her isRanged, so the AI kites at range and lobs mud instead of rushing to melee
                id = "hag#$i", name = "Local Hag", isPlayer = true, maxHp = 40f, hp = 40f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 20f + i * 20f, targetX = 20f + i * 20f, facingRight = true, size = 0.8f, hairColor = androidx.compose.ui.graphics.Color(0xFFAAAAAA), hairStyle = "long", isDualWielding = false
            ))
        }

        if (state.unlockedAncillaries.contains(Ancillary.TROJAN_HORSE)) {
            enemies.add(FighterState(
                // 200hp died to the enemy line long before it mattered; it exists to soak.
                id = "trojan_horse", name = "Trojan Horse", isPlayer = true, maxHp = 450f, hp = 450f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 180f, targetX = 180f, facingRight = true, size = 1.8f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = false
            ))
        }
        
        // Pets stack: rally Buster twice and you get two dogs. Ids stay unique ("wardog#0") and
        // everything that cares asks isKind("wardog"), so each one still renders as a dog and bites.
        // posX is jittered per copy or the pack spawns exactly on top of itself.
        repeat(state.unlockedAncillaries.count { it == Ancillary.WARDOG }) { i ->
            enemies.add(FighterState(
                id = "wardog#$i", name = "Buster", isPlayer = true, maxHp = 75f, hp = 75f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 70f + i * 26f, targetX = 70f + i * 26f, facingRight = true, size = 1.1f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = true,
                speedBoost = 1.0f
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.RAVEN }) { i ->
            enemies.add(FighterState(
                id = "raven#$i", name = "Munin", isPlayer = true, maxHp = 20f, hp = 20f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 50f + i * 18f, targetX = 50f + i * 18f, facingRight = true, size = 0.35f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = true,
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

        // Keep the retinue alive deeper into a run: on-field allies gain a little HP per level.
        run {
            val allyHpBonus = (state.level - 1) * 6f
            if (allyHpBonus > 0f) enemies.filter { it.isPlayer }.forEach { ally ->
                ally.maxHp += allyHpBonus
                ally.hp += allyHpBonus
            }
        }

        // Retinue panoply: kit the whole retinue out in one pass, after every spawn block has run,
        // rather than threading gear through each one. Beasts and the decoy can't wear mail, and
        // the pallbearers already inherit the lord's kit.
        if (state.hasRetinuePanoply) {
            enemies.filter {
                it.isPlayer && !it.isKind("wardog") && !it.isKind("raven") &&
                    it.id != "trojan_horse" && !it.id.startsWith("pallbearer_")
            }.forEach { ally ->
                ally.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" }
                ally.armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" }
                ally.extraArmors = ally.extraArmors + GameData.ARMOR_PIECES.first { it.id == "armor_gauntlets" }
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

            // Code-drawn buildings (procedural, or with seeded overlays) plus every JSON asset in
            // assets/art/ that declares a spawn block. A new .json file therefore needs no code here.
            val codeBuildings = listOf(
                BackgroundObjectType.BUILDING_BOSHAM,
                BackgroundObjectType.BUILDING_BAYEUX,
                BackgroundObjectType.TOWER_SPIRAL
            )
            val assetBuildings = VectorAsset.spawnable().filter { state.level >= (it.spawn?.minLevel ?: 2) }

            // Keep them apart: a random x per building had them growing out of each other
            val placedX = mutableListOf<Float>()
            fun placeX(): Float {
                repeat(24) {
                    val candidate = 300f + Random.nextFloat() * (levelWidth - 600f)
                    if (placedX.none { abs(it - candidate) < MIN_BUILDING_GAP }) {
                        placedX.add(candidate)
                        return candidate
                    }
                }
                // Crowded level: fall back to evenly spaced rather than stacked
                val fallback = 300f + placedX.size * MIN_BUILDING_GAP
                placedX.add(fallback)
                return fallback.coerceAtMost(levelWidth - 300f)
            }

            for (i in 0 until numBuildings) {
                val bx = placeX()
                if (state.level >= 5 && i == 0) {
                    val fort = listOf(
                        BackgroundObjectType.FORT_DINAN, BackgroundObjectType.FORT_PALACE,
                        BackgroundObjectType.FORT_TOWER, BackgroundObjectType.FORT_MOTTE
                    ).random()
                    bgObjects.add(BackgroundObject("bg_$i", fort, bx, 0f, 300f, 1000f, 1000f))
                    continue
                }
                // Weighted draw across both pools
                val assetPicks = assetBuildings.flatMap { a -> List(a.spawn?.weight ?: 1) { a } }
                val pick = Random.nextInt(codeBuildings.size + assetPicks.size)
                if (pick < codeBuildings.size) {
                    bgObjects.add(BackgroundObject("bg_$i", codeBuildings[pick], bx, 0f, 300f, 300f, 300f))
                } else {
                    val asset = assetPicks[pick - codeBuildings.size]
                    val hp = asset.spawn?.hp ?: 300f
                    bgObjects.add(
                        BackgroundObject(
                            id = "bg_$i",
                            type = BackgroundObjectType.VECTOR,
                            posX = bx,
                            width = 300f,
                            hp = hp,
                            maxHp = hp,
                            artId = asset.id
                        )
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                isBattleActive = true,
                battleWon = false,
                battleLost = false,
                // Starting a battle bare-fisted flips the music to the brawl (speed-metal) variant
                // for the rest of the run. Throne mode doesn't count — the lord isn't punching.
                brawlMode = it.brawlMode || (!it.isThroneMode && it.weaponHead.id == "head_bare"),
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

        // One puff every 4th tick, not two every tick: at 30fps the old rate spawned 60 smoke
        // particles a second, which pinned the particle cap on its own and starved out the blood.
        incenseTick = (incenseTick + 1) % 4
        if (incenseTick == 0 && _uiState.value.unlockedAncillaries.contains(Ancillary.MONK)) {
            addIncenseParticles(player.posX - (40f * player.size), 190f, count = 1)
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
            // Buildings are only *cover* when a living target is actually sheltering just behind
            // them (further along the shot's path than the building). Otherwise the shot flies past,
            // so a background house no longer eats your arrows when nobody is hiding behind it.
            // Ship is pure background scenery — it never blocks.
            val coverTargets = if (proj.isPlayerOwned) livingEnemies else listOfNotNull(player.takeIf { !it.isDead })
            val bgHit = bgObjects.firstOrNull { bg ->
                !bg.isDestroyed && bg.type != BackgroundObjectType.SHIP &&
                proj.posX in (bg.posX - 100f)..(bg.posX + 100f) && proj.posY in 100f..350f &&
                coverTargets.any { t ->
                    val behindBuilding = if (proj.velocityX >= 0f) t.posX > bg.posX else t.posX < bg.posX
                    behindBuilding && abs(t.posX - bg.posX) < 130f
                }
            }
            if (bgHit != null) {
                hit = true
                bgHit.hp -= proj.damage
                if (proj.type.isArrowLike) {
                    // Capped: the renderer draws one arrow per count, so an uncapped counter grew
                    // the per-building draw cost for the whole battle. 20 a side already reads as
                    // a pincushion.
                    if (proj.velocityX > 0) bgHit.stuckArrowsFromLeft = (bgHit.stuckArrowsFromLeft + 1).coerceAtMost(20)
                    else bgHit.stuckArrowsFromRight = (bgHit.stuckArrowsFromRight + 1).coerceAtMost(20)
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
                // Hit test against the player AND his on-field ancillaries (hag, fanatic, peasant —
                // isPlayer=true bodies in the enemies list). Whoever is nearer the incoming shot soaks
                // it, so an ancillary standing in front of you takes the arrow meant for you.
                val friendly = (listOf(player) + enemies.filter { it.isPlayer && !it.isDead && !it.isDying })
                    .filter { !it.isDead && abs(proj.posX - it.posX) < 30f }
                    .minByOrNull { abs(proj.posX - it.posX) }
                if (friendly != null && proj.posY in 100f..350f) {
                    engine.applyProjectileDamage(proj, friendly)
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
            // Must match the entourage TapestryRenderer.drawAncillaries actually draws, or the arrows
            // fly out of thin air — unlocking a mount shifted the index and Long Shanks "shot arrows".
            val sortedAncs = _uiState.value.unlockedAncillaries
                .filter {
                    !it.id.startsWith("anc_mount_") &&
                        it !in listOf(Ancillary.WARDOG, Ancillary.RAVEN, Ancillary.FANATIC, Ancillary.HAG,
                            Ancillary.TROJAN_HORSE, Ancillary.PLAGUE_PEASANT, Ancillary.GREASER)
                }
                .sortedBy { it.name }
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
        if (enemies.any { it.isKind("wardog") && !it.isDead && !it.isDying } && Random.nextFloat() < dt * 0.2f) {
            MedievalAudioSynth.playDogBark()
        }

        // Old Maud cackles now and then (assets/hag; silent until clips are added)
        if (enemies.any { it.isKind("hag") && !it.isDead && !it.isDying } && Random.nextFloat() < dt * 0.15f) {
            MedievalAudioSynth.playHagCackle()
        }

        // One throw rate per Lil Guy on your back — three of them sling three times as often.
        val lilGuyCount = _uiState.value.unlockedAncillaries.count { it == Ancillary.LIL_GUY }
        if (lilGuyCount > 0 && !player.isDead && Random.nextFloat() < dt * 0.7f * lilGuyCount) {
            val dir = if (player.facingRight) 1f else -1f
            // Spawn ahead of the carrier's leading edge (scaled by body size). Player-owned bolts
            // already spare the player and allies (livingEnemies excludes both), but the old
            // posX-15 origin materialised the shot *inside* a big "absolute unit", so it struck an
            // enemy pressed against the player and the enemy's blood rendered on the carrier —
            // reading as friendly fire. Launch it past the body so it clearly flies at the foe.
            val spawnX = player.posX + dir * (35f + 30f * player.size)
            remainingProjectiles.add(Projectile(
                id = "lilguy_${System.currentTimeMillis()}_${Random.nextInt(100)}",
                isPlayerOwned = true, posX = spawnX, posY = 150f,
                velocityX = dir * (300f + Random.nextFloat() * 80f), velocityY = -25f,
                // 4/2/1 was effectively nothing once armour, the deflect roll and shield blocks ate
                // it — "he didn't do anything" (Jesse). Chip damage now, still well under the archer.
                damage = 14f, pierce = 8f, blunt = 4f, type = ProjectileType.ROCK,
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

                // 1a. Late-run "triple a follower" card. Level 8+ (a floor, not an exact match) and
                // only once you actually have someone to triple. Duplicates already stack their
                // hpBoost/speedBoost (SimulationModels totalHpBoost/totalSpeedBoost sum the list).
                if (state.level >= 8 && state.unlockedAncillaries.isNotEmpty() && Random.nextFloat() < 0.5f) {
                    val lucky = state.unlockedAncillaries.random()
                    pendingChoices.add(LevelUpChoice(
                        id = "triple_${lucky.id}",
                        title = "Thrice-Blessed: ${lucky.ancillaryName}",
                        description = if (lucky in STACKABLE_ANCILLARIES)
                            "Some say the Almighty works in threes. THREE of ${lucky.ancillaryName} take the field."
                        else
                            "Some say the Almighty works in threes. ${lucky.ancillaryName}'s effect is TRIPLED (Max HP +${(lucky.hpBoost * 2).toInt()}, speed +${(lucky.speedBoost * 200).toInt()}%).",
                        type = "follower_multiply",
                        itemId = lucky.id
                    ))
                }

                // 1b. Retinue panoply: arm the whole retinue. Only worth offering once you have
                // bodies on the field to equip, and only once.
                val panoplyWorthy = state.unlockedAncillaries.any {
                    it in listOf(Ancillary.HAG, Ancillary.FANATIC, Ancillary.GREASER, Ancillary.PLAGUE_PEASANT)
                }
                if (!state.hasRetinuePanoply && panoplyWorthy && state.level >= 5) {
                    pendingChoices.add(LevelUpChoice(
                        id = "retinue_panoply",
                        title = "Panoply: Arm the Retinue",
                        description = "Spangenhelms, mail and iron gauntlets for every follower who walks the field. They kit up exactly as you do — and live a good deal longer for it.",
                        type = "panoply",
                        itemId = "retinue_panoply"
                    ))
                }

                // 1. Follower option. Stackable pets stay in the pool even once owned, so you can
                // keep rallying dogs/ravens and field a whole pack; everyone else dedups as before.
                val availableAncs = GameData.ANCILLARIES.filter {
                    it !in state.unlockedAncillaries || it in STACKABLE_ANCILLARIES
                }
                // Two distinct follower offers instead of one — the extra card per battle. Shuffle
                // and take 2 so they never duplicate each other.
                val followerOffers = availableAncs.shuffled().take(2)
                if (followerOffers.isNotEmpty()) {
                    followerOffers.forEach { anc ->
                        val isObject = anc in listOf(com.example.game.Ancillary.WARHORSE, com.example.game.Ancillary.CHARIOT, com.example.game.Ancillary.STILTS, com.example.game.Ancillary.TROJAN_HORSE)
                        val owned = state.unlockedAncillaries.count { it == anc }
                        val titlePrefix = if (isObject) "Acquire" else if (owned > 0) "Another" else "Rally"
                        val titleSuffix = if (isObject) "" else " the ${anc.role}"
                        val packNote = if (owned > 0) " You already have $owned — they stack." else ""
                        pendingChoices.add(LevelUpChoice(
                            id = "follower_${anc.id}",
                            title = "$titlePrefix: ${anc.ancillaryName}$titleSuffix",
                            description = "${anc.description} (Entourage follower: Max HP +${anc.hpBoost.toInt()}, speed +${(anc.speedBoost * 100).toInt()}%)$packNote",
                            type = "follower",
                            itemId = anc.id
                        ))
                    }
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
                // No haft extension on ranged weapons — a longer shaft does nothing for a bow/sling.
                if (rndVal < 0.33f && !isUnarmed && !state.weaponHead.isRanged) {
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
                                description = if (isComedy) "A joke item! Removes all armor protection but gives a massive score multiplier." else "Add ${armorPiece.itemName} to your loadout, gaining +${armorPiece.defense.toInt()} Armor!",
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

        // The run's score is NOT cleared here: the defeat card and its shared tapestry are still
        // showing it. It resets in dismissBattleResult, when the next run actually begins.
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
                val weakBynames = listOf("Weak-feet", "Soft-bread", "The Timid", "The Bastard", "The Infirm")
                val firstName = givenNamesFor(size).random(rng)
                
                // A runt is mocked for it; anyone else keeps the byname he earned.
                val lastName = if (size < 0.9f) {
                    weakBynames.random(rng)
                } else {
                    state.byname.ifEmpty { "the Unknown" }
                }

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
                    score = 0, // a new man starts with no glory (highscore survives)
                    honorific = randomHonorific(rng),
                    givenName = firstName,
                    byname = lastName,
                    faceNoseShape = (0..3).random(rng),
                    faceBiteShape = (0..3).random(rng),
                    faceForehead = (0..2).random(rng),
                    faceMustache = (0..3).random(rng),
                    // Preselect hair like gear — always one of the start-screen palette options
                    hairColor = HAIR_COLORS.random(rng),
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
                    hasTakenThrone = false,
                    unlockedAncillaries = emptyList(),
                    // A new man starts with nothing. activeMount was surviving the reset, so the
                    // next run began already riding the last one's chariot.
                    activeMount = null,
                    isDualWielding = false,
                    hasSilkenGarments = false,
                    // Same leak class as activeMount and the music moods: without this the next
                    // run's retinue spawns pre-armoured and the panoply card never reappears.
                    hasRetinuePanoply = false,
                    divineWeathers = emptyList(),
                    weatherCooldowns = emptyMap(),
                    hasShieldbreaker = false,
                    hasArmorPiercing = false,
                    seenCounters = emptySet(),
                    // Music state is per-run: moods were surviving the reset and stacking across
                    // runs (uncapped tempo, key drift) until every track came out discordant.
                    appliedMusicMoods = emptyList(),
                    showMusicDecision = false,
                    pendingMusicOptions = emptyList(),
                    brawlMode = false,
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
        _uiState.update {
            val taking = !it.isThroneMode
            it.copy(isThroneMode = taking, hasTakenThrone = it.hasTakenThrone || taking)
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






