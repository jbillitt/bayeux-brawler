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
    val isIgniting: Boolean = false,
    val gravityMult: Float = 1f,
    val sourceFighterId: FighterId? = null,
    /**
     * Ground level the shot was loosed from (0 on the flat, down to -150 at a hill crest).
     * Fighters have no real y — only posX — so a missile's height is only ever meaningful
     * relative to the ground it left. Tested absolutely, an archer on a crest loosed his arrows
     * at y≈80 and they spent the whole flight above the 100..350 body window: on a hill, nothing
     * ranged hit anything, uphill or down.
     */
    val launchLiftY: Float = 0f,
    /** Plague-tipped: lays rotting sickness on whoever it strikes, stacking with existing rot. */
    val isPlaguing: Boolean = false,
    /**
     * Share of the target's armour this missile simply ignores, 0..1. A bodkin point is a spike
     * meant for mail; the reward cards promised exactly this and nothing implemented it, because
     * a missile's `pierce` stat is never read when damage is worked out.
     */
    val armorPierceFraction: Float = 0f,
    /** Fragments this missile bursts into when it strikes. 0 for an ordinary shaft. */
    val clusterCount: Int = 0,
    /**
     * Whether this shot sails straight past a building instead of thudding into it. Rolled once,
     * here, rather than per frame — a shot crossing the cover band gets many collision tests, and
     * re-rolling each one would block almost everything however low the chance.
     *
     * The player's missiles mostly ignore cover: a ranged build was spending its whole battle
     * feeding arrows to a barn. Enemy fire is blocked exactly as before.
     */
    val ignoresCover: Boolean = isPlayerOwned && Random.nextFloat() < PLAYER_COVER_PASS_CHANCE
)

/** How often a player-loosed missile flies past a building that would otherwise stop it. */
const val PLAYER_COVER_PASS_CHANCE = 0.85f

/** The body window a missile has to be inside to strike, measured from where it was loosed. */
internal fun Projectile.atBodyHeight(): Boolean = (posY - launchLiftY) in 100f..350f

internal fun createLilGuyDart(
    player: FighterState,
    nowMillis: Long = System.currentTimeMillis(),
    random: Random = Random.Default
): Projectile {
    val direction = if (player.facingRight) 1f else -1f
    return Projectile(
        id = "lilguy_${nowMillis}_${random.nextInt(100)}",
        isPlayerOwned = true,
        posX = player.posX + direction * (35f + 30f * player.size),
        posY = 150f + player.terrainLiftY, // riding the player's back, so his hill lift applies
        launchLiftY = player.terrainLiftY,
        velocityX = direction * (300f + random.nextFloat() * 80f),
        velocityY = -25f,
        damage = 14f,
        pierce = 8f,
        blunt = 4f,
        type = ProjectileType.DART,
        sizeMultiplier = 0.5f,
        // Lil Guy has no FighterState — he is drawn as an ancillary on the player's back — so his
        // darts have no shooter to trace. This field held "anc_lil_guy", an ancillary id, which
        // matched no fighter and so always resolved to null anyway.
        sourceFighterId = null
    )
}

/** One throw every this many seconds, per Lil Guy on your back. Steady, not a coin flip. */
internal const val LIL_GUY_THROW_SECONDS = 1.4f

internal fun rollFollowerCopies(random: Random = Random.Default): Int =
    if (random.nextFloat() < 0.06f) 2 else 1   // was 0.15 — twins turned up most rounds

internal fun pickTripleCandidate(
    followers: List<Ancillary>,
    consumedIds: Set<String>,
    random: Random = Random.Default
): Ancillary? {
    // Mounts are excluded outright: you can only ever ride one, so a "Thrice-Blessed" mount is
    // three cards' worth of nothing. Guarded here rather than at the call site — this is the only
    // route into the follower_multiply branch.
    val candidates = followers.distinctBy { it.id }
        .filter { it.id !in consumedIds && it.id !in MOUNT_ANCILLARY_IDS }
    if (candidates.isEmpty()) return null
    val weights = candidates.map { candidate ->
        1 + 2 * (followers.count { it.id == candidate.id } - 1)
    }
    var roll = random.nextInt(weights.sum())
    for (index in candidates.indices) {
        roll -= weights[index]
        if (roll < 0) return candidates[index]
    }
    return candidates.last()
}

internal fun shieldHpFor(shield: GameData.Shield, upgrades: List<String>): Float =
    shield.defense * 2f +
        (if ("oak_reinforcing" in upgrades) 50f else 0f) +
        (if ("iron_plating" in upgrades) 100f else 0f) +
        (if ("shield_helmet" in upgrades) 40f else 0f)

internal class TickScheduler(val periodMs: Long = 33L) {
    private var deadlineMs: Long? = null

    init {
        require(periodMs > 0L) { "Tick period must be positive" }
    }

    fun nextDelay(nowMs: Long): Long {
        val nextDeadline = (deadlineMs ?: nowMs) + periodMs
        if (nextDeadline <= nowMs) {
            deadlineMs = nowMs
            return 0L
        }
        deadlineMs = nextDeadline
        return nextDeadline - nowMs
    }
}

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

    // Blood thrown onto the linen itself when a whole knot of men goes down together. Purely a
    // flourish — it fades out and touches nothing in the simulation.
    private val _tapestrySplats = MutableStateFlow<List<TapestrySplat>>(emptyList())
    val tapestrySplats: StateFlow<List<TapestrySplat>> = _tapestrySplats.asStateFlow()

    private var gameLoopJob: Job? = null
    private var pendingReinforcements = 0
    private var battleContentRandom = Random(0)

    // Multikill tracking for the splatter. Kept here rather than in CombatEngine so the flourish
    // costs the combat rules nothing and needs no new BattleContext hook.
    private var lastLivingEnemyCount = 0
    private var multiKillCount = 0
    private var multiKillWindow = 0f
    /** Size of the host when the horn blew — the gate on whether this battle can splash at all. */
    private var battleHostSize = 0

    // Softlock watchdog: seconds since ANY hp (fighters, mounts, shields, gate) last changed.
    // If nothing has been hurt for a while mid-battle, something is stuck — see updateSimulation.
    private var stallSeconds = 0f
    private var lastVitalitySignature = Float.NaN

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
            Ancillary.HAG, Ancillary.FANATIC, Ancillary.GREASER, Ancillary.PLAGUE_PEASANT,
            Ancillary.FIREBRAND, Ancillary.BEEKEEPER,
            // The diggers and the skirmisher stack like the rest — twins and trios of them are
            // exactly the sort of nonsense the reward screen already knows how to offer.
            Ancillary.SAPPER, Ancillary.MOLEMAN, Ancillary.TINY_TERRENCE
        )

        // Every live particle is a draw call per frame, so this is a frame-budget number, not a
        // taste one. 120 still reads as a gout of blood; 250 was costing frames on mid devices.
        /**
         * The gear a run may draw on: this run's random roll, plus everything ever earned. Reads the
         * cached profile so it never suspends on the game loop.
         */
        fun poolWithUnlocks(roll: Set<String>): Set<String> =
            roll + GameProfile.cached.unlockedItemIds.filterNot {
                it in GameData.UNLOCKABLE_HANDLE_IDS || it in GameData.STRANGE_HEAD_IDS
            }

        /**
         * Heads a run may roll from. The Strange Relics — the eel, the cheese, the goose, the
         * thighbone — are meant to be rare rewards, so handing them over permanently the moment
         * one is earned (which is what poolWithUnlocks did) put a cheese wheel in the opening
         * loadout of every subsequent run. An earned relic instead turns up in the opening roll
         * just occasionally; the rest of the time it is still won as a reward.
         */
        fun headRollPool(random: kotlin.random.Random = kotlin.random.Random.Default): List<GameData.WeaponHead> {
            val earned = GameProfile.cached.unlockedItemIds
            // head_bare is granted outright; letting it win a rolled slot meant a run could open
            // with one actual weapon head to choose from.
            val base = GameData.WEAPON_HEADS.filter {
                it.id !in GameData.STRANGE_HEAD_IDS && it.id != "head_bare"
            }
            val relics = GameData.WEAPON_HEADS.filter {
                it.id in GameData.STRANGE_HEAD_IDS && it.id in earned
            }
            return if (relics.isNotEmpty() && random.nextFloat() < 0.07f) base + relics.random(random)
            else base
        }

        /**
         * Handles a run may roll from: the sixteen base hafts, plus every unlockable the profile
         * has earned. Earned handles join the *pool* rather than being handed over outright —
         * dumping all seven straight into unlockedGearIds put nine buttons in a single-row picker
         * and mangled it. You still only ever field the two or three this run rolled.
         */
        fun handleRollPool(): List<GameData.WeaponHandle> {
            val earned = GameProfile.cached.unlockedItemIds
            return GameData.WEAPON_HANDLES.filter {
                // Bare wrists are granted outright and are not a haft you would choose, so they
                // must not consume one of the rolled slots — rolling it left a single button.
                it.id != "handle_fists" &&
                    (it.id !in GameData.UNLOCKABLE_HANDLE_IDS || it.id in earned)
            }
        }

        /** Never fewer than this many real hafts and heads to pick between before a match. */
        const val MIN_GEAR_CHOICES = 2

        /**
         * Earned mounts are saved as Ancillary ids, but the mount picklist and the stat wiring both
         * read `unlockedAncillaries` — so they have to be folded in there too, or a mount can be won
         * and never appear. Runs reset that list, which is exactly why this is applied on reset.
         */
        fun ancillariesWithUnlocks(
            current: List<Ancillary>,
            random: kotlin.random.Random = kotlin.random.Random.Default
        ): List<Ancillary> {
            val earned = GameProfile.cached.unlockedItemIds.mapNotNull { id ->
                Ancillary.values().firstOrNull { it.id == id }
            }
            val offered = earned.filter { anc ->
                // Earning a mount means it can TURN UP, not that you own it in perpetuity. The
                // general rule for every unlock: it joins the pool a run may draw from, it is not
                // handed over every time. Most runs should begin on foot.
                if (anc.id in MOUNT_ANCILLARY_IDS) random.nextFloat() < 0.25f else true
            }
            return current + offered.filter { it !in current }
        }

        /**
         * The rewarded ad's payout, as pure data: two extra cards added to the level-up pool. Kept a
         * pure function of state so the reward is unit-testable — the SDK's earned callback only
         * calls this. Never grants a mechanic the player could not otherwise get, only more choice.
         */
        fun rewardChoices(state: BattleSimState, random: kotlin.random.Random = kotlin.random.Random.Default): List<LevelUpChoice> {
            val out = mutableListOf<LevelUpChoice>()

            // An extra attachment, drawn from the same pool the ordinary card uses.
            val head = GameData.WEAPON_HEADS.filter {
                it.id !in listOf("head_bare", "head_bow", "head_longbow", "head_slingshot") &&
                    it.id !in GameData.STRANGE_HEAD_IDS &&
                    (it.id !in GameData.RARE_HEAD_IDS || random.nextFloat() < 0.17f) &&
                    it.id !in state.extraAttachments
            }.randomOrNull(random)
            if (head != null) {
                out.add(LevelUpChoice(
                    id = "attach_${head.id}",
                    title = head.itemName,
                    description = "${head.description} Attached dynamically to weapon, adding +50% of its base damage!",
                    type = "attachment",
                    itemId = head.id
                ))
            }

            // A wider armour selection: one layer the player is not already wearing.
            val layer = listOf("armor_gauntlets", "armor_boots", "armor_coif", "armor_greaves", "armor_spaulders")
                .filter { it !in state.extraArmors }
                .mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } }
                .randomOrNull(random)
            if (layer != null) {
                out.add(LevelUpChoice(
                    id = "armor_${layer.id}",
                    title = layer.itemName,
                    description = "${layer.description} Worn over your existing armour.",
                    type = "armor",
                    itemId = layer.id
                ))
            }
            return out
        }

        /**
         * Grant a milestone if it has not been granted before. Returns true only on the first award,
         * so the caller knows whether to shout about it.
         */
        suspend fun awardMilestone(m: Milestone): Boolean {
            if (m.id in GameProfile.cached.clearedMilestones) return false
            GameProfile.grant(m.grants, m.id)
            return true
        }

        /**
         * Kit one ally out in the retinue panoply. Shared by the start-of-battle pass and the
         * Trojan Horse's spearmen, who spawn long after that pass has run.
         */
        fun applyRetinuePanoply(fighter: FighterState) {
            fighter.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" }
            fighter.armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" }
            fighter.extraArmors = fighter.extraArmors + GameData.ARMOR_PIECES.first { it.id == "armor_gauntlets" }
        }

        private const val MAX_PARTICLES = 120
        private const val ARMOR_WEIGHT_LIMIT = 20f
        private const val WEATHER_UNLOCK_LEVEL = 12
        private const val MAX_WEATHERS_HELD = 2
        /** Buildings draw ~300px wide and were spawning on top of each other. */
        private const val MIN_BUILDING_GAP = 340f
        /** Extra clearance between two obstacle edges (added to their half-widths). */
        private const val BUILDING_MARGIN = 40f

        /** Seconds before a spent weather charge is ready again. The border icons dim against it. */
        const val WEATHER_COOLDOWN = 60f

        // Tapestry splatter. Seconds the kill window stays open, and how many must fall inside it.
        const val MULTIKILL_WINDOW_SECS = 1.6f
        const val MULTIKILL_THRESHOLD = 4

        /**
         * Men on the field at the horn before the linen can be splashed at all.
         *
         * Blood thrown across the tapestry is meant to mark a slaughter, and cutting down three
         * of the four men on a level-2 beach is not one — it just made the flourish routine before
         * the player had seen a real battle. The opening levels field 2-4, so this holds it back
         * until the host is genuinely a host.
         */
        const val MULTIKILL_MIN_HOST = 7

        // The diggers. Short trips: they are meant to open a second front early in the fight,
        // not be absent for half of it. The moleman swims through soil with his hands and is the
        // quicker of the two; the sapper is shifting earth with a spade.
        const val SAPPER_BURROW_SECONDS = 4.2f
        const val MOLEMAN_BURROW_SECONDS = 2.8f
        /** How long the descent itself takes — he sinks at the dig site, throwing up spoil. */
        const val DIG_DOWN_SECS = 0.9f

        /** Finish a level with at least this much health left and it counts as unpunished. */
        const val UNPUNISHED_HP_FRACTION = 0.8f
        /**
         * Unpunished levels in a row before the host starts compensating. Below this the run is
         * simply going well; at and past it the player has stopped being fought and starts getting
         * extra men and extra iron thrown at him until a level costs him something again.
         */
        const val UNPUNISHED_STREAK_TRIGGER = 5
        /** Most extra bodies and armour layers the streak can ever add. */
        const val UNPUNISHED_MAX_EXTRA_ENEMIES = 3
        const val UNPUNISHED_MAX_EXTRA_ARMOUR = 2
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
        val foes = _enemiesState.value.filter {
            !it.isPlayer && !it.isDead && !it.isDying && !it.isCombatInactive &&
                it.climbState == ClimbState.NONE
        }
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
                    it.tryCrumple(3.5f)
                    engine.applyFlatDamage(25f, it, isPlayerSource = true)
                }
                _screenshake.value = 22f
            }
            DivineWeather.FROST -> {
                // Buffed: longer freeze and more of them go down.
                // Frost binds rather than fells: they keep their feet and their weapons, but the
                // ground is glass and they crawl. No knockdown — hail already owns that.
                foes.forEach {
                    it.slowDuration = 11f
                }
            }
            DivineWeather.FROGS -> {
                // Chronicle-grade chaos: frogs on EVERYONE. Foes go down hard; you and yours
                // merely stumble. High risk, high comedy.
                // Frogs rout the host. They scatter, blunder about, and nobody looses an arrow
                // with a frog down his collar — no knockdown at all, which is the point.
                foes.forEach {
                    it.panicDuration = 6f
                    engine.applyFlatDamage(8f, it, isPlayerSource = true)
                }
                _enemiesState.value.filter { it.isPlayer && !it.isDead && !it.isDying }.forEach {
                    it.isCrumpled = true
                    it.crumpleDuration = 1.4f
                }
                _playerState.value?.let {
                    if (!it.isDead && !it.isDying) {
                        it.isCrumpled = true
                        it.crumpleDuration = 1.4f
                    }
                }
                _screenshake.value = 18f
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
        initialGear.add("armor_bare")
        initialGear.add("helm_none")
        
        // Randomly unlock 2 more of each category to start
        initialGear.addAll(headRollPool().shuffled().take(MIN_GEAR_CHOICES).map { it.id })
        // The seven unlockables are earned, not rolled — the ✦ marker's promise, now kept. They still
        // reach the pool for anyone who has earned them, via poolWithUnlocks below.
        initialGear.addAll(handleRollPool().shuffled().take(MIN_GEAR_CHOICES).map { it.id })
        initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.ARMOR_PIECES.filter {
                    it.id !in listOf(
                        "armor_gauntlets", "armor_boots", "armor_coif", "armor_jester",
                        "armor_greaves", "armor_spaulders", "armor_surcoat",
                        "armor_habit", "armor_apron", "armor_frock", "armor_toga"
                    )
                }.shuffled().take(2).map { it.id })
        initialGear.addAll(GameData.HEADGEAR_PIECES.filter {
                    it.id !in listOf("helm_jester", "helm_antlered", "helm_winged", "helm_wolf", "helm_pot")
                }.shuffled().take(2).map { it.id })

        _uiState.update { it.copy(
            highscore = 0,
            unlockedGearIds = poolWithUnlocks(initialGear),
            hasThroneOption = kotlin.random.Random.nextFloat() < 0.2f
        ) }
        randomizeGear()

        // Reading the profile suspends, so the pool above is built from whatever was already cached
        // (nothing, on the first launch of the process). Re-apply once it has actually loaded, or
        // earned gear is missing until the player dies once and the retry path rebuilds the pool.
        viewModelScope.launch {
            GameProfile.migrateBatchAHandles()
            val profile = GameProfile.load()
            _uiState.update { it.copy(
                unlockedGearIds = poolWithUnlocks(it.unlockedGearIds),
                unlockedAncillaries = ancillariesWithUnlocks(it.unlockedAncillaries),
                highscore = profile.highscore,
                clearedMilestones = profile.clearedMilestones
            ) }
        }
        
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
                    state.unlockedAncillaries + List(choice.copies) { anc }
                }
                // (a mount taken as a reward also becomes the active mount — see newActiveMount)
                "follower_multiply" -> {
                    val anc = GameData.ANCILLARIES.first { it.id == choice.itemId }
                    val existingCopies = state.unlockedAncillaries.count { it.id == anc.id }
                    state.unlockedAncillaries + List(existingCopies * 2) { anc }
                }
                else -> state.unlockedAncillaries
            }
            val newTripledFollowerIds = if (choice.type == "follower_multiply") {
                state.tripledFollowerIds + choice.itemId
            } else {
                state.tripledFollowerIds
            }
            val newWeathers = if (choice.type == "weather") state.divineWeathers + DivineWeather.values().first { it.id == choice.itemId } else state.divineWeathers
            val newExtensions = if (choice.type == "extension") state.handleExtensionCount + 1 else state.handleExtensionCount
            val newRangedUpgrades = if (choice.type == "ranged_upgrade") state.rangedUpgrades + choice.itemId else state.rangedUpgrades
            val newShieldUpgrades = if (choice.type == "shield_upgrade") state.shieldUpgrades + choice.itemId else state.shieldUpgrades
            val newBrawlerUpgrades = if (choice.type == "brawler_upgrade") state.brawlerUpgrades + choice.itemId else state.brawlerUpgrades
            val newHasSilkenGarments = state.hasSilkenGarments || choice.id == "silken_garments"
            val newHasRetinuePanoply = state.hasRetinuePanoply || choice.type == "panoply"
            val newHasShieldbreaker = state.hasShieldbreaker || choice.itemId == "counter_shieldbreaker"
            val newHasSiegeLadders = state.hasSiegeLadders || choice.type == "siege_ladders"
            val newHasUnmuzzledBear = state.hasUnmuzzledBear || choice.type == "unmuzzle_bear"
            val newHasFeltShoes = state.hasFeltShoes || choice.itemId == "felt_shoes"
            val newHasPaperUnder = state.hasPaperUndergarments || choice.itemId == "paper_undergarments"
            val newHasFullShave = state.hasFullShave || choice.itemId == "full_shave"
            val newHasGreasedWeapon = state.hasGreasedWeapon || choice.itemId == "greased_weapon"
            val newHasPointierSticks = state.hasPointierSticks || choice.itemId == "pointier_sticks"
            val newHasArmorPiercing = state.hasArmorPiercing || choice.itemId == "counter_armor_piercing"
            
            var newHeadgear = state.headgear
            if (choice.itemId == "armor_jester") {
                newHeadgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_jester" }
            }
            if (choice.type == "headgear") {
                newHeadgear = GameData.HEADGEAR_PIECES.first { it.id == choice.itemId }
            }

            // Take a mount as a reward and you ride it immediately — nobody picks a warhorse and
            // then expects to walk. This is set explicitly because effectiveMount no longer falls
            // back to "whatever mount is in the list", which was force-mounting profile unlocks.
            val newActiveMount = if (choice.type == "follower" && choice.itemId in MOUNT_ANCILLARY_IDS) {
                GameData.ANCILLARIES.first { it.id == choice.itemId }
            } else {
                state.activeMount
            }
            
            // Gain dynamic buffs based on items or ancillaries selected
            state.copy(
                headgear = newHeadgear,
                extraAttachments = newAttachments,
                extraArmors = newArmors,
                unlockedAncillaries = newAncs,
                activeMount = newActiveMount,
                isThroneMode = if (newActiveMount != state.activeMount) false else state.isThroneMode,
                tripledFollowerIds = newTripledFollowerIds,
                handleExtensionCount = newExtensions,
                rangedUpgrades = newRangedUpgrades,
                shieldUpgrades = newShieldUpgrades,
                brawlerUpgrades = newBrawlerUpgrades,
                hasSilkenGarments = newHasSilkenGarments,
                hasRetinuePanoply = newHasRetinuePanoply,
                divineWeathers = newWeathers,
                hasShieldbreaker = newHasShieldbreaker,
                hasSiegeLadders = newHasSiegeLadders,
                hasUnmuzzledBear = newHasUnmuzzledBear,
                hasFeltShoes = newHasFeltShoes,
                hasPaperUndergarments = newHasPaperUnder,
                hasFullShave = newHasFullShave,
                hasGreasedWeapon = newHasGreasedWeapon,
                hasPointierSticks = newHasPointierSticks,
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

    /** Go on foot. Needed since an unlocked mount is no longer equipped whether you like it or not. */
    fun clearMount() {
        if (_uiState.value.isBattleActive) return
        _uiState.update { it.copy(activeMount = null, isThroneMode = false) }
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
        } else if (hairStyle == "hair_tonsure_monk") {
            listOf("the Devout", "the Cloistered", "Brother", "the Pious").random(rng)
        } else if (hairStyle == "hair_braids") {
            listOf("the Northman", "Braid-beard", "the Sea-wolf", "the Dane").random(rng)
        } else if (hairStyle == "hair_tonsure_norman") {
            listOf("the Norman", "Crop-head", "the Conqueror's Man").random(rng)
        } else if (hairStyle == "hair_topknot") {
            listOf("the Veteran", "Knot-hair", "the Old Soldier", "Twice-scarred").random(rng)
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
        val bossType = BossSchedule.forLevel(state.level)
        battleContentRandom = Random(
            MedievalHarpPlayer.gameSeed xor
                (state.level.toLong() * 0x425241574cL) xor 0x5341584f4e59L
        )
        val contentRandom = battleContentRandom
        val isSiegeBattle = bossType == null &&
            SiegeSchedule.isSiegeLevel(MedievalHarpPlayer.gameSeed, state.level)

        // Create player state with complete roguelike upgrade state
        val totalArmorMass = state.armor.mass + state.headgear.mass + state.extraArmors.sumOf { id -> com.example.game.GameData.ARMOR_PIECES.find { it.id == id }?.mass?.toDouble() ?: 0.0 }.toFloat()
        val currentMount = state.effectiveMount
        val chariotCollapses = currentMount == com.example.game.Ancillary.CHARIOT && totalArmorMass > ARMOR_WEIGHT_LIMIT && !state.hasSilkenGarments
        val baseHp = 100f + state.totalHpBoost
        // Soften the size-HP penalty and give an extra evasion-HP buff so small builds stay viable
        // The haircut's own small contribution, added before the size scaling so it reads the same
        // proportionally on a big build as on a small one. Deliberately tiny — see HAIR_TRAITS.
        val hair = hairTrait(state.hairStyle)
        val totalPlayerMaxHp = (baseHp + hair.hpBonus) * (0.75f + 0.25f * state.characterSize) * (1f + (1f - state.characterSize).coerceAtLeast(0f) * 0.6f)
        val player = FighterState(
            id = FighterId("player_knight"),
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
            hairStyle = if (state.hasFullShave) "bald" else state.hairStyle,
            faceNoseShape = state.faceNoseShape,
            faceBiteShape = state.faceBiteShape,
            faceForehead = state.faceForehead,
            faceMustache = state.faceMustache,
            level = state.level,
            extraAttachments = state.extraAttachments.mapNotNull { id -> GameData.WEAPON_HEADS.find { it.id == id } },
            extraArmors = state.extraArmors.mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } },
            handleExtensionCount = state.handleExtensionCount,
            speedBoost = state.totalSpeedBoost + hair.speedBonus,
            rangedUpgrades = state.rangedUpgrades,
            shieldUpgrades = state.shieldUpgrades,
            brawlerUpgrades = state.brawlerUpgrades,
            shieldHp = shieldHpFor(state.shield, state.shieldUpgrades),
            isMounted = currentMount == Ancillary.WARHORSE || (currentMount == Ancillary.CHARIOT && !chariotCollapses) || currentMount == Ancillary.STILTS || currentMount == Ancillary.WAR_OX || currentMount == Ancillary.PACK_MULE || currentMount == Ancillary.WAR_BEAR || state.isThroneMode,
            mountHp = if (state.isThroneMode) 100f else if (currentMount == Ancillary.STILTS) 40f else if (currentMount == Ancillary.CHARIOT && !chariotCollapses) 100f else if (currentMount == Ancillary.WARHORSE) 80f else if (currentMount == Ancillary.WAR_OX) 140f else if (currentMount == Ancillary.PACK_MULE) 40f else if (currentMount == Ancillary.WAR_BEAR) 100f else 0f,
            isChariot = currentMount == Ancillary.CHARIOT && !chariotCollapses,
            isStilts = !state.isThroneMode && currentMount == Ancillary.STILTS,
            isOx = !state.isThroneMode && currentMount == Ancillary.WAR_OX,
            isMule = !state.isThroneMode && currentMount == Ancillary.PACK_MULE,
            isBear = !state.isThroneMode && currentMount == Ancillary.WAR_BEAR,
            isBearUnmuzzled = state.hasUnmuzzledBear,
            isLord = state.isThroneMode,
            hasSilkenGarments = state.hasSilkenGarments,
            weightCutKg = state.weightCutKg,
            hasGreasedWeapon = state.hasGreasedWeapon,
            // A full shave takes the hair off the picture too, not just off the scales.
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
        // Now the renderer is optimised the field can carry more bodies — a fuller host earlier
        // (was 1 + level/2, which felt thin in the opening rounds).
        // The opening three levels are a fixed, gentle ramp. Level 1 gave a bare pair and level 2
        // then jumped to three or four PLUS up to two more from perfBonus — so clearing the
        // tutorial well made the next fight harder, which is exactly backwards and was killing
        // people on level 2. The random roll and the performance bonus both start at level 4.
        // How far past the trigger the player's untouched streak has run. Zero for anyone the
        // Saxons have actually managed to hurt lately.
        val unpunished = (state.unpunishedStreak - UNPUNISHED_STREAK_TRIGGER + 1).coerceAtLeast(0)
        val rawEnemiesCount = when (state.level) {
            1 -> 2
            2 -> 3
            3 -> 4
            else -> (2 + (state.level * 6 / 10) + contentRandom.nextInt(0, 2) +
                (perfBonus * 2).toInt() +
                unpunished.coerceAtMost(UNPUNISHED_MAX_EXTRA_ENEMIES)).coerceAtLeast(2)
        }
        val enemiesCount = rawEnemiesCount.coerceAtMost(10)
        // Overflow beyond the on-screen cap arrives as reinforcements from the right once
        // the battle scrolls past dead foes — longer battles instead of inflated HP.
        pendingReinforcements =
            if (bossType != null || isSiegeBattle) 0 else (rawEnemiesCount - enemiesCount).coerceIn(0, 8)
        var siegeState: SiegeState? = null
        var hillState: HillState? = null
        val enemies = when {
            bossType != null -> EnemyFactory.createBossEncounter(bossType, state.level, BossSchedule.tierForLevel(state.level))
            isSiegeBattle -> {
                // Harder siege: a fuller line of archers raining down and a deeper relief column.
                // The first siege of a run goes easier (two fewer archers on the wall); every siege
                // after it adds men to the wall and the relief column, so they keep escalating.
                val siegeOrdinal = SiegeSchedule.levels(MedievalHarpPlayer.gameSeed, state.level)
                    .indexOf(state.level).coerceAtLeast(0)
                val parapetCount = (max(3, (enemiesCount * 2) / 3) +
                    (if (siegeOrdinal == 0) -2 else siegeOrdinal.coerceAtMost(4))).coerceAtLeast(1)
                val queueCount = max(3, enemiesCount + 2) + siegeOrdinal.coerceAtMost(5)
                // A late siege is defended, not merely garrisoned: from level 30 the wall and the
                // relief column layer on coif, gauntlets and boots the way the field host already
                // does, and a second piece goes on at 50.
                val defenderArmour = GameData.ARMOR_PIECES
                    .filter { it.id in listOf("armor_coif", "armor_gauntlets", "armor_boots") }
                    .take(if (state.level >= 50) 2 else if (state.level >= 30) 1 else 0)
                val wall = List(parapetCount) { index ->
                    EnemyFactory.createArchetype(EnemyArchetype.WALL_ARCHER, index, state.level).apply {
                        elevated = true
                        posX = 1850f + index * 90f
                        targetX = posX
                        extraArmors = defenderArmour
                    }
                }
                val queue = List(queueCount) { index ->
                    val type = when {
                        index == 0 -> EnemyArchetype.DANE_AXE_EXECUTIONER
                        index == 1 -> EnemyArchetype.NORMAN_LOYALIST
                        index == 2 -> EnemyArchetype.TORCH_BEARER
                        // Deep sieges send the marginalia out of the gate: a snail squatting in the
                        // gateway that has to be chewed through, and dog-heads down the column.
                        state.level >= 40 && index == 3 -> EnemyArchetype.REBEL_SNAIL
                        state.level >= 30 && index % 4 == 3 -> EnemyArchetype.CYNOCEPHALUS
                        else -> EnemyArchetype.HOUSECARL
                    }
                    EnemyFactory.createArchetype(type, parapetCount + index, state.level).apply {
                        posX = 2050f + index * 65f
                        targetX = posX
                        isCombatInactive = true
                        // The snail's shell is its armour; strapping mail to it is nonsense.
                        if (type != EnemyArchetype.REBEL_SNAIL) extraArmors = defenderArmour
                    }
                }
                val gateHp = 360f + state.level * 22f
                siegeState = SiegeState(
                    gateHp = gateHp,
                    gateMaxHp = gateHp,
                    parapetFighterIds = wall.map { it.id }.toSet(),
                    queuedFighterIds = queue.map { it.id }.toSet(),
                    siegeLadders = state.hasSiegeLadders
                )
                (wall + queue).toMutableList()
            }
            else -> List(enemiesCount) { index ->
                EnemyFactory.randomSaxon(index, state.level, contentRandom)
            }.toMutableList()
        }

        // The host answers a player nobody can touch. Extra bodies came in above; this is the iron.
        // Applied after every spawn branch so it reaches sieges and boss retinues too, and it lifts
        // by itself the moment one level actually costs the player some health.
        if (unpunished > 0) {
            val hardening = GameData.ARMOR_PIECES
                .filter { it.id in listOf("armor_coif", "armor_gauntlets", "armor_boots") }
                .take(unpunished.coerceAtMost(UNPUNISHED_MAX_EXTRA_ARMOUR))
            enemies.filter { !it.isPlayer && it.archetype != EnemyArchetype.REBEL_SNAIL }
                .forEach { foe ->
                    foe.extraArmors = (foe.extraArmors + hardening).distinctBy { it.id }
                }
        }

        // Retinue bodies spawn one per copy (Twins/Thrice-Blessed) — ids get "#i" and everything
        // that cares matches via isKind(). posX jittered per copy or they stand inside each other.
        repeat(state.unlockedAncillaries.count { it == Ancillary.FANATIC }) { i ->
            enemies.add(FighterState(
                id = FighterId("fanatic_boris#$i"),
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
        if (bossType == null && !isSiegeBattle &&
            state.level >= EnemyFactory.SHIELD_WALL_FROM_LEVEL && contentRandom.nextFloat() < 0.20f
        ) {
            enemies.addAll(EnemyFactory.shieldWallPair(enemiesCount, state.level))
            metCounters.add(EnemyFactory.COUNTER_SHIELD_WALL)
        }
        if (bossType == null && !isSiegeBattle &&
            state.level >= EnemyFactory.BRUTE_FROM_LEVEL && contentRandom.nextFloat() < 0.25f
        ) {
            enemies.add(EnemyFactory.armouredBrute(enemiesCount + 2, state.level))
            metCounters.add(EnemyFactory.COUNTER_BRUTE)
        }
        if (bossType == null && !isSiegeBattle &&
            state.level >= EnemyFactory.WAR_PRIEST_FROM_LEVEL && contentRandom.nextFloat() < 0.15f
        ) {
            enemies.add(EnemyFactory.warPriest(enemiesCount + 3, state.level))
            metCounters.add(EnemyFactory.COUNTER_WAR_PRIEST)
        }

        // Deep-run curveballs: from level 40 the host occasionally shows up with a surprise —
        // a pair of immovable brutes, a berserker mob, or a double shield wall.
        if (bossType == null && !isSiegeBattle && state.level >= 40) {
            when (contentRandom.nextInt(5)) {
                0 -> repeat(2) { enemies.add(EnemyFactory.armouredBrute(enemiesCount + 10 + it, state.level)) }
                1 -> repeat(3) { enemies.add(EnemyFactory.createArchetype(EnemyArchetype.BERSERKER, enemiesCount + 10 + it, state.level)) }
                2 -> {
                    enemies.addAll(EnemyFactory.shieldWallPair(enemiesCount + 10, state.level))
                    enemies.addAll(EnemyFactory.shieldWallPair(enemiesCount + 12, state.level))
                }
                3 -> // The marginalia strike back: a giant snail joins the host
                    enemies.add(EnemyFactory.createArchetype(EnemyArchetype.REBEL_SNAIL, enemiesCount + 10, state.level))
                // else: a plain fight — the threat of the curveball is part of the curve
            }
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.PLAGUE_PEASANT }) { i ->
            enemies.add(FighterState(
                // Dying already, so he simply runs at the foe and breathes on them until one of them drops
                id = FighterId("plague_peasant#$i"),
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
                id = FighterId("greaser#$i"), name = "Slippery Sam", isPlayer = true, maxHp = 35f, hp = 35f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 40f + i * 22f, targetX = 40f + i * 22f, facingRight = true, size = 0.85f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF6B4A1F), hairStyle = "short", isDualWielding = false
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.BEEKEEPER }) { i ->
            enemies.add(FighterState(
                // Backline lobber like the hag; CombatEngine turns his shots into bee hives via
                // isKind("beekeeper"). The renderer gives him his veiled hat and a live swarm.
                id = FighterId("beekeeper#$i"), name = "Humble Bede", isPlayer = true, maxHp = 40f, hp = 40f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 35f + i * 22f, targetX = 35f + i * 22f, facingRight = true, size = 0.95f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF8A7156), hairStyle = "bald", isDualWielding = false
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.FIREBRAND }) { i ->
            enemies.add(FighterState(
                // Same trick as the hag/greaser: head_slingshot marks him isRanged so he holds the
                // backline; CombatEngine turns his shots into igniting torches via isKind("firebrand").
                id = FighterId("firebrand#$i"), name = "Cinder Cedric", isPlayer = true, maxHp = 35f, hp = 35f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 30f + i * 22f, targetX = 30f + i * 22f, facingRight = true, size = 0.9f,
                hairColor = androidx.compose.ui.graphics.Color(0xFFC08030), hairStyle = "short", isDualWielding = false
            ))
        }

        repeat(state.unlockedAncillaries.count { it == Ancillary.HAG }) { i ->
            enemies.add(FighterState(
                // head_slingshot makes her isRanged, so the AI kites at range and lobs mud instead of rushing to melee
                id = FighterId("hag#$i"), name = "Local Hag", isPlayer = true, maxHp = 40f, hp = 40f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_slingshot" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 20f + i * 20f, targetX = 20f + i * 20f, facingRight = true, size = 0.8f, hairColor = androidx.compose.ui.graphics.Color(0xFFAAAAAA), hairStyle = "long", isDualWielding = false
            ))
        }

        // Longbowman and crossbowman fight from the field like the hag — real bodies with real
        // bows, not parade-line followers conjuring arrows from thin air.
        repeat(state.unlockedAncillaries.count { it == Ancillary.ARCHER }) { i ->
            enemies.add(FighterState(
                id = FighterId("archer#$i"), name = "Robin", isPlayer = true, maxHp = 45f, hp = 45f,
                // He is billed as the Longbowman; he carried the short bow.
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_longbow" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_leather" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 55f + i * 24f, targetX = 55f + i * 24f, facingRight = true, size = 0.95f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF4C613D), hairStyle = "short", isDualWielding = false
            ))
        }
        repeat(state.unlockedAncillaries.count { it == Ancillary.CROSSBOWMAN }) { i ->
            enemies.add(FighterState(
                id = FighterId("crossbowman#$i"), name = "Gaston", isPlayer = true, maxHp = 50f, hp = 50f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_crossbow" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_kettle" },
                posX = 45f + i * 24f, targetX = 45f + i * 24f, facingRight = true, size = 1.0f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF3B2F2F), hairStyle = "short", isDualWielding = false
            ))
        }

        // One horse per copy, like the pets below — a `contains` check meant a TWINS
        // card handed you two entries in the list and still rolled out a single horse.
        val trojanHp = 450f + (state.level - 1) * 55f
        repeat(state.unlockedAncillaries.count { it == Ancillary.TROJAN_HORSE }) { i ->
            enemies.add(FighterState(
                // 200hp died to the enemy line long before it mattered; it exists to soak.
                // Fixed 450 collapsed almost instantly once the host started hitting properly,
                // so the decoy stopped decoying anything past the midgame.
                id = FighterId("trojan_horse#$i"), name = "Trojan Horse", isPlayer = true,
                maxHp = trojanHp, hp = trojanHp,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 180f - i * 40f, targetX = 180f - i * 40f, facingRight = true, size = 1.8f, hairColor = androidx.compose.ui.graphics.Color.Transparent, hairStyle = "none", isDualWielding = false
            ))
        }

        // The diggers. Both go under the field at the horn and surface behind the enemy line,
        // where a host that is all facing forward has nobody watching its back. burrowTimer keeps
        // them off the field entirely until then — untargetable, undrawn, unhittable.
        repeat(state.unlockedAncillaries.count { it == Ancillary.SAPPER }) { i ->
            enemies.add(FighterState(
                id = FighterId("sapper#$i"), name = "Digger Dunstan", isPlayer = true,
                maxHp = 120f + state.level * 4f, hp = 120f + state.level * 4f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_axe" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_long" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_kettle" },
                posX = 120f + i * 24f, targetX = 120f + i * 24f, facingRight = true, size = 1.0f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF6B5B4A), hairStyle = "short",
                burrowTimer = SAPPER_BURROW_SECONDS
            ))
        }
        repeat(state.unlockedAncillaries.count { it == Ancillary.MOLEMAN }) { i ->
            enemies.add(FighterState(
                id = FighterId("moleman#$i"), name = "The Moleman", isPlayer = true,
                // Very hard to kill: that is the whole of him, since he arrives alone and
                // surrounded with no armour and no weapon.
                maxHp = 260f + state.level * 9f, hp = 260f + state.level * 9f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 132f + i * 24f, targetX = 132f + i * 24f, facingRight = true, size = 1.15f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF3A2E24), hairStyle = "long",
                burrowTimer = MOLEMAN_BURROW_SECONDS
            ))
        }
        // Tiny Terrence: fast, tiny, two daggers, and he runs straight past the shield wall for
        // the archers behind it. Survivable by being impossible to shoot rather than by hp.
        repeat(state.unlockedAncillaries.count { it == Ancillary.TINY_TERRENCE }) { i ->
            enemies.add(FighterState(
                id = FighterId("tiny_terrence#$i"), name = "Tiny Terrence", isPlayer = true,
                maxHp = 70f + state.level * 3f, hp = 70f + state.level * 3f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_dagger" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_dagger" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_leather" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 100f + i * 22f, targetX = 100f + i * 22f, facingRight = true, size = 0.62f,
                hairColor = androidx.compose.ui.graphics.Color(0xFF9E6B3A), hairStyle = "short",
                isDualWielding = true, speedBoost = 1.15f
            ))
        }

        // Pets stack: rally Buster twice and you get two dogs. Ids stay unique ("wardog#0") and
        // everything that cares asks isKind("wardog"), so each one still renders as a dog and bites.
        // posX is jittered per copy or the pack spawns exactly on top of itself.
        repeat(state.unlockedAncillaries.count { it == Ancillary.WARDOG }) { i ->
            enemies.add(FighterState(
                id = FighterId("wardog#$i"), name = "Buster", isPlayer = true, maxHp = 150f, hp = 150f,
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
                id = FighterId("raven#$i"), name = "Munin", isPlayer = true, maxHp = 20f, hp = 20f,
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
            // Dedicated retinue of four. Front pair (0,1) inherit the lord's weapon and fight;
            // bearer 1 carries his shield as well. Rear pair (2,3) only carry the throne.
            //
            // Both front men carry the whole weapon. Bearer 1 used to get the lord's HANDLE with a
            // head_bare head, so the shield-carrier walked into battle brandishing a naked haft.
            //
            // All four wear the lord's armour, helm and layered pieces, and share his earned HP:
            // the men carrying you are the only thing between you and the Saxons, and they were
            // being sent out in the kit you started the run in.
            val lordArmours = state.extraArmors.mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } }
            for (i in 0 until 4) {
                val isFront = i < 2
                val bearerShield = if (i == 1 && !state.isDualWielding) {
                    state.shield
                } else {
                    GameData.SHIELDS.first { it.id == "shield_none" }
                }
                val bearerHp = 70f + state.totalHpBoost
                enemies.add(FighterState(
                    id = FighterId("pallbearer_$i"), name = "Pallbearer", isPlayer = true,
                    maxHp = bearerHp, hp = bearerHp,
                    weaponHead = if (isFront) state.weaponHead else GameData.WEAPON_HEADS.first { it.id == "head_bare" },
                    weaponHandle = if (isFront) state.weaponHandle else GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
                    shield = bearerShield,
                    armor = state.armor,
                    extraArmors = lordArmours,
                    extraAttachments = if (isFront) {
                        state.extraAttachments.mapNotNull { id -> GameData.WEAPON_HEADS.find { it.id == id } }
                    } else {
                        emptyList()
                    },
                    handleExtensionCount = if (isFront) state.handleExtensionCount else 0,
                    rangedUpgrades = if (isFront) state.rangedUpgrades else emptyList(),
                    shieldUpgrades = if (isFront) state.shieldUpgrades else emptyList(),
                    brawlerUpgrades = if (isFront) state.brawlerUpgrades else emptyList(),
                    shieldHp = if (isFront) shieldHpFor(bearerShield, state.shieldUpgrades) else 0f,
                    headgear = state.headgear,
                    posX = player.posX, targetX = player.posX, facingRight = true, size = 0.95f,
                    hairColor = androidx.compose.ui.graphics.Color(0xFF5A442E), hairStyle = "short",
                    isDualWielding = isFront && state.isDualWielding,
                    hasSilkenGarments = state.hasSilkenGarments,
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

        // Pointier Sticks: half the melee entourage are issued a real head. Applied after every
        // spawn block, like the panoply, so it reaches whoever turned up. Beasts, the decoy and
        // the bare-fisted specialists (the moleman punches on purpose) are left alone.
        if (state.hasPointierSticks) {
            val armable = enemies.filter {
                it.isPlayer && !it.isRanged && !it.isKind("wardog") && !it.isKind("raven") &&
                    !it.isKind("trojan_horse") && !it.isKind("moleman") &&
                    it.pallbearerIndex < 0
            }
            armable.filterIndexed { index, _ -> index % 2 == 0 }.forEach { ally ->
                ally.weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" }
                if (ally.weaponHandle.id == "handle_fists") {
                    ally.weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" }
                }
            }
        }

        // Retinue panoply: kit the whole retinue out in one pass, after every spawn block has run,
        // rather than threading gear through each one. Beasts and the decoy can't wear mail, and
        // the pallbearers already inherit the lord's kit.
        if (state.hasRetinuePanoply) {
            enemies.filter {
                it.isPlayer && !it.isKind("wardog") && !it.isKind("raven") &&
                    !it.isKind("trojan_horse") && !it.id.raw.startsWith("pallbearer_")
            }.forEach { ally -> applyRetinuePanoply(ally) }
            // The beasts get the helm and nothing else: a dog cannot buckle a gauntlet and a
            // raven cannot carry mail, but both can be sent a very small spangenhelm.
            enemies.filter { it.isPlayer && (it.isKind("wardog") || it.isKind("raven")) }
                .forEach { beast ->
                    beast.headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" }
                }
        }

        _playerState.value = player
        _enemiesState.value = enemies
        _projectilesState.value = emptyList()
        _popupsState.value = emptyList()
        _particlesState.value = emptyList() // clear blood from previous battle
        _tapestrySplats.value = emptyList()
        lastLivingEnemyCount = enemies.count { !it.isPlayer && !it.isDead && !it.isDying }
        battleHostSize = lastLivingEnemyCount + pendingReinforcements
        multiKillCount = 0
        multiKillWindow = 0f

        // Generate Environment
        val levelWidth = if (state.level == 1) 1500f else if (state.level >= 5) 2500f else 1000f + (contentRandom.nextFloat() * 500f)
        val bgObjects = mutableListOf<BackgroundObject>()
        
        if (chariotCollapses) {
            bgObjects.add(BackgroundObject(
                "broken_chariot", BackgroundObjectType.BROKEN_CHARIOT,
                150f, 0f, 150f, 100f, 100f, seed = contentRandom.nextInt()
            ))
            addPopup("THE CHARIOT COLLAPSES!", 150f, 110f, androidx.compose.ui.graphics.Color.Red)
        }

        if (isSiegeBattle) {
            bgObjects.addAll(
                BattlegroundContent.objectsForBattle(
                    MedievalHarpPlayer.gameSeed,
                    state.level,
                    levelWidth,
                    checkNotNull(siegeState).gateMaxHp
                )
            )
        } else if (bossType == BossType.HARALD_HARDRADA) {
            bgObjects.addAll(
                BattlegroundContent.objectsForBattle(
                    MedievalHarpPlayer.gameSeed,
                    state.level,
                    levelWidth,
                    0f
                )
            )
        } else if (state.level == 1) {
            bgObjects.add(BackgroundObject(
                "ship_0", BackgroundObjectType.SHIP,
                -40f, 0f, 400f, 500f, 500f, seed = contentRandom.nextInt()
            ))
            player.posX = 220f
            player.targetX = 220f
        } else if (BattlegroundContent.themeFor(MedievalHarpPlayer.gameSeed, state.level) == BattlegroundTheme.FIELD) {
            // Open field: trees + long grass (from objectsFor), no forts. Later levels are hilly —
            // the enemy holds the crest and we fight our way up.
            bgObjects.addAll(BattlegroundContent.objectsFor(MedievalHarpPlayer.gameSeed, state.level, levelWidth))
            if (HillField.isHillLevel(MedievalHarpPlayer.gameSeed, state.level)) {
                val hill = HillField.stateFor(MedievalHarpPlayer.gameSeed, state.level, levelWidth)
                hillState = hill
                // The slope itself is drawn full-width in the render loop from hillState, not as a
                // per-object bitmap. The host holds the crest, clustered near the top.
                enemies.filter { !it.isPlayer && !it.isBossRetinue }.forEachIndexed { i, e ->
                    e.posX = (hill.crestX - i * 70f).coerceAtLeast(hill.footX + 200f)
                    e.targetX = e.posX
                    e.terrainLiftY = HillField.liftAt(e.posX, hill)
                }
            }
        } else {
            bgObjects.addAll(
                BattlegroundContent.objectsFor(
                    MedievalHarpPlayer.gameSeed,
                    state.level,
                    levelWidth
                )
            )
            // Generate some range cover buildings or forts. Never inside an interior — towers
            // sprouting through the feast-hall floor looked absurd.
            val isInterior = BattlegroundContent.themeFor(MedievalHarpPlayer.gameSeed, state.level) == BattlegroundTheme.INTERIOR
            val numBuildings = if (isInterior) 0 else if (state.level >= 5) contentRandom.nextInt(1, 4) else contentRandom.nextInt(0, 2)

            // Code-drawn buildings (procedural, or with seeded overlays) plus every JSON asset in
            // assets/art/ that declares a spawn block. A new .json file therefore needs no code here.
            val codeBuildings = listOf(
                BackgroundObjectType.BUILDING_BOSHAM,
                BackgroundObjectType.BUILDING_BAYEUX,
                BackgroundObjectType.TOWER_SPIRAL,
                // The tapestry-traced set. Same pool as the rest, so they turn up in ordinary play
                // rather than needing their own spawn rule.
                BackgroundObjectType.DOMED_TOWER,
                BackgroundObjectType.ABBEY_NAVE,
                BackgroundObjectType.ECCLESIA,
                BackgroundObjectType.PALACE_ARCH,
                BackgroundObjectType.BELL_TOWER,
                BackgroundObjectType.CLOISTER_WALK
            )
            val assetBuildings = VectorAsset.spawnable().filter { state.level >= (it.spawn?.minLevel ?: 2) }

            // Keep them apart: a random x per building had them growing out of each other.
            // Track each obstacle's half-width so wide pieces (the feast hall / fleet / motte
            // battleground objects, already in bgObjects) clear by their real footprint — a
            // fixed gap let buildings spawn inside the 500-wide feast hall.
            val placed = bgObjects.map { it.posX to it.width / 2f }.toMutableList()
            fun placeX(halfWidth: Float = 150f): Float {
                repeat(24) {
                    val candidate = 300f + contentRandom.nextFloat() * (levelWidth - 600f)
                    if (placed.none { abs(it.first - candidate) < it.second + halfWidth + BUILDING_MARGIN }) {
                        placed.add(candidate to halfWidth)
                        return candidate
                    }
                }
                // Crowded level: fall back to evenly spaced rather than stacked
                val fallback = 300f + placed.size * MIN_BUILDING_GAP
                placed.add(fallback to halfWidth)
                return fallback.coerceAtMost(levelWidth - 300f)
            }

            for (i in 0 until numBuildings) {
                val bx = placeX()
                if (state.level >= 5 && i == 0) {
                    val fort = listOf(
                        BackgroundObjectType.FORT_DINAN, BackgroundObjectType.FORT_PALACE,
                        BackgroundObjectType.FORT_TOWER, BackgroundObjectType.FORT_MOTTE
                    ).random(contentRandom)
                    bgObjects.add(BackgroundObject(
                        "bg_$i", fort, bx, 0f, 300f, 1000f, 1000f,
                        seed = contentRandom.nextInt()
                    ))
                    continue
                }
                // Weighted draw across both pools
                val assetPicks = assetBuildings.flatMap { a -> List(a.spawn?.weight ?: 1) { a } }
                val pick = contentRandom.nextInt(codeBuildings.size + assetPicks.size)
                if (pick < codeBuildings.size) {
                    bgObjects.add(BackgroundObject(
                        "bg_$i", codeBuildings[pick], bx, 0f, 300f, 300f, 300f,
                        seed = contentRandom.nextInt()
                    ))
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
                            seed = contentRandom.nextInt(),
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
                isRetired = false,
                // Starting a battle bare-fisted flips the music to the BRAWL (speed-metal) theme
                // for the rest of the run. Throne mode doesn't count — the lord isn't punching.
                brawlMode = it.brawlMode || (!it.isThroneMode && it.weaponHead.id == "head_bare"),
                playerHp = player.hp,
                playerMaxHp = player.maxHp,
                levelWidth = levelWidth,
                cameraX = 0f,
                backgroundObjects = bgObjects,
                // Every weather charge is ready when the horns blow
                weatherCooldowns = it.divineWeathers.associate { w -> w.id to 0f },
                seenCounters = it.seenCounters + metCounters,
                siegeState = siegeState,
                hillState = hillState,
                bossType = bossType,
                forceThroneMusic = BossSchedule.forcesThroneMusic(bossType),
                cometPortent = false
            )
        }

        engine.reset() // drop any follow-up hits queued in a previous battle
        // A weather flourish from the last battle must not replay when this screen recomposes —
        // the stale flash re-drew as a phantom "rain storm" at the start of the next fight.
        _weatherFlash.value = null
        stallSeconds = 0f
        lastVitalitySignature = Float.NaN

        // Play battle start timpani roll
        MedievalAudioSynth.playSound(SoundType.DRUM_ROLL)

        // Sir Boast-a-lot announces you over the drums — but only now and then. Every single battle
        // wore the joke out, and his clips run 4s over the top of the drum roll.
        if (_uiState.value.unlockedAncillaries.contains(Ancillary.HERALD) && Random.nextFloat() < 0.3f) {
            MedievalAudioSynth.playHeraldBoast()
        }

        // Launch game loop
        startGameLoop()
    }

    /**
     * Set when a defeat has earned an ad break. The Activity owns the SDK call (it needs a real
     * Activity), observes this, shows the interstitial and clears it — so the ViewModel stays free of
     * any ads dependency and this stays testable.
     */
    val pendingInterstitial = MutableStateFlow(false)
    fun clearPendingInterstitial() { pendingInterstitial.value = false }

    /**
     * Pay out the rewarded ad. Called only from the SDK's *earned* callback. Cards are appended, so
     * the player keeps every choice they already had.
     */
    fun grantAdReward() {
        _uiState.update { state ->
            val extra = rewardChoices(state).filter { new ->
                state.pendingLevelUpChoices.none { it.id == new.id }
            }
            if (extra.isEmpty()) state
            else state.copy(
                pendingLevelUpChoices = state.pendingLevelUpChoices + extra,
                adRewardClaimedThisLevel = true
            )
        }
    }

    /** True while the pause menu holds the battle. The loop keeps ticking but simulates nothing. */
    val isPaused = MutableStateFlow(false)
    fun setPaused(paused: Boolean) { isPaused.value = paused }

    /**
     * End the run by choice: same defeat flow (retry / share the tale) but the tapestry reads
     * "Retired" and the defeat dirge stays silent.
     */
    fun retire() {
        val s = _uiState.value
        if (!s.isBattleActive || s.battleWon || s.battleLost) return
        isPaused.value = false
        _uiState.update { it.copy(isRetired = true) }
        endBattle(won = false)
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            val dt = 0.033f // 30 FPS tick updates to keep state changes highly robust
            val scheduler = TickScheduler()
            while (_uiState.value.isBattleActive) {
                delay(scheduler.nextDelay(android.os.SystemClock.elapsedRealtime()))
                if (!isPaused.value) updateSimulation(dt)
            }
        }
    }

    private fun updateTransientEffects(dt: Float) {
        val popups = _popupsState.value
        popups.forEach { it.age += dt }
        if (popups.any { it.age >= 1.2f }) {
            _popupsState.value = popups.filter { it.age < 1.2f }
        }

        val particles = _particlesState.value
        particles.forEach {
            it.age += dt
            if (it.y < 350f || it.isSmoke) {
                it.x += it.vx * dt
                it.y += it.vy * dt
                if (!it.isSmoke) {
                    it.vy += 400f * dt
                } else {
                    it.vx += (Random.nextFloat() * 10f - 5f) * dt
                }
            }
        }

        val hasExpiredParticles = particles.any { it.age >= it.maxAge }
        if (hasExpiredParticles || particleBuffer.isNotEmpty()) {
            val survivors = if (hasExpiredParticles) particles.filter { it.age < it.maxAge } else particles
            val liveParticles = if (particleBuffer.isEmpty()) survivors else survivors + particleBuffer
            particleBuffer.clear()
            _particlesState.value =
                if (liveParticles.size > MAX_PARTICLES) liveParticles.takeLast(MAX_PARTICLES)
                else liveParticles
        }
    }

    private fun updateSimulation(dt: Float) {
        val player = _playerState.value ?: return
        var enemies = _enemiesState.value
        val projectiles = _projectilesState.value
        val siege = _uiState.value.siegeState

        // 1. Update Floating Combat Popups and Particles
        updateTransientEffects(dt)

        // One puff every 4th tick, not two every tick: at 30fps the old rate spawned 60 smoke
        // particles a second, which pinned the particle cap on its own and starved out the blood.
        incenseTick = (incenseTick + 1) % 4
        if (incenseTick == 0 && _uiState.value.unlockedAncillaries.contains(Ancillary.MONK)) {
            addIncenseParticles(player.posX - (40f * player.size), 190f, count = 1)
        }
        // Every torch bearer carries a visible flame. It was a red line on the haft, which read
        // as nothing at all — and he is now the one enemy who can set the player alight, so he
        // has to be identifiable across the field before he is in reach. Same tick budget as the
        // incense for the same reason: particles are draw calls.
        if (incenseTick == 0) {
            _enemiesState.value.forEach { e ->
                if (e.isPlayer || e.isDead || e.isDying) return@forEach
                // The archetype carries a club-and-flame; the Burning Brand weapon head IS a lit
                // torch. Both burn, whoever is holding them.
                // Every brand he carries, each tracked to where it actually is this frame.
                e.torchHeadsWorld.forEach { (tx, ty) -> addFlameAndSmokeParticles(tx, ty) }
                if (e.archetype == EnemyArchetype.TORCH_BEARER && e.torchHeadsWorld.isEmpty()) {
                    val (tx, ty) = e.weaponHeadWorld
                    addFlameAndSmokeParticles(tx, ty)
                }
                // A man alight smokes. Ignition used to show only as a warmer skin tone, which
                // is invisible on a mailed enemy at arm's length — the same puff the torch uses
                // is what a burning man looks like, so it does the job for both.
                if (e.igniteDuration > 0f) addFlameAndSmokeParticles(e.posX, 190f)
            }
            if (player.igniteDuration > 0f && !player.isDead) {
                addFlameAndSmokeParticles(player.posX, 190f)
            }
            if (!player.isDead && !player.isDying) {
                player.torchHeadsWorld.forEach { (tx, ty) -> addFlameAndSmokeParticles(tx, ty) }
            }
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

        // Multikill splatter. Counted off the living-enemy tally rather than through a new combat
        // hook: a body stops being living exactly once, whatever killed it, so this catches a
        // cleave, a hail of arrows and a divine bolt alike without CombatEngine knowing about it.
        if (battleHostSize >= MULTIKILL_MIN_HOST) {
            val livingNow = enemies.count { !it.isPlayer && !it.isDead && !it.isDying }
            val fellThisTick = (lastLivingEnemyCount - livingNow).coerceAtLeast(0)
            lastLivingEnemyCount = livingNow
            if (multiKillWindow > 0f) {
                multiKillWindow -= dt
                if (multiKillWindow <= 0f) multiKillCount = 0
            }
            if (fellThisTick > 0) {
                multiKillCount += fellThisTick
                multiKillWindow = MULTIKILL_WINDOW_SECS
                if (multiKillCount >= MULTIKILL_THRESHOLD) {
                    multiKillCount = 0
                    multiKillWindow = 0f
                    _tapestrySplats.value = _tapestrySplats.value + List(Random.nextInt(3, 6)) {
                        TapestrySplat(
                            xFrac = Random.nextFloat(),
                            // Upper reaches of the cloth, where nothing is happening — the border
                            // and the sky. Down among the feet it would read as ordinary gore.
                            yFrac = Random.nextFloat() * 0.45f,
                            radius = 10f + Random.nextFloat() * 26f,
                            seed = Random.nextInt(1000)
                        )
                    }
                }
            }
            if (_tapestrySplats.value.isNotEmpty()) {
                _tapestrySplats.value.forEach { it.age += dt }
                _tapestrySplats.value = _tapestrySplats.value.filter { it.age < it.maxAge }
            }
        }

        if (siege != null) {
            SiegeRules.tickClimb(player, dt)
            SiegeRules.tickClimbs(enemies, dt)
            SiegeRules.reconcileParapet(siege, player, enemies)
            // The player going over the wall commits the garrison exactly like a broken gate:
            // the queue behind it wakes up. Without this, mounting by ladder left them inert
            // and the battle could never end.
            if ((player.elevated || player.climbState == ClimbState.CLIMBING_UP) && !siege.gateBroken) {
                enemies.forEach {
                    if (it.id in siege.queuedFighterIds && it.isCombatInactive) it.isCombatInactive = false
                }
            }
        }

        // Nobody leaves the field for good. Knockbacks — the barrow-king's hurl, a ballista spear,
        // a body throw — can shove a fighter past the edge of the level, where he would keep
        // walking back from off-screen or simply never be reachable again. A little slack outside
        // the bounds so a shove still reads as a shove, then he is on the map and can return.
        run {
            val edge = _uiState.value.levelWidth
            player.posX = player.posX.coerceIn(-40f, edge + 40f)
            enemies.forEach { it.posX = it.posX.coerceIn(-40f, edge + 40f) }
        }

        // The diggers, under the field. While burrowTimer runs they are parked far off the left
        // edge and flagged inactive, which is what keeps them untargetable, unhittable and out of
        // the draw — no new "is he underground" check needed anywhere else.
        enemies.forEach { digger ->
            if (digger.burrowTimer > 0f && !digger.isDead && !digger.isDying) {
                digger.burrowTimer -= dt
                digger.isCombatInactive = true
                val total = if (digger.isKind("moleman")) MOLEMAN_BURROW_SECONDS else SAPPER_BURROW_SECONDS
                val elapsed = total - digger.burrowTimer
                if (elapsed < DIG_DOWN_SECS) {
                    // Still going down, in plain sight, so it reads as digging rather than as a
                    // man who simply vanished. He sinks into the ground throwing up spoil.
                    digger.visualOffsetY = 95f * (elapsed / DIG_DOWN_SECS).coerceIn(0f, 1f)
                    digger.animFrame += dt * 12f
                    if (Random.nextFloat() < dt * 26f) {
                        particleBuffer.add(BloodParticle(
                            x = digger.posX + Random.nextInt(-16, 17),
                            y = 330f + Random.nextInt(-6, 7),
                            vx = Random.nextFloat() * 90f - 45f,
                            vy = -70f - Random.nextFloat() * 70f,
                            color = if (Random.nextFloat() < 0.5f) Color(0xFF5A452C) else Color(0xFF6E5536),
                            maxAge = 0.8f + Random.nextFloat() * 0.5f
                        ))
                    }
                } else {
                    digger.posX = -600f
                    digger.visualOffsetY = 0f
                }
                if (digger.burrowTimer <= 0f && !digger.hasSurfaced) {
                    digger.hasSurfaced = true
                    digger.isCombatInactive = false
                    // Up behind the rearmost man on the field — which in a siege is behind the
                    // wall, exactly where the relief column is queued and nobody is watching.
                    val rear = enemies
                        .filter { !it.isPlayer && !it.isDead && !it.isDying }
                        .maxByOrNull { it.posX }?.posX ?: (player.posX + 400f)
                    digger.posX = rear + 90f
                    digger.targetX = digger.posX
                    digger.facingRight = false
                    _uiState.value.hillState?.let { digger.terrainLiftY = HillField.liftAt(digger.posX, it) }
                    digger.visualOffsetY = 0f
                    // Spoil thrown up as he breaks the surface, same as the hole he left behind.
                    repeat(14) {
                        particleBuffer.add(BloodParticle(
                            x = digger.posX + Random.nextInt(-20, 21),
                            y = 330f + Random.nextInt(-8, 9),
                            vx = Random.nextFloat() * 150f - 75f,
                            vy = -110f - Random.nextFloat() * 90f,
                            color = if (it % 2 == 0) Color(0xFF5A452C) else Color(0xFF6E5536),
                            maxAge = 1.0f + Random.nextFloat() * 0.6f
                        ))
                    }
                    MedievalAudioSynth.playTrojanBurst()
                    addPopup("FROM BELOW!", digger.posX, 150f, Color(0xFF8A7156))
                }
            }
        }

        // Hill terrain: lift every fighter to the slope under his feet so the high-ground bonus
        // and the render both key off the same value. Left at 0 on flat fields.
        val hill = _uiState.value.hillState
        if (hill != null) {
            player.terrainLiftY = HillField.liftAt(player.posX, hill)
            enemies.forEach { it.terrainLiftY = HillField.liftAt(it.posX, hill) }
        }

        // Stamford Bridge feeds Hardrada's guard through the choke two at a time; he joins only
        // after the last of them falls.
        if (_uiState.value.bossType == BossType.HARALD_HARDRADA) {
            val livingRetinue = enemies.filter { it.isBossRetinue && !it.isDead && !it.isDying }
            val activeRetinue = livingRetinue.count { !it.isCombatInactive }
            livingRetinue.filter { it.isCombatInactive }.take((2 - activeRetinue).coerceAtLeast(0))
                .forEach { it.isCombatInactive = false }
            if (livingRetinue.isEmpty()) {
                enemies.firstOrNull { it.bossType == BossType.HARALD_HARDRADA }
                    ?.isCombatInactive = false
            }
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
        val targetableEnemies = enemies.filter {
            !it.isDead && !it.isDying && !it.isPlayer && !it.isCombatInactive &&
                it.climbState == ClimbState.NONE &&
                (player.isRanged || it.elevated == player.elevated)
        }

        // 3. Update Player Fighter State
        var closestEnemy = targetableEnemies.minByOrNull { kotlin.math.abs(it.posX - player.posX) }
        // Firebrand: his torches keep the gate smouldering — it burns down on its own, slowly.
        if (siege != null && !siege.gateBroken &&
            enemies.any { it.isKind("firebrand") && !it.isDead && !it.isDying }
        ) {
            SiegeRules.damageGate(siege, 10f * dt, enemies)
        }
        // Ladders raise only at the wall itself — you march there first, no climbing from the
        // beach. (A broken gate keeps the old behaviour: the way is open wherever you stand.)
        if (siege != null && !player.elevated &&
            (siege.gateBroken || (siege.siegeLadders && abs(1800f - player.posX) < 140f)) &&
            enemies.none {
                !it.isPlayer && !it.isDead && !it.isDying && !it.isCombatInactive &&
                    !it.elevated && it.climbState == ClimbState.NONE
            }
        ) {
            if (player.isLord) {
                // A carried throne does not go up a ladder. The wall's defenders climb down
                // to face the lord instead — no more glitched throne-on-parapet.
                SiegeRules.livingParapetEnemies(siege, enemies).forEach { SiegeRules.beginClimbDown(it) }
            } else {
                SiegeRules.beginClimbUp(siege, player, enemies)
            }
            closestEnemy = null
        }
        engine.tick(dt) // run any queued follow-up hits before this tick's new swings
        if (siege != null && !siege.gateBroken && closestEnemy == null &&
            player.climbState == ClimbState.NONE
        ) {
            engine.updateFighter(player, null, dt)
            val gateX = 1800f
            val distance = abs(gateX - player.posX)
            if (distance > 75f) {
                player.posX += player.moveSpeed * dt
                player.facingRight = true
                // updateFighter only cycles the legs when it has a target; drive the walk bob
                // ourselves or the player moon-walks up to the gate with frozen legs.
                player.animFrame += dt * 9f
            } else if (player.attackCooldown <= 0f) {
                if (player.weaponHandle.id == "handle_ram") {
                    // The battering ram does one thing, and it does it in one blow.
                    SiegeRules.breakGate(siege, enemies)
                } else {
                    val gatecrasher = if ("gatecrasher" in player.brawlerUpgrades) 4f else 1f
                    SiegeRules.damageGate(siege, player.baseDamage.coerceAtLeast(5f) * gatecrasher, enemies)
                }
                player.attackCooldown = player.attackSpeedDelay
                player.isAttacking = true
                addPopup("PORTA!", gateX, 130f, Color(0xFF6E5536))
                MedievalAudioSynth.playSound(SoundType.CRUNCH)
            }
        } else {
            engine.updateFighter(player, closestEnemy, dt)
        }
        if (player.ghostHp > player.hp) {
            player.ghostHp -= 20f * dt
            if (player.ghostHp < player.hp) player.ghostHp = player.hp
        }

        val newEnemiesToSpawn = mutableListOf<FighterState>()
        // 4. Update Enemy Fighter States (and allied NPCs like Fanatic!)
        enemies.forEach { enemy ->
            val wasDead = enemy.isDead
            var pTarget = if (enemy.isPlayer) {
                val reachable = enemies.filter {
                    !it.isDead && !it.isDying && !it.isPlayer && !it.isCombatInactive &&
                        it.climbState == ClimbState.NONE &&
                        (enemy.isRanged || it.elevated == enemy.elevated)
                }
                // Tiny Terrence has one job and it is not the shield wall: he runs past the melee
                // entirely and goes for whoever is shooting. Only falls back to the nearest body
                // when there is nothing left with a bow.
                if (enemy.isKind("tiny_terrence")) {
                    reachable.filter { it.isRanged }.minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
                        ?: reachable.minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
                } else {
                    reachable.minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
                }
            } else {
                // Nobody stabs the gift horse. The whole trick is that the host wheels it in
                // gladly — so it is never a target, at any point, and rolls through untouched.
                (enemies.filter {
                    !it.isDead && !it.isDying && it.isPlayer && !it.isCombatInactive &&
                        it.climbState == ClimbState.NONE &&
                        (enemy.isRanged || it.elevated == enemy.elevated)
                    // The horse used to be excluded here, so nothing ever attacked the decoy whose
                    // whole purpose is to be attacked — most visibly when it rolled up to a siege
                    // wall and the garrison ignored it completely.
                } + listOfNotNull(
                    player.takeIf {
                        !it.isDead && !it.isDying && it.climbState == ClimbState.NONE &&
                            (enemy.isRanged || it.elevated == enemy.elevated)
                    }
                ))
                    .minByOrNull { kotlin.math.abs(it.posX - enemy.posX) }
            }
            // Allied NPCs no longer loiter at the rear while we alone batter the gate — with no
            // reachable foe during a siege they march up and help break it down.
            // The horse is exempt: it does not batter gates, it is invited through them. Leaving it
            // in this block also double-moved it (updateFighter already rolls it) and had a wooden
            // horse punching the oak with its bare fists.
            if (siege != null && !siege.gateBroken && enemy.isPlayer && pTarget == null &&
                !enemy.isKind("trojan_horse") &&
                !enemy.isDead && !enemy.isDying && !enemy.elevated &&
                enemy.climbState == ClimbState.NONE && enemy.pallbearerIndex < 0
            ) {
                // Siege ladders: the squad scales the wall with you instead of battering the gate —
                // but only once they have actually marched up to it.
                if (siege.siegeLadders && abs(1800f - enemy.posX) < 140f &&
                    SiegeRules.beginClimbUp(siege, enemy, enemies)
                ) return@forEach
                engine.updateFighter(enemy, null, dt)
                val gateX = 1800f
                if (abs(gateX - enemy.posX) > 75f) {
                    enemy.posX += enemy.moveSpeed * dt
                    enemy.facingRight = true
                    enemy.animFrame += dt * 9f
                } else if (enemy.attackCooldown <= 0f) {
                    SiegeRules.damageGate(siege, enemy.baseDamage.coerceAtLeast(5f), enemies)
                    enemy.attackCooldown = enemy.attackSpeedDelay
                    enemy.isAttacking = true
                }
                return@forEach
            }
            // On a hill field the host defends the crest: they hold their ground and only join
            // battle once you've climbed within reach, rather than streaming down at you.
            if (hill != null && !enemy.isPlayer && pTarget != null &&
                abs(pTarget.posX - enemy.posX) > 380f
            ) {
                engine.updateFighter(enemy, null, dt)
                return@forEach
            }
            engine.updateFighter(enemy, pTarget, dt)
            if (enemy.ghostHp > enemy.hp) {
                enemy.ghostHp -= 20f * dt
                if (enemy.ghostHp < enemy.hp) enemy.ghostHp = enemy.hp
            }
            
            // Trojan Horse death spawn
            if (!wasDead && enemy.isDead && enemy.isKind("trojan_horse")) {
                for (i in 0 until 3) {
                    val spearman = FighterState(
                        id = FighterId("trojan_knight_${System.currentTimeMillis()}_$i"), name = "Trojan Spearman", isPlayer = true,
                        maxHp = 45f, hp = 45f,
                        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" },
                        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                        shield = GameData.SHIELDS.first { it.id == "shield_none" },
                        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
                        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
                        posX = enemy.posX + Random.nextInt(-40, 40),
                        targetX = enemy.posX, facingRight = true, size = 0.95f, hairColor = Color.Black, hairStyle = "short", isDualWielding = false
                    )
                    // The men in the belly are retinue too — they just arrive late.
                    if (_uiState.value.hasRetinuePanoply) applyRetinuePanoply(spearman)
                    newEnemiesToSpawn.add(spearman)
                }
                MedievalAudioSynth.playSound(SoundType.CRUNCH)
                MedievalAudioSynth.playTrojanBurst()
            }
        }
        // Reinforcements: once dead foes have scrolled off the left edge and the field has
        // thinned, feed in the overflow enemies from the right so later battles run longer.
        val camX = _uiState.value.cameraX
        if (pendingReinforcements > 0 &&
            enemies.count { !it.isDead && !it.isDying && !it.isPlayer } < 5 &&
            enemies.any { it.isDead && !it.isPlayer && it.posX < camX - 30f }
        ) {
            val reinforcement = EnemyFactory.randomSaxon(
                enemies.size + battleContentRandom.nextInt(10000),
                _uiState.value.level,
                battleContentRandom
            )
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
            val bgHit = if (proj.ignoresCover) null else bgObjects.firstOrNull { bg ->
                !bg.isDestroyed && bg.type != BackgroundObjectType.SHIP &&
                bg.type != BackgroundObjectType.CASTLE_GATE &&
                bg.type != BackgroundObjectType.CASTLE_WALL &&
                bg.type != BackgroundObjectType.MOTTE &&
                // You are INSIDE these — the room itself cannot stand between archer and target.
                bg.type != BackgroundObjectType.FEASTING_HALL &&
                bg.type != BackgroundObjectType.INTERIOR_KITCHEN &&
                bg.type != BackgroundObjectType.INTERIOR_CHAMBER &&
                proj.posX in (bg.posX - 100f)..(bg.posX + 100f) && proj.atBodyHeight() &&
                coverTargets.any { t ->
                    val behindBuilding = if (proj.velocityX >= 0f) t.posX > bg.posX else t.posX < bg.posX
                    behindBuilding && abs(t.posX - bg.posX) < 130f
                }
            }
            if (bgHit != null) {
                hit = true
                // A defender's torch belongs on the attacker, not on his own cover.
                if (proj.type != ProjectileType.TORCH || proj.isPlayerOwned) {
                    bgHit.hp -= proj.damage
                }
                if (proj.type.isArrowLike) {
                    // Capped: the renderer draws one arrow per count, so an uncapped counter grew
                    // the per-building draw cost for the whole battle. 20 a side already reads as
                    // a pincushion.
                    if (proj.velocityX > 0) bgHit.stuckArrowsFromLeft = (bgHit.stuckArrowsFromLeft + 1).coerceAtMost(20)
                    else bgHit.stuckArrowsFromRight = (bgHit.stuckArrowsFromRight + 1).coerceAtMost(20)

                    val impactAngle = kotlin.math.atan2(proj.velocityY.toDouble(), proj.velocityX.toDouble()).toFloat()
                    val jitteredAngle = impactAngle + (Random.nextFloat() - 0.5f) * 0.16f
                    val relX = (proj.posX - bgHit.posX).coerceIn(-bgHit.width / 2f + 15f, bgHit.width / 2f - 15f)
                    val relY = (proj.posY - 200f).coerceIn(-150f, -20f)
                    if (bgHit.stuckBuildingArrows.size < 30) {
                        bgHit.stuckBuildingArrows.add(
                            StuckBuildingArrow(
                                offsetX = relX,
                                offsetY = relY,
                                angle = jitteredAngle,
                                fromLeft = proj.velocityX >= 0f,
                                projType = proj.type,
                                sizeMultiplier = proj.sizeMultiplier
                            )
                        )
                    }
                }
                if (bgHit.hp <= 0) bgHit.isDestroyed = true
            }
            
            // Check entity collisions if not hit building
            if (!hit) {
                if (proj.isPlayerOwned) {
                // Hit test against enemies
                for (enemy in livingEnemies) {
                    if (enemy.isCombatInactive || enemy.climbState != ClimbState.NONE) continue
                    if (abs(proj.posX - enemy.posX) < 30f && proj.atBodyHeight()) {
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
                    .filter {
                        !it.isDead && it.climbState == ClimbState.NONE &&
                            abs(proj.posX - it.posX) < 30f
                    }
                    .minByOrNull { abs(proj.posX - it.posX) }
                if (friendly != null && proj.atBodyHeight()) {
                    engine.applyProjectileDamage(proj, friendly)
                    hit = true
                }
            }
        }

        // Boundary collision or hit
            if (!hit && proj.posX in -500f..(_uiState.value.levelWidth + 500f) && proj.posY - proj.launchLiftY < 350f) {
                remainingProjectiles.add(proj)
            } else if (bgHit != null) {
                // Only building/cover hits thud from here — body hits sound from CombatEngine,
                // which knows whether the missile struck iron, shield or flesh.
                MedievalAudioSynth.playSound(SoundType.THWACK)
            }
        }
        
        // (Archer & crossbowman now fight as their own bodies on the field, spawned in startBattle —
        // their shots come from CombatEngine like any other ranged fighter.)

        // One throw rate per Lil Guy on your back — three of them sling three times as often.
        // Must run BEFORE the StateFlow assignment below: adding to the list after assigning it
        // was lost whenever the flow's equality check skipped the (structurally equal, e.g. empty)
        // new list — which is why he waved his arms and nothing ever flew.
        val lilGuyCount = _uiState.value.unlockedAncillaries.count { it == Ancillary.LIL_GUY }
        if (lilGuyCount > 0 && !player.isDead) {
            val before = player.lilGuyThrowPhase
            player.lilGuyThrowPhase = (before + dt * lilGuyCount / LIL_GUY_THROW_SECONDS) % 1f
            // The dart leaves as the arm reaches full forward, i.e. exactly when the phase wraps.
            if (player.lilGuyThrowPhase < before) {
                remainingProjectiles.add(createLilGuyDart(player))
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
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

        // The entourage's voices. Rolled PER BODY, not once for the whole kind, and each body
        // passes its own id as a voice key: one dog cannot bark over his own bark, but two dogs
        // can bark together. Rolling once for "any dog alive" is what made a lone beast sound
        // like a kennel — the roll fired again while his last bark was still sounding.
        enemies.forEach { e ->
            if (e.isDead || e.isDying) return@forEach
            when {
                // Buster barks every now and then mid-battle (rare, for comedy)
                e.isKind("wardog") && Random.nextFloat() < dt * 0.2f ->
                    MedievalAudioSynth.playDogBark(e.id.raw)
                // Old Maud cackles now and then (assets/hag; silent until clips are added)
                e.isKind("hag") && Random.nextFloat() < dt * 0.15f ->
                    MedievalAudioSynth.playHagCackle(e.id.raw)
                e.isKind("fanatic_boris") && Random.nextFloat() < dt * 0.15f ->
                    MedievalAudioSynth.playFanaticScream(e.id.raw)
                e.isKind("plague_peasant") && Random.nextFloat() < dt * 0.2f ->
                    MedievalAudioSynth.playPlagueCough(e.id.raw)
            }
        }
        // The Monk is a non-combatant follower, so he is not on the field to be found in
        // `enemies` — his bodies are the copies held in unlockedAncillaries.
        repeat(_uiState.value.unlockedAncillaries.count { it == Ancillary.MONK }) { i ->
            if (Random.nextFloat() < dt * 0.1f) MedievalAudioSynth.playMonkChant("monk#$i")
        }

        // Softlock watchdog: if no hp anywhere (fighters, mounts, shields, gate) has moved for a
        // while, some units are stuck — inactive spawns never released, or someone shoved off the
        // playfield. Wake everything up and drag strays back in rather than leave a dead battle.
        // Positions are in the signature too: a long quiet march (e.g. the walk to a siege gate)
        // is progress, not a stall — hp alone would fire mid-march and release the gate garrison.
        // ponytail: sum-signature can theoretically miss offsetting changes in one tick; fine as a watchdog
        val vitality = player.hp + player.mountHp + player.posX +
            enemies.sumOf { (it.hp + it.mountHp + it.shieldHp + it.posX).toDouble() }.toFloat() +
            (siege?.gateHp ?: 0f)
        if (vitality == lastVitalitySignature) stallSeconds += dt else stallSeconds = 0f
        lastVitalitySignature = vitality
        if (stallSeconds > 12f) {
            stallSeconds = 0f
            enemies.forEach { e ->
                if (e.isCombatInactive && !e.isDead && !e.isDying) e.isCombatInactive = false
                // Stranded on a parapet with nobody coming up? Come down and fight.
                if (e.elevated && !e.isPlayer && !player.elevated) SiegeRules.beginClimbDown(e)
                if (!e.isDead && !e.isDying) {
                    e.posX = e.posX.coerceIn(30f, _uiState.value.levelWidth - 30f)
                    e.targetX = e.posX
                }
            }
            player.posX = player.posX.coerceIn(30f, _uiState.value.levelWidth - 30f)
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

    /**
     * A burning brand, or a burning man: a couple of short-lived flame licks with a longer grey
     * plume above them, the same shape as the monk's censer but hot. The flame rises fast and dies
     * fast; the smoke drifts and lingers, which is what makes it read as fire rather than as
     * coloured confetti.
     */
    private fun addFlameAndSmokeParticles(x: Float, y: Float) {
        val flame = List(2) {
            BloodParticle(
                x = x + Random.nextInt(-3, 4),
                y = y - Random.nextInt(0, 6),
                vx = Random.nextFloat() * 22f - 11f,
                vy = Random.nextFloat() * -70f - 30f,
                color = if (Random.nextBoolean()) Color(0xFFE8A33A) else Color(0xFFD4562A),
                isSmoke = true,
                maxAge = 0.5f + Random.nextFloat() * 0.4f
            )
        }
        val smoke = BloodParticle(
            x = x + Random.nextInt(-4, 5),
            y = y - 14f,
            vx = Random.nextFloat() * 26f - 13f,
            vy = Random.nextFloat() * -42f - 16f,
            color = Color(0xFF6E6A63),
            isSmoke = true,
            maxAge = 1.8f + Random.nextFloat() * 1.2f
        )
        particleBuffer.addAll(flame + smoke)
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

    /**
     * Sieges cleared in the current run, for the siege milestones. Deliberately per-run rather than
     * a lifetime tally: three sieges in one run is the achievement, and it needs no extra saved key.
     */
    private var siegesClearedThisRun = 0

    /**
     * Check every milestone the run's current state could have satisfied. Cheap — eleven set
     * lookups — and only runs between battles, so there is no reason to be clever about it.
     */
    private fun checkMilestones(
        won: Boolean, level: Int, kills: Int, woreNoArmour: Boolean,
        bossBeaten: BossType?, usedFistsOnly: Boolean, siegesCleared: Int,
        monkInRetinue: Boolean, score: Int, previousBest: Int
    ) {
        if (!won) return
        val profile = GameProfile.cached
        val earned = buildList {
            if (level >= 5) add(Milestone.REACH_5)
            if (level >= 10) add(Milestone.REACH_10)
            if (level >= 15) add(Milestone.REACH_15)
            if (level >= 20) add(Milestone.REACH_20)
            if (level >= 25) add(Milestone.REACH_25)
            if (level >= 30) add(Milestone.STAR_GAZER)
            if (level >= 35) add(Milestone.REACH_35)
            if (kills >= 500) add(Milestone.FIVE_HUNDRED_KILLS)
            if (kills >= 1000) add(Milestone.THOUSAND_KILLS)
            if (woreNoArmour) add(Milestone.NAKED_WIN)
            if (siegesCleared >= 1) add(Milestone.FIRST_SIEGE)
            if (siegesCleared >= 3) add(Milestone.THREE_SIEGES)
            if (siegesCleared >= 5) add(Milestone.FIVE_SIEGES)
            if (monkInRetinue) add(Milestone.MONK_SURVIVES)
            when (bossBeaten) {
                BossType.HAROLD_GODWINSON -> add(Milestone.BEAT_HAROLD)
                BossType.HARALD_HARDRADA -> { add(Milestone.BEAT_HARDRADA); add(Milestone.HARDRADA_BRAIDS) }
                BossType.WILLIAM_THE_BASTARD -> add(Milestone.BEAT_WILLIAM)
                BossType.GOG, BossType.MAGOG -> add(Milestone.BEAT_GIANT)
                null -> {}
            }
            if (bossBeaten != null && usedFistsOnly) add(Milestone.BARE_FISTED_BOSS)
            if (bossBeaten != null && woreNoArmour) add(Milestone.UNSHORN)

            // Cross-run counters, read from the profile rather than this run's state. recordBossKill
            // runs before this, so a second William is already counted by the time we look.
            if (profile.williamKills >= 2) add(Milestone.WILLIAM_TWICE)
            if (profile.beatenBosses.containsAll(listOf("boss_gog", "boss_magog"))) add(Milestone.BOTH_GIANTS)
            // Doubling your own best: compared against the score carried INTO this battle, or the
            // new highscore would already have absorbed it and nothing would ever qualify.
            if (previousBest > 0 && score >= previousBest * 2) add(Milestone.DOUBLE_BEST)
        }
        if (earned.isEmpty()) return
        viewModelScope.launch {
            earned.forEach { m ->
                if (awardMilestone(m)) {
                    addPopup("UNLOCKED: ${m.label}", 400f, 200f, Color(0xFFB08221))
                }
            }
            // The pool is rebuilt from the freshly granted set so the new gear is selectable now,
            // not only after the next death. The trophy rows refresh with it.
            _uiState.update { it.copy(
                unlockedGearIds = poolWithUnlocks(it.unlockedGearIds),
                unlockedAncillaries = ancillariesWithUnlocks(it.unlockedAncillaries),
                clearedMilestones = GameProfile.cached.clearedMilestones
            ) }
        }
    }

    private fun endBattle(won: Boolean) {
        gameLoopJob?.cancel()

        // Read before the update block: it is what the milestone checks are judged against, and
        // that lambda can be re-executed.
        val pre = _uiState.value
        if (won && pre.siegeState != null) siegesClearedThisRun++

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
            var newUnpunishedStreak = state.unpunishedStreak
            if (won) {
                // Compute performance from hp ratio + kill rate
                val hpRatio = (state.playerHp / state.playerMaxHp).coerceIn(0f, 1f)
                val killRate = (state.totalKills.toFloat() / (state.level.toFloat() + 1f)).coerceIn(0f, 1f)
                newPerf = (hpRatio * 0.6f + killRate * 0.4f).coerceIn(0f, 1f)

                // A level that barely scratched you extends the streak; one that hurt clears it.
                newUnpunishedStreak =
                    if (hpRatio >= UNPUNISHED_HP_FRACTION) state.unpunishedStreak + 1 else 0

                val triggerMusicDecision = (state.level % 5 == 0)

                // 1a. Late-run "triple a follower" card. Level 8+ (a floor, not an exact match) and
                // only once you actually have someone to triple. Duplicates already stack their
                // hpBoost/speedBoost (SimulationModels totalHpBoost/totalSpeedBoost sum the list).
                val tripleCandidate = pickTripleCandidate(
                    state.unlockedAncillaries,
                    state.tripledFollowerIds
                )
                if (state.level >= 8 && tripleCandidate != null && Random.nextFloat() < 0.03f) {
                    val lucky = tripleCandidate
                    pendingChoices.add(LevelUpChoice(
                        id = "triple_${lucky.id}",
                        // "Thrice-Blessed" is now the ×3 badge on the card, not words in the title.
                        title = lucky.ancillaryName,
                        description = when {
                            lucky == Ancillary.MONK -> "Thy cause be truly righteous."
                            lucky in STACKABLE_ANCILLARIES ->
                                "Some say the Almighty works in threes. THREE of ${lucky.ancillaryName} take the field."
                            else ->
                                "Some say the Almighty works in threes. ${lucky.ancillaryName}'s effect is TRIPLED (Max HP +${(lucky.hpBoost * 2).toInt()}, speed +${(lucky.speedBoost * 200).toInt()}%)."
                        },
                        type = "follower_multiply",
                        itemId = lucky.id
                    ))
                }

                // 1b. Retinue panoply: arm the whole retinue. Only worth offering once you have
                // bodies on the field to equip, and only once.
                val panoplyWorthy = state.unlockedAncillaries.any {
                    it in listOf(Ancillary.HAG, Ancillary.FANATIC, Ancillary.GREASER, Ancillary.PLAGUE_PEASANT, Ancillary.FIREBRAND, Ancillary.BEEKEEPER, Ancillary.ARCHER, Ancillary.CROSSBOWMAN)
                }

                // 1c. Siege ladders: you and your squad scale fortress walls right away. Offered
                // once sieges are on the horizon, until taken.
                if (!state.hasSiegeLadders && state.level >= 4 && Random.nextFloat() < 0.35f) {
                    pendingChoices.add(LevelUpChoice(
                        id = "siege_ladders",
                        title = "Siege Ladders",
                        description = "Your retinue carries escalade ladders to every fortress. Scale the walls from the first horn — no waiting on a broken gate.",
                        type = "siege_ladders",
                        itemId = "siege_ladders"
                    ))
                }
                if (!state.hasRetinuePanoply && panoplyWorthy && state.level >= 5) {
                    pendingChoices.add(LevelUpChoice(
                        id = "retinue_panoply",
                        title = "Arm the Retinue",
                        description = "Spangenhelms, mail and iron gauntlets for every follower who walks the field. They kit up exactly as you do — and live a good deal longer for it.",
                        type = "panoply",
                        itemId = "retinue_panoply"
                    ))
                }

                // 1. Follower option. Stackable pets stay in the pool even once owned, so you can
                // keep rallying dogs/ravens and field a whole pack; everyone else dedups as before.
                // Mounts are never guaranteed on a reward screen, and get rarer once you have
                // actually taken one. "Taken" means activeMount — the mount you are riding —
                // NOT unlockedAncillaries, which now also holds a mount the start screen merely
                // OFFERED you from a profile unlock and which you may well have declined. Judging
                // it on the offer punished you for an unlock you never accepted.
                //
                // A second mount is dead weight anyway: effectiveMount only ever reads one and the
                // stat sums skip mounts outright, so a swap is all a second one can ever be.
                val ridingAlready = state.activeMount != null
                val mountChance = if (ridingAlready) 0.10f else 0.40f
                val suppressMounts = Random.nextFloat() >= mountChance
                val availableAncs = GameData.ANCILLARIES.filter {
                    (it !in state.unlockedAncillaries || it in STACKABLE_ANCILLARIES) &&
                        !(suppressMounts && it.id in MOUNT_ANCILLARY_IDS)
                }
                // Two distinct follower offers instead of one — the extra card per battle.
                // First slot favours someone you DON'T yet own: the always-eligible stackable
                // pets were crowding the pool, so every run collected the same dogs.
                val unowned = availableAncs.filter { it !in state.unlockedAncillaries }
                // At most ONE mount across both offer slots. Two mounts in one round cost two
                // follower cards for a choice you can only take one of anyway — the mount always
                // arrives at the entourage's expense, so it may take one slot and no more.
                val followerOffers = (unowned.shuffled().take(1) + availableAncs.shuffled())
                    .distinct()
                    .fold(mutableListOf<Ancillary>()) { picked, anc ->
                        val isMount = anc.id in MOUNT_ANCILLARY_IDS
                        if (picked.size < 2 && !(isMount && picked.any { it.id in MOUNT_ANCILLARY_IDS })) {
                            picked.add(anc)
                        }
                        picked
                    }
                    .take(2)
                if (followerOffers.isNotEmpty()) {
                    followerOffers.forEach { anc ->
                        val isObject = anc in listOf(com.example.game.Ancillary.WARHORSE, com.example.game.Ancillary.CHARIOT, com.example.game.Ancillary.STILTS, com.example.game.Ancillary.TROJAN_HORSE)
                        val owned = state.unlockedAncillaries.count { it == anc }
                        val titleSuffix = if (isObject) "" else " the ${anc.role}"
                        val packNote = if (owned > 0) " You already have $owned — they stack." else ""
                        // Twin mounts grant nothing — you can only ride one. Never offer them.
                        val copiesCount = if (anc.id in MOUNT_ANCILLARY_IDS) 1 else rollFollowerCopies()
                        val isTwins = copiesCount > 1
                        // No shouted "TWINS!" in the title or the body any more — the card wears a
                        // "+2" badge instead, which says the same thing without eating two lines.
                        val twinTitle = "${anc.ancillaryName}$titleSuffix"
                        // Objects field their own body, so their hpBoost is that body's — quoting
                        // it as "Max HP +200" read as a player buff the Great Horse never grants.
                        val statLine = if (anc.id in OBJECT_ANCILLARY_IDS) "Fights on its own: ${anc.hpBoost.toInt()} HP of its own"
                                       else "Entourage follower: Max HP +${anc.hpBoost.toInt()}, speed +${(anc.speedBoost * 100).toInt()}%"
                        // Two Brothers Tuck censing you at once is not a stat line, it is a verdict
                        // on the war you are prosecuting.
                        val body = if (isTwins && anc == Ancillary.MONK) "Thy cause be truly righteous."
                                   else anc.description
                        val twinDesc = "$body ($statLine)$packNote"
                        pendingChoices.add(LevelUpChoice(
                            id = if (isTwins) "follower_twins_${anc.id}" else "follower_${anc.id}",
                            title = twinTitle,
                            description = twinDesc,
                            type = "follower",
                            itemId = anc.id,
                            copies = copiesCount
                        ))
                    }
                } else {
                    // Fallback boost if all followers are hired
                    pendingChoices.add(LevelUpChoice(
                        id = "boost_hp",
                        title = "Follower Vitality Boost",
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
                        LevelUpChoice("brawler_wrestling_belt", "Brawler: Champion Belt", "Increases grapple strength. Wrestle foes to the ground!", "brawler_upgrade", "champion_belt"),
                        LevelUpChoice("brawler_gatecrasher", "Brawler: Gatecrasher Fists", "Punch through oak like it owes you money. Your bare hands deal 4x damage to fortress gates!", "brawler_upgrade", "gatecrasher")
                    )
                    val availableBrawler = possibleBrawler.filter { it.itemId !in state.brawlerUpgrades }
                    if (availableBrawler.isNotEmpty()) {
                        pendingChoices.add(availableBrawler.random())
                    }
                } else if (!state.weaponHead.isRanged) {
                    // Very rarely the attachment on offer is a Strange Relic — the eel, the femur,
                    // the goose, the cheese. Never in the normal pool, each taken only once.
                    val strangeAvailable = GameData.WEAPON_HEADS.filter {
                        it.id in GameData.STRANGE_HEAD_IDS && it.id !in state.extraAttachments
                    }
                    val strange = strangeAvailable.isNotEmpty() && state.level >= 10 && Random.nextFloat() < 0.02f
                    val weaponHead = if (strange) strangeAvailable.random() else {
                        GameData.WEAPON_HEADS.filter {
                            it.id !in listOf("head_bare", "head_bow", "head_longbow", "head_slingshot") &&
                                it.id !in GameData.STRANGE_HEAD_IDS &&
                                // The brand is in the reward pool, but only one round in six.
                                (it.id !in GameData.RARE_HEAD_IDS || Random.nextFloat() < 0.17f)
                        }.random()
                    }
                    pendingChoices.add(LevelUpChoice(
                        id = "attach_${weaponHead.id}",
                        title = if (strange) "STRANGE RELIC: ${weaponHead.itemName}" else weaponHead.itemName,
                        description = "${weaponHead.description} Attached dynamically to weapon, adding +50% of its base damage!",
                        type = "attachment",
                        itemId = weaponHead.id
                    ))
                }

                // 3. Handle Extension or Layered Armor or Shield Upgrade option
                val rndVal = Random.nextFloat()
                // No haft extension on ranged weapons — a longer shaft does nothing for a bow/sling —
                // and none once reach is capped, or the card is a wasted slot every level after.
                val reachCapped = GameData.meleeReachMetres(
                    state.weaponHandle.id, state.weaponHead.reach,
                    state.characterSize, state.handleExtensionCount
                ) >= GameData.MAX_MELEE_REACH_M
                if (rndVal < 0.33f && !isUnarmed && !state.weaponHead.isRanged && !reachCapped) {
                    pendingChoices.add(LevelUpChoice(
                        id = "extension",
                        title = "Handle Extension",
                        description = "Lash an additional wood shaft extension to your grip. Increases reach (+1m, whatever your size) and supports more attachments!",
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
                                title = "Silken Garments",
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
                            pendingChoices.add(LevelUpChoice(
                                id = "armor_${armorPiece.id}",
                                title = armorPiece.itemName,
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
                            LevelUpChoice("ranged_slingshot_poison", "Sling: Swamp-Mud Poison", "Dip your stones in venomous Hastings swamp slime. Poisons foes, dealing damage over time!", "ranged_upgrade", "slingshot_poison"),
                            LevelUpChoice("ranged_slingshot_plague", "Sling: Pestilent Eyeballs", "Sling the eyes of the plague dead over the line, as the besiegers of old did with whole corpses. Infects whoever they strike — and the rot piles onto any pestilence already in him.", "ranged_upgrade", "slingshot_plague")
                        )
                    } else {
                        listOf(
                            LevelUpChoice("ranged_bow_bigger", "Ranged: Ballista Spears", "Launch thick spear-shafts instead of arrows! High velocity, +15 damage, and knocks foes back.", "ranged_upgrade", "bow_bigger"),
                            LevelUpChoice("ranged_bow_spikes", "Ranged: Bodkin Barb-Points", "Solder razor-sharp steel claws to your arrowheads. Ignores 50% armor and deals bleeding.", "ranged_upgrade", "bow_spikes"),
                            LevelUpChoice("ranged_bow_weapon_heads", "Ranged: Weapon-Tipped Shafts", "Fletch actual miniature iron morningstars and axes onto your arrows. Complete comedic over-engineering!", "ranged_upgrade", "bow_weapon_heads")
                        )
                    }

                    // Rare prizes a bow and a sling can both win. Multishot has no level gate on
                    // purpose: a triple shot in the opening rounds is a story worth telling, and
                    // the roll is thin enough that it stays a story. The other two want a run with
                    // some road behind it.
                    val rareRanged = listOf(
                        LevelUpChoice(
                            "ranged_cluster", "Ranged: Cluster Charge",
                            "Pack every shot with flint scrap and iron nails. Each one bursts on impact, spraying ${CombatEngine.CLUSTER_FRAGMENTS} fragments through whoever is standing close.",
                            "ranged_upgrade", "cluster"
                        ) to (state.level >= 8 && Random.nextFloat() < 0.12f),
                        LevelUpChoice(
                            "ranged_volley", "Ranged: Arcing Volley",
                            "Send a second shaft up with every attack. It falls on the rear ranks, well past where a flat shot dies — and it costs you nothing, it flies alongside your normal shot.",
                            "ranged_upgrade", "volley"
                        ) to (state.level >= 5 && Random.nextFloat() < 0.15f),
                        LevelUpChoice(
                            "ranged_multishot_double", "Ranged: Double Shot",
                            "Two shafts to the string at once. Every attack looses an extra missile, forever.",
                            "ranged_upgrade", "multishot_double"
                        ) to (Random.nextFloat() < 0.05f),
                        LevelUpChoice(
                            "ranged_multishot_triple", "Ranged: Triple Shot",
                            "Three shafts to the string, in defiance of all bowyery and most physics. Every attack looses two extra missiles.",
                            "ranged_upgrade", "multishot_triple"
                        ) to (Random.nextFloat() < 0.02f)
                    ).filter { it.second }.map { it.first }
                        .filter { it.itemId !in state.rangedUpgrades }

                    // A rare prize that actually rolled takes the slot — rolling it and then
                    // shuffling it back into the pool would be the same as never rolling it.
                    val pick = rareRanged.firstOrNull()
                        ?: possibleUpgrades.filter { it.itemId !in state.rangedUpgrades }.randomOrNull()
                    if (pick != null) pendingChoices.add(pick)
                }
                
                // 4c. Take the straps off Grimm. Only for a player actually riding the bear, and
                // the card is blunt about the cost — an upside-only "unleash the bear" would be a
                // trap dressed as a choice.
                if (state.effectiveMount == Ancillary.WAR_BEAR && !state.hasUnmuzzledBear &&
                    state.level >= 8
                ) {
                    pendingChoices.add(LevelUpChoice(
                        id = "unmuzzle_bear",
                        title = "Unmuzzle Grimm",
                        description = "Cut the muzzle straps and let the bear fight. He mauls whatever you are fighting for heavy extra damage — but WARNING: a bear off the strap does not check who he is biting, and now and then he will maul one of your own retinue instead.",
                        type = "unmuzzle_bear",
                        itemId = "unmuzzle_bear"
                    ))
                }

                // 4d. Ways to shed kilos and buy speed once silken garments are gone. Without
                // these a late build only ever gets heavier: every armour and attachment card adds
                // mass, and mass is attack speed and foot speed. Each is offered once.
                run {
                    val cuts = mutableListOf<LevelUpChoice>()
                    if (!state.hasFeltShoes) cuts.add(LevelUpChoice(
                        "felt_shoes", "Felt Shoes",
                        "Swap your boots for stitched felt. Sheds ${FELT_SHOES_KG.toInt()}kg — you will swing and move quicker, and your feet will regret it.",
                        "weight_cut", "felt_shoes"
                    ))
                    if (!state.hasPaperUndergarments) cuts.add(LevelUpChoice(
                        "paper_undergarments", "Paper Undergarments",
                        "The monks had spare vellum. Sheds ${PAPER_UNDERGARMENTS_KG.toInt()}kg of linen you will not miss until it rains.",
                        "weight_cut", "paper_undergarments"
                    ))
                    if (!state.hasFullShave) cuts.add(LevelUpChoice(
                        "full_shave", "The Full Shave",
                        "Every hair, head to toe, off with a seax. Sheds ${FULL_SHAVE_KG.toInt()}kg and a great deal of dignity. You will be bald for the rest of the run.",
                        "weight_cut", "full_shave"
                    ))
                    if (!state.hasGreasedWeapon) cuts.add(LevelUpChoice(
                        "greased_weapon", "Grease the Blade",
                        "A pot of rendered lard, worked into the steel. Every weapon you ever carry swings ${((1f - GREASED_WEAPON_SPEEDUP) * 100).toInt()}% faster, for the rest of the run.",
                        "weight_cut", "greased_weapon"
                    ))
                    // Only from the point where weight actually starts to bite.
                    if (cuts.isNotEmpty() && state.level >= 6 && Random.nextFloat() < 0.3f) {
                        pendingChoices.add(cuts.random())
                    }
                }

                // 4e. Pointier Sticks: arm the rabble. Half the melee entourage stop punching.
                if (!state.hasPointierSticks && state.level >= 5 &&
                    state.unlockedAncillaries.isNotEmpty() && Random.nextFloat() < 0.25f
                ) {
                    pendingChoices.add(LevelUpChoice(
                        id = "pointier_sticks",
                        title = "Pointier Sticks",
                        description = "Lash a real head onto whatever your followers are swinging. Half your melee entourage trade their fists and farm tools for an actual weapon.",
                        type = "pointier_sticks",
                        itemId = "pointier_sticks"
                    ))
                }

                // 4a. A helm for a bare head. You can start a run with nothing on your head and
                // there was no way to ever change your mind. This is an offer, never a swap: it
                // only appears while the head is actually bare, and only some of the time, so a
                // deliberate bare-headed run isn't nagged every single level. The card says out
                // loud what it costs, because the score bonus for going bare is large.
                if (state.headgear.id == "helm_none" && state.level >= 3 && Random.nextFloat() < 0.35f) {
                    val helmPool = GameData.HEADGEAR_PIECES.filter {
                        it.id !in listOf("helm_none", "helm_jester") &&
                            // The heavy iron waits until a run has some road behind it.
                            (it.defense <= 45f || state.level >= 12)
                    }
                    helmPool.randomOrNull()?.let { helm ->
                        pendingChoices.add(LevelUpChoice(
                            id = "headgear_${helm.id}",
                            title = helm.itemName,
                            description = "${helm.description} +${helm.defense.toInt()} Armour — and it ends your bare-headed score bonus.",
                            type = "headgear",
                            itemId = helm.id
                        ))
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
                // Recorded by the last damaging hit; a retirement has no killer to name.
                slainByName = if (won) null else _playerState.value?.slayerName,
                slainByWeapon = if (won) null else _playerState.value?.slayerWeapon,
                score = newScore,
                highscore = newHighscore,
                level = nextLevel,
                pendingLevelUpChoices = pendingChoices,
                showLevelUpScreen = showLevelUp,
                adRewardClaimedThisLevel = false, // a fresh level-up screen, a fresh offer
                performanceScore = newPerf,
                unpunishedStreak = newUnpunishedStreak,
                bandagesCount = newBandagesCount,
                showMusicDecision = if (won && (state.level % 5 == 0)) true else state.showMusicDecision,
                pendingMusicOptions = if (won && (state.level % 5 == 0)) {
                    val allMusic = listOf("More Tempo", "Merrier", "More Solemn", "Wilder", "Nobler")
                    allMusic.shuffled().take(2) + "No Change"
                } else state.pendingMusicOptions,
                forceThroneMusic = false
            )
        }

        // Milestones are checked outside the update lambda above, which can be re-executed.
        val post = _uiState.value
        val bossBeaten = if (won) pre.bossType else null
        val previousBest = pre.highscore

        // Order matters: the cross-run counters (William twice, both giants, 25 deaths) must be
        // written before the milestone check reads them, so all of it lives in one coroutine.
        viewModelScope.launch {
            if (bossBeaten != null) {
                GameProfile.recordBossKill(
                    if (bossBeaten == BossType.WILLIAM_THE_BASTARD) GameProfile.WILLIAM_BOSS_ID
                    else "boss_${bossBeaten.name.lowercase()}"
                )
            }
            GameProfile.setHighscore(post.highscore)
            if (!won) {
                GameProfile.recordDeath()

                // Ad break, on the defeat screen only — never mid-battle. AdGate is the one place
                // that knows whether ads are allowed at all.
                if (AdGate.adsAllowedNow() && AdGate.shouldShowInterstitial(GameProfile.cached.totalDeaths)) {
                    pendingInterstitial.value = true
                }

                // Checked here rather than in checkMilestones, which returns early unless you won.
                if (GameProfile.cached.totalDeaths >= 25 && awardMilestone(Milestone.DIE_TWENTY_FIVE)) {
                    addPopup("UNLOCKED: ${Milestone.DIE_TWENTY_FIVE.label}", 400f, 200f, Color(0xFFB08221))
                    _uiState.update { it.copy(
                        unlockedGearIds = poolWithUnlocks(it.unlockedGearIds),
                        clearedMilestones = GameProfile.cached.clearedMilestones
                    ) }
                }
            }

            checkMilestones(
                won = won,
                level = post.level,
                kills = post.totalKills,
                woreNoArmour = pre.armor.id == "armor_bare" && pre.extraArmors.isEmpty(),
                bossBeaten = bossBeaten,
                usedFistsOnly = pre.weaponHead.id == "head_bare" && pre.weaponHandle.id == "handle_fists",
                siegesCleared = siegesClearedThisRun,
                monkInRetinue = pre.unlockedAncillaries.contains(Ancillary.MONK),
                score = post.score,
                previousBest = previousBest
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
                siegesClearedThisRun = 0 // the siege milestones count within a single run
                val initialGear = mutableSetOf<String>()
                initialGear.add("head_bare")
                initialGear.add("handle_fists")
                initialGear.add("shield_none")
                initialGear.add("armor_bare")
                initialGear.add("helm_none")
                initialGear.addAll(headRollPool().shuffled().take(MIN_GEAR_CHOICES).map { it.id })
                initialGear.addAll(handleRollPool().shuffled().take(maxOf(3, MIN_GEAR_CHOICES)).map { it.id })
                initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.ARMOR_PIECES.filter {
                    it.id !in listOf(
                        "armor_gauntlets", "armor_boots", "armor_coif", "armor_jester",
                        "armor_greaves", "armor_spaulders", "armor_surcoat",
                        "armor_habit", "armor_apron", "armor_frock", "armor_toga"
                    )
                }.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.HEADGEAR_PIECES.filter {
                    it.id !in listOf("helm_jester", "helm_antlered", "helm_winged", "helm_wolf", "helm_pot")
                }.shuffled().take(2).map { it.id })
                
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
                    unlockedGearIds = poolWithUnlocks(initialGear),
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    rangedUpgrades = emptyList(),
                    shieldUpgrades = emptyList(),
                    brawlerUpgrades = emptyList(),
                    hasThroneOption = kotlin.random.Random.nextFloat() < 0.2f,
                    isThroneMode = false,
                    hasTakenThrone = false,
                    // A new man keeps nothing he rallied — but an earned mount is his for good.
                    unlockedAncillaries = ancillariesWithUnlocks(emptyList()),
                    tripledFollowerIds = emptySet(),
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
                    // Same leak class as activeMount: ladders were surviving the reset, so the
                    // next run scaled walls it never earned.
                    hasSiegeLadders = false,
                    hasUnmuzzledBear = false,
                    hasFeltShoes = false,
                    hasPaperUndergarments = false,
                    hasFullShave = false,
                    hasGreasedWeapon = false,
                    hasPointierSticks = false,
                    // A new man has not yet proved untouchable, whatever the last one managed.
                    unpunishedStreak = 0,
                    isRetired = false,
                    seenCounters = emptySet(),
                    // Music state is per-run: moods were surviving the reset and stacking across
                    // runs (uncapped tempo, key drift) until every track came out discordant.
                    appliedMusicMoods = emptyList(),
                    showMusicDecision = false,
                    pendingMusicOptions = emptyList(),
                    brawlMode = false,
                    forceThroneMusic = false,
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
                    battleLost = false,
                    forceThroneMusic = false
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
