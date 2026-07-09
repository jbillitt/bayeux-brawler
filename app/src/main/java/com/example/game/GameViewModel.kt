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
    val isBallista: Boolean = false
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
    }

    fun selectLevelUpChoice(choiceId: String) {
        _uiState.update { state ->
            val choice = state.pendingLevelUpChoices.find { it.id == choiceId }
            if (choice == null) {
                return@update state.copy(
                    showLevelUpScreen = false,
                    pendingLevelUpChoices = emptyList()
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

    fun selectGear(item: GearItem) {
        if (_uiState.value.isBattleActive) return // Cannot change gear mid-battle

        _uiState.update { state ->
            var newState = when (item.type) {
                ItemType.WEAPON_HEAD -> state.copy(weaponHead = item)
                ItemType.WEAPON_HANDLE -> state.copy(weaponHandle = item)
                ItemType.SHIELD -> state.copy(shield = item)
                ItemType.ARMOR -> state.copy(armor = item)
                ItemType.HEADGEAR -> state.copy(headgear = item)
            }
            
            // Enforce two-handed rule
            val isTwoHanded = newState.weaponHead.id in listOf("head_claymore", "head_longbow", "head_halberd", "head_pike", "head_scythe", "head_bow")
            if (isTwoHanded && newState.shield.id != "shield_none") {
                newState = newState.copy(shield = GameData.SHIELDS.first { it.id == "shield_none" })
            }
            newState
        }
    }

    fun updatePhysical(size: Float, hairColor: Color, hairStyle: String) {
        if (_uiState.value.isBattleActive) return
        
        // Generate a deterministic Norman/Saxon name based on physical traits
        val seed = size.toBits() xor hairColor.value.toLong().toInt() xor hairStyle.hashCode()
        val rng = kotlin.random.Random(seed)
        
        val firstNames = if (size > 1.1f) {
            listOf("Guillaume", "Hugo", "Rollo", "Thorold", "Drogo", "Godfrey")
        } else if (size < 0.9f) {
            listOf("Pippin", "Leofric", "Giles", "Alan", "Eustace", "Aethelred")
        } else {
            listOf("William", "Robert", "Richard", "Odo", "Harold", "Edward")
        }
        val firstName = firstNames.random(rng)
        
        val lastName = if (hairStyle == "bald") {
            "the Bald"
        } else if (hairStyle == "long") {
            listOf("the Wild", "the Mane", "Long-Locks").random(rng)
        } else {
            when (hairColor) {
                Color(0xFFE5C09F) -> "the Fair"
                Color(0xFFC08030) -> "the Red"
                Color(0xFF2C2219) -> "the Dark"
                else -> "the Brown"
            }
        }
        val newName = "$firstName $lastName"

        _uiState.update { state ->
            state.copy(
                characterSize = size,
                hairColor = hairColor,
                hairStyle = hairStyle,
                playerName = newName
            )
        }
    }

    fun toggleDualWield() {
        if (_uiState.value.isBattleActive) return
        _uiState.update { it.copy(isDualWielding = !it.isDualWielding) }
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
        val totalPlayerMaxHp = (100f * state.characterSize) + state.totalHpBoost
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
            level = state.level,
            extraAttachments = state.extraAttachments.mapNotNull { id -> GameData.WEAPON_HEADS.find { it.id == id } },
            extraArmors = state.extraArmors.mapNotNull { id -> GameData.ARMOR_PIECES.find { it.id == id } },
            handleExtensionCount = state.handleExtensionCount,
            speedBoost = state.totalSpeedBoost,
            rangedUpgrades = state.rangedUpgrades,
            isMounted = state.unlockedAncillaries.contains("anc_mount_horse")
        )

        // Create Saxon enemies based on level
        val enemiesCount = 1 + (state.level / 2) + Random.nextInt(0, 2)
        val enemies = List(enemiesCount) { index ->
            generateRandomSaxon(index, state.level)
        }

        _playerState.value = player
        _enemiesState.value = enemies
        _projectilesState.value = emptyList()
        _popupsState.value = emptyList()

        _uiState.update {
            it.copy(
                isBattleActive = true,
                battleWon = false,
                battleLost = false,
                playerHp = player.hp,
                playerMaxHp = player.maxHp
            )
        }

        // Play battle cry
        MedievalAudioSynth.playSound(SoundType.HUZZAH)

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

        // Scale Saxon health slightly with level
        val baseHp = 50f + (level * 10f)

        // Poor gear for early levels
        val selectedHead: com.example.game.GearItem
        val selectedHandle: com.example.game.GearItem
        val selectedShield: com.example.game.GearItem
        val selectedArmor: com.example.game.GearItem
        val selectedHelm: com.example.game.GearItem

        if (level <= 2) {
            // Early levels: pitchforks, clubs, slingshots, mostly shirtless or tunic
            val earlyHeads = listOf("head_bare", "head_pitchfork", "head_club", "head_slingshot", "head_dagger")
            val head = GameData.WEAPON_HEADS.filter { it.id in earlyHeads }.randomOrNull() ?: GameData.WEAPON_HEADS[0]
            selectedHead = head
            selectedHandle = if (head.isRanged || head.id == "head_bare") GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_short", "handle_medium") }.random()
            selectedShield = GameData.SHIELDS[0] // No shield early
            val armors = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_bare", "armor_padded") }
            selectedArmor = if (armors.isNotEmpty()) armors.random() else GameData.ARMOR_PIECES[0]
            val helms = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_none", "helm_coif") }
            selectedHelm = if (helms.isNotEmpty()) helms.random() else GameData.HEADGEAR_PIECES[0]
        } else if (level <= 4) {
            // Mid levels: spears, axes, shields
            val midHeads = listOf("head_spear", "head_axe", "head_sword", "head_bow", "head_club")
            val head = GameData.WEAPON_HEADS.filter { it.id in midHeads }.randomOrNull() ?: GameData.WEAPON_HEADS.random()
            selectedHead = head
            selectedHandle = if (head.isRanged) GameData.WEAPON_HANDLES[0] else GameData.WEAPON_HANDLES.filter { it.id in listOf("handle_medium", "handle_long") }.random()
            selectedShield = if (head.isRanged) GameData.SHIELDS[0] else GameData.SHIELDS.filter { it.id in listOf("shield_none", "shield_buckler", "shield_tower") }.random()
            val armors = GameData.ARMOR_PIECES.filter { it.id in listOf("armor_padded", "armor_leather") }
            selectedArmor = if (armors.isNotEmpty()) armors.random() else GameData.ARMOR_PIECES[0]
            val helms = GameData.HEADGEAR_PIECES.filter { it.id in listOf("helm_coif", "helm_conical") }
            selectedHelm = if (helms.isNotEmpty()) helms.random() else GameData.HEADGEAR_PIECES[0]
        } else {
            // High levels: good gear
            val weaponHeads = GameData.WEAPON_HEADS.filter { it.id !in listOf("head_bare", "head_pitchfork", "head_club", "head_slingshot") }
            selectedHead = weaponHeads.random()
            selectedHandle = if (selectedHead.isRanged) GameData.WEAPON_HANDLES[0] else listOf(GameData.WEAPON_HANDLES[1], GameData.WEAPON_HANDLES[2], GameData.WEAPON_HANDLES[4]).random()
            
            val saxonShields = listOf(GameData.SHIELDS[0], GameData.SHIELDS[1], GameData.SHIELDS[2], GameData.SHIELDS[3])
            selectedShield = if (selectedHead.isRanged) GameData.SHIELDS[0] else saxonShields.random()

            val armorOptions = listOf(GameData.ARMOR_PIECES[1], GameData.ARMOR_PIECES[2], GameData.ARMOR_PIECES[3], GameData.ARMOR_PIECES[4])
            selectedArmor = armorOptions.random()

            val helmOptions = listOf(GameData.HEADGEAR_PIECES[0], GameData.HEADGEAR_PIECES[1], GameData.HEADGEAR_PIECES[2])
            selectedHelm = helmOptions.random()
        }

        // Random physical traits
        val sizeMultiplier = Random.nextFloat() * 0.4f + 0.9f // 0.9 to 1.3
        val enemyHp = baseHp * sizeMultiplier
        val hairColors = listOf(Color(0xFFC08030), Color(0xFF5A442E), Color(0xFF8A7156), Color(0xFF2C2219), Color(0xFFE5C09F))
        val hairStyles = listOf("short", "long", "bald")

        // Saxon initial position: spread them out slightly
        val startX = 850f + (index * 130f)

        return FighterState(
            id = "saxon_$index",
            name = saxonName,
            isPlayer = false,
            maxHp = enemyHp,
            hp = enemyHp,
            weaponHead = selectedHead,
            weaponHandle = selectedHandle,
            shield = selectedShield,
            armor = selectedArmor,
            headgear = selectedHelm,
            posX = startX,
            targetX = startX,
            facingRight = false,
            size = sizeMultiplier,
            hairColor = hairColors.random(),
            hairStyle = hairStyles.random(),
            level = level
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
            if (it.y < 240f) {
                it.x += it.vx * dt
                it.y += it.vy * dt
                it.vy += 400f * dt // gravity
            }
        }
        _particlesState.value = particles.filter { it.age < it.maxAge }

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
        updateFighter(player, targetableEnemies.firstOrNull(), dt)
        if (player.ghostHp > player.hp) {
            player.ghostHp -= 20f * dt
            if (player.ghostHp < player.hp) player.ghostHp = player.hp
        }

        // 4. Update Enemy Fighter States
        enemies.forEach { enemy ->
            val pTarget = if (!player.isDead && !player.isDying) player else null
            updateFighter(enemy, pTarget, dt)
            if (enemy.ghostHp > enemy.hp) {
                enemy.ghostHp -= 20f * dt
                if (enemy.ghostHp < enemy.hp) enemy.ghostHp = enemy.hp
            }
        }

        // 5. Update Projectiles
        val remainingProjectiles = mutableListOf<Projectile>()
        projectiles.forEach { proj ->
            proj.posX += proj.velocityX * dt
            proj.posY += proj.velocityY * dt
            proj.velocityY += 130f * dt // Gravity pulling it downwards!

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
        if (_uiState.value.unlockedAncillaries.contains("anc_archer") && !player.isDead && !player.isDying) {
            if (Random.nextFloat() < dt * 0.4f) { // roughly every 2.5 seconds
                val dir = if (player.facingRight) 1f else -1f
                val proj = Projectile(
                    id = "arch_${System.currentTimeMillis()}_${Random.nextInt(100)}",
                    isPlayerOwned = true,
                    posX = player.posX - (80f * dir),
                    posY = 230f,
                    velocityX = dir * (300f + Random.nextFloat() * 80f),
                    velocityY = -45f + (Random.nextFloat() * 10f - 5f),
                    damage = 12f,
                    pierce = 8f,
                    blunt = 2f,
                    type = "arrow",
                    sizeMultiplier = 1f,
                    hasSpikes = false,
                    launchedWeaponId = null,
                    isSplash = false,
                    isPoisonous = false,
                    isBallista = false
                )
                remainingProjectiles.add(proj)
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
            }
        }
        
        val newlySpawned = _projectilesState.value.filter { it !in projectiles }
        remainingProjectiles.addAll(newlySpawned)
        _projectilesState.value = remainingProjectiles

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
            fighter.attackCooldown -= dt
        }

        // Update Swing Progress
        if (fighter.isAttacking) {
            fighter.swingProgress += dt * (1.2f / fighter.attackSpeedDelay)
            if (fighter.swingProgress >= 1f) {
                // Land strike!
                if (target != null) {
                    performStrike(fighter, target)
                }
                fighter.isAttacking = false
                fighter.swingProgress = 0f
            }
        }

        // Decide movement & actions
        if (target != null && !target.isDead) {
            val dist = abs(fighter.posX - target.posX)
            val reachPixels = fighter.reach * 40f + 40f // generous hitbox

            fighter.facingRight = target.posX > fighter.posX

            if (dist > reachPixels) {
                // Walk closer
                val direction = if (target.posX > fighter.posX) 1f else -1f
                fighter.posX += direction * fighter.moveSpeed * dt
                // Desync animations slightly based on maxHp to avoid identical marching
                fighter.animFrame = (fighter.animFrame + dt * (9f + (fighter.maxHp % 3f))) % 4f 
            } else if (dist < reachPixels * 0.7f && fighter.moveSpeed > 0f) {
                // Step back to keep them at the tip of our longer weapon!
                val direction = if (target.posX > fighter.posX) -1f else 1f
                fighter.posX += direction * (fighter.moveSpeed * 0.45f) * dt
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
            val projType = if (isSlingshot) "stone" else "arrow"
            
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
            } else {
                listOf(defender)
            }
            
            if (targets.isEmpty() || abs(attacker.posX - defender.posX) > reachPixels) {
                // Missed!
                MedievalAudioSynth.playSound(SoundType.SWOOSH)
                addPopup("SWISH!", attacker.posX + if(attacker.facingRight) 40f else -40f, 120f, Color.Gray)
                return
            }
            
            var damageFalloff = 1f

            for (currTarget in targets) {
                val isBlocked = currTarget.shield.id != "shield_none" && Random.nextFloat() < (currTarget.shield.defense / 100f)
                
                if (isBlocked) {
                    // Blocked by shield!
                    MedievalAudioSynth.playSound(SoundType.CLANG)
                    val shout = LatinShouts.getRandomShout(SoundType.CLANG)
                    addPopup(shout, currTarget.posX, 120f, Color(0xFFB08221))
                    
                    // Still take minimal blunt impact damage
                    val blockDamage = (attacker.damageBlunt * 0.15f * damageFalloff).coerceAtLeast(1f)
                    if (blockDamage > 5f && kotlin.random.Random.nextBoolean()) MedievalAudioSynth.playSound(SoundType.CRUNCH)
                    applyFlatDamage(blockDamage, currTarget, attacker.isPlayer)
                } else {
                    // Full hit!
                    val slash = attacker.damageSlash * damageFalloff
                    val pierce = attacker.damagePierce * damageFalloff
                    val blunt = attacker.damageBlunt * damageFalloff

                    // Calculate damage reduction based on defender armor
                    // Armor reduces slash and pierce, but blunt damage partially ignores armor
                    val armorFactor = (1f - (currTarget.totalArmor / 100f)).coerceIn(0.1f, 1f)
                    
                    val totalDamage = (slash * armorFactor) + (pierce * (armorFactor + 0.15f).coerceIn(0.1f, 1f)) + blunt
                    
                    applyFlatDamage(totalDamage, currTarget, attacker.isPlayer)

                    // Play hit sounds & comedically yell in latin!
                    if (totalDamage > 0f) {
                        val isCrunch = blunt > 15f && kotlin.random.Random.nextFloat() < 0.4f
                        MedievalAudioSynth.playSound(if (isCrunch) SoundType.CRUNCH else SoundType.THWACK)
                        val strikeType = if (pierce > slash && pierce > blunt) "pierce" else if (slash > blunt) "slash" else "blunt"
                        val hitShout = LatinShouts.getRandomShout(if (isCrunch) SoundType.CRUNCH else SoundType.THWACK, strikeType)
                        addPopup(hitShout, currTarget.posX, 140f, Color(0xFF9E3624))
                    }

                    // Limb loss mechanic! (heavy slash)
                    if (slash > 18f && kotlin.random.Random.nextFloat() < 0.2f && !currTarget.missingArm) {
                        currTarget.missingArm = true
                        // Disarm off-hand/shield logically
                        if (currTarget.isDualWielding || currTarget.shield.id != "shield_none") {
                            currTarget.isDualWielding = false
                            // Find 'shield_none' safely
                            GameData.SHIELDS.find { it.id == "shield_none" }?.let { currTarget.shield = it }
                        }
                        MedievalAudioSynth.playSound(SoundType.THWACK)
                        addPopup("ARM SEVERED!", currTarget.posX, 160f, androidx.compose.ui.graphics.Color.Red)
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
            val shout = LatinShouts.getRandomShout(SoundType.CLANG)
            addPopup(shout, defender.posX, 120f, Color(0xFFB08221))
        } else {
            val armorFactor = (1f - (defender.totalArmor / 100f)).coerceIn(0.15f, 1f)
            val totalDamage = (proj.damage * armorFactor) + (proj.blunt * 0.6f)
            applyFlatDamage(totalDamage, defender, proj.isPlayerOwned)

            if (totalDamage > 0f) {
                val hitShout = LatinShouts.getRandomShout(SoundType.OUCH)
                addPopup(hitShout, defender.posX, 140f, Color(0xFF9E3624))
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
        val finalDmg = dmg.coerceAtLeast(1f).toInt().toFloat()
        defender.hp = (defender.hp - finalDmg).coerceAtLeast(0f)
        defender.damageIndicator = "-${finalDmg.toInt()}"
        defender.damageIndicatorTimer = 0.5f
        
        // Trigger screenshake on hit!
        _screenshake.value = (finalDmg * 1.5f).coerceIn(10f, 35f)
        
        // Spawn blood particles based on damage
        addBloodParticles(defender.posX, 100f, count = (finalDmg / 2).toInt().coerceIn(5, 20))

        if (defender.hp <= 0f) {
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
        val popup = CombatPopup(text, x, y + Random.nextInt(-15, 15), 0f, color)
        _popupsState.value = _popupsState.value + popup
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
            if (won) {
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

                // 2. Weapon Attachment option (attach existing weapon head as dynamic angled secondary blade!)
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
                showLevelUpScreen = showLevelUp
            )
        }

        if (won) {
            MedievalAudioSynth.playSound(SoundType.HUZZAH)
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
                initialGear.add("armor_none")
                initialGear.add("head_none")
                initialGear.addAll(GameData.WEAPON_HEADS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.WEAPON_HANDLES.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.SHIELDS.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.ARMOR_PIECES.shuffled().take(2).map { it.id })
                initialGear.addAll(GameData.HEADGEAR_PIECES.shuffled().take(2).map { it.id })
                
                // Completely random starter gear for the next attempt (each attempt starts fresh and unique!)
                state.copy(
                    isBattleActive = false,
                    battleWon = false,
                    battleLost = false,
                    level = 1,
                    unlockedGearIds = initialGear,
                    extraAttachments = emptyList(),
                    extraArmors = emptyList(),
                    handleExtensionCount = 0,
                    unlockedAncillaries = emptySet(),
                    weaponHead = GameData.WEAPON_HEADS.filter { it.id in initialGear }.random(),
                    weaponHandle = GameData.WEAPON_HANDLES.filter { it.id in initialGear }.random(),
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

    override fun onCleared() {
        gameLoopJob?.cancel()
        super.onCleared()
    }
}

// Utility extension
fun Float.coerceIn(min: Float, max: Float): Float = if (this < min) min else if (this > max) max else this
