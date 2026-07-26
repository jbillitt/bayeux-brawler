package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        viewModel = GameViewModel()
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value
        assertEquals(1, state.level)
        assertFalse(state.isBattleActive)
    }

    @Test
    fun `startBattle triggers battle state`() {
        viewModel.startBattle()
        val state = viewModel.uiState.value
        assertTrue(state.isBattleActive)
    }

    @Test
    fun `startBattle generates enemies without crashing`() {
        // Arrange
        // (ViewModel is already initialized)

        // Act
        // This should invoke generateRandomSaxon inside startBattle.
        // If it throws NoSuchElementException (due to empty filtered gear lists), the test will fail.
        viewModel.startBattle()
        val state = viewModel.uiState.value

        // Assert
        assertTrue("Battle should be active", state.isBattleActive)
        
        val enemies = viewModel.enemiesState.value
        assertTrue("Enemies should be generated", enemies.isNotEmpty())
        
        // Verify the enemy has valid equipment
        val firstEnemy = enemies.first()
        assertTrue("Enemy should have a valid weapon head", firstEnemy.weaponHead.id.isNotEmpty())
        assertTrue("Enemy should have a valid armor", firstEnemy.armor.id.isNotEmpty())
    }

    @Test
    fun `William battle creates loyalist encounter and forces throne music boolean`() {
        mutateState { it.copy(level = 30, isThroneMode = false, brawlMode = true) }

        viewModel.startBattle()

        val state = viewModel.uiState.value
        assertEquals(BossType.WILLIAM_THE_BASTARD, state.bossType)
        assertTrue(state.forceThroneMusic)
        assertTrue(viewModel.enemiesState.value.any { it.bossType == BossType.WILLIAM_THE_BASTARD })
        assertTrue(viewModel.enemiesState.value.any { it.archetype == EnemyArchetype.NORMAN_LOYALIST })
    }

    @Test
    fun `fists preselected never equip a hilt at battle start`() {
        val bareHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" }
        viewModel.selectGear(bareHead)

        viewModel.startBattle()

        val player = viewModel.playerState.value!!
        assertEquals("head_bare", player.weaponHead.id)
        assertEquals("handle_fists", player.weaponHandle.id)
    }

    @Test
    fun `Lil Guy fires a visible player-owned dart without changing damage`() {
        viewModel.startBattle()
        val projectile = createLilGuyDart(viewModel.playerState.value!!, 123L, kotlin.random.Random(7))

        assertEquals(ProjectileType.DART, projectile.type)
        assertEquals(14f, projectile.damage)
        assertTrue(projectile.isPlayerOwned)
    }

    // Reflection helper: the endBattle(won) that flags a run as lost is private, and the
    // random new-attempt loadout (the bug's actual home, ~GameViewModel.kt:1117) only runs
    // from dismissBattleResult() once battleLost is true. There's no public way to lose a
    // battle deterministically, so we invoke the private method directly.
    private fun loseBattle() {
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(viewModel, false)
    }

    @Test
    fun `random loadout after losing never equips a hilt with bare fists`() {
        // The next-attempt gear randomiser picks weaponHead and weaponHandle independently
        // and can roll head_bare with a non-fists handle. Repeat to reliably surface it.
        repeat(50) {
            viewModel.startBattle()
            loseBattle()
            viewModel.dismissBattleResult()

            val state = viewModel.uiState.value
            if (state.weaponHead.id == "head_bare") {
                assertEquals("handle_fists", state.weaponHandle.id)
            }
        }
    }

    @Test
    fun `toggleDualWield changes state correctly`() {
        viewModel.selectGear(com.example.game.GameData.WEAPON_HEADS.first { it.id == "head_sword" })
        val initialState = viewModel.uiState.value.isDualWielding
        viewModel.toggleDualWield()
        val newState = viewModel.uiState.value.isDualWielding
        assertEquals(!initialState, newState)
    }

    private fun spearman() = FighterState(
        id = "trojan_knight_1_0", name = "Trojan Spearman", isPlayer = true,
        maxHp = 45f, hp = 45f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_spear" },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_spangen" },
        posX = 0f, targetX = 0f, size = 0.95f,
        hairColor = androidx.compose.ui.graphics.Color.Black, hairStyle = "short"
    )

    @Test
    fun `panoply arms a fighter in mail and gauntlets`() {
        val f = spearman()
        GameViewModel.applyRetinuePanoply(f)
        assertEquals("armor_chainmail", f.armor.id)
        assertEquals("helm_spangen", f.headgear.id)
        assertTrue(f.extraArmors.any { it.id == "armor_gauntlets" })
    }

    private fun mutateState(mutator: (BattleSimState) -> BattleSimState) {
        val field = GameViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<BattleSimState>
        flow.value = mutator(flow.value)
    }

    private fun setPrivateListFlow(fieldName: String, value: List<*>) {
        val field = GameViewModel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<List<Any>>
        flow.value = value as List<Any>
    }

    private fun updateTransientEffects(dt: Float) {
        val method = GameViewModel::class.java.getDeclaredMethod(
            "updateTransientEffects",
            Float::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(viewModel, dt)
    }

    private fun updateSimulation(dt: Float) {
        val method = GameViewModel::class.java.getDeclaredMethod(
            "updateSimulation",
            Float::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(viewModel, dt)
    }

    private fun foes() = viewModel.enemiesState.value.filter { !it.isPlayer && !it.isDead && !it.isDying }

    private fun endBattle(won: Boolean) {
        val m = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.java)
        m.isAccessible = true
        m.invoke(viewModel, won)
    }

    @Test
    fun `the throne is only a mount option once actually taken`() {
        // Being *offered* the throne is not the same as sitting on it.
        mutateState { it.copy(hasThroneOption = true, isThroneMode = false, hasTakenThrone = false) }
        assertFalse("an unaccepted offer put a throne in the picklist", viewModel.uiState.value.hasTakenThrone)

        viewModel.selectThrone()
        assertTrue(viewModel.uiState.value.hasTakenThrone)

        // Switching to a horse dismounts the throne, but you keep the right to climb back on.
        viewModel.selectMount(Ancillary.WARHORSE)
        assertFalse("should be off the throne", viewModel.uiState.value.isThroneMode)
        assertTrue("throne vanished from the picklist after mounting a horse", viewModel.uiState.value.hasTakenThrone)
    }

    @Test
    fun `a defeated run keeps its score until dismissed, so the shared tale can show it`() {
        viewModel.startBattle()
        mutateState { it.copy(score = 4200) }

        endBattle(won = false)
        // The defeat card — and the share PNG generated from it — is on screen now. It read 0 before.
        assertTrue("battle should be lost", viewModel.uiState.value.battleLost)
        assertEquals("score was wiped before the player could see or share it", 4200, viewModel.uiState.value.score)

        viewModel.dismissBattleResult()
        assertEquals("score should reset once the next run begins", 0, viewModel.uiState.value.score)
    }

    @Test
    fun `divine bolt smites the mightiest foe`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.LIGHTNING)) }
        viewModel.startBattle()
        val mightiest = foes().maxByOrNull { it.hp }!!
        val before = mightiest.hp
        viewModel.triggerWeather("weather_lightning")
        assertTrue("the heavens did nothing", mightiest.hp < before || mightiest.isDying)
    }

    @Test
    fun `the deluge sweeps the furthest foes off the field`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.FLOOD)) }
        viewModel.startBattle()
        val before = foes()
        val furthest = before.maxByOrNull { it.posX }!!
        viewModel.triggerWeather("weather_flood")
        assertTrue("furthest foe was not swept away", furthest.isDying || furthest.isDead)
        assertEquals(DeathType.KNOCKED_FLYING, furthest.deathType)
        assertTrue("swept foe has no wash-away velocity", furthest.velocityX > 0f)
    }

    @Test
    fun `hail batters every foe to the ground`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.HAIL)) }
        viewModel.startBattle()
        val before = foes()
        viewModel.triggerWeather("weather_hail")
        assertTrue("hail left someone standing", before.all { it.isCrumpled && it.crumpleDuration > 0f })
    }

    @Test
    fun `killing frost slows the whole host`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.FROST)) }
        viewModel.startBattle()
        val before = foes()
        viewModel.triggerWeather("weather_frost")
        assertTrue("frost slowed nobody", before.all { it.slowDuration > 0f })
    }

    @Test
    fun `a weather cannot be called twice until it recharges, and battle start recharges it`() {
        mutateState { it.copy(divineWeathers = listOf(DivineWeather.HAIL)) }
        viewModel.startBattle()
        viewModel.triggerWeather("weather_hail")
        assertTrue("no cooldown after use", viewModel.uiState.value.weatherCooldowns["weather_hail"]!! > 0f)

        // Second call must be a no-op: everyone gets back up, and hail does not re-crumple them.
        // Hail also deals damage now, so a low-level host may be entirely dead — only a survivor
        // can demonstrate the no-op.
        val foe = foes().firstOrNull()
        if (foe != null) {
            foe.isCrumpled = false
            foe.crumpleDuration = 0f
            viewModel.triggerWeather("weather_hail")
            assertFalse("hail fired while still cooling down", foe.isCrumpled)
        }

        // Next battle: the charge is full again (startBattle no-ops while one is still running)
        mutateState { it.copy(isBattleActive = false) }
        viewModel.startBattle()
        assertEquals(0f, viewModel.uiState.value.weatherCooldowns["weather_hail"]!!, 0.001f)
    }

    @Test
    fun `divine weather spares queued and climbing defenders`() {
        mutateState {
            it.copy(level = 8, divineWeathers = listOf(DivineWeather.HAIL))
        }
        viewModel.startBattle()
        val protected = foes().take(2)
        assertEquals(2, protected.size)
        protected[0].isCombatInactive = true
        protected[1].climbState = ClimbState.CLIMBING_UP
        val hp = protected.map { it.hp }

        viewModel.triggerWeather("weather_hail")

        assertEquals(hp[0], protected[0].hp, 0f)
        assertEquals(hp[1], protected[1].hp, 0f)
        assertFalse(protected[0].isCrumpled)
        assertFalse(protected[1].isCrumpled)
    }

    @Test
    fun `William throne music flag is cleared across the battle lifecycle`() {
        mutateState { it.copy(level = 30) }
        viewModel.startBattle()
        assertTrue(viewModel.uiState.value.forceThroneMusic)

        endBattle(false)
        assertFalse(viewModel.uiState.value.forceThroneMusic)
        viewModel.dismissBattleResult()
        assertFalse(viewModel.uiState.value.forceThroneMusic)

        mutateState { it.copy(level = 2, forceThroneMusic = true) }
        viewModel.startBattle()
        assertFalse(viewModel.uiState.value.forceThroneMusic)
    }

    @Test
    fun `complete runtime battle descriptor is deterministic for seed and level`() {
        val seed = 1066L
        val level = (5..15).first {
            BossSchedule.forLevel(it) == null && !SiegeSchedule.isSiegeLevel(seed, it)
        }
        fun descriptor(vm: GameViewModel): List<Any> {
            val state = vm.uiState.value
            return listOf(
                state.levelWidth,
                state.siegeState != null,
                vm.enemiesState.value.map {
                    listOf(
                        it.id, it.archetype, it.weaponHead.id, it.weaponHandle.id,
                        it.shield.id, it.armor.id, it.headgear.id, it.size, it.posX
                    )
                },
                state.backgroundObjects.map {
                    listOf(it.id, it.type, it.posX, it.width, it.hp, it.maxHp, it.seed, it.artId)
                }
            )
        }
        fun configured(): GameViewModel {
            MedievalHarpPlayer.newGame(seed)
            return GameViewModel().also { vm ->
                val field = GameViewModel::class.java.getDeclaredField("_uiState")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<BattleSimState>
                flow.value = flow.value.copy(
                    level = level,
                    performanceScore = 0.5f,
                    unlockedAncillaries = emptyList(),
                    headgear = GameData.HeadgearPiece.NONE
                )
                vm.startBattle()
            }
        }

        assertEquals(descriptor(configured()), descriptor(configured()))
    }

    @Test
    fun `enemy torch collision consumes the shot without harming defender cover`() {
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        player.posX = 550f
        player.targetX = 550f
        val building = BackgroundObject(
            id = "defender_cover",
            type = BackgroundObjectType.BUILDING_BOSHAM,
            posX = 500f,
            width = 300f,
            hp = 300f,
            maxHp = 300f
        )
        mutateState { it.copy(backgroundObjects = listOf(building), levelWidth = 1500f) }
        setPrivateListFlow(
            "_projectilesState",
            listOf(
                Projectile(
                    id = "torch_test",
                    isPlayerOwned = false,
                    posX = 500f,
                    posY = 200f,
                    velocityX = 0f,
                    velocityY = 0f,
                    damage = 50f,
                    pierce = 0f,
                    blunt = 2f,
                    type = ProjectileType.TORCH
                )
            )
        )

        updateSimulation(0.001f)

        assertEquals(300f, building.hp, 0f)
        assertTrue(viewModel.projectilesState.value.none { it.id == "torch_test" })
    }

    @Test
    fun `weather is only offered late and never a third time`() {
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true

        // Too early: level 5 must never offer it
        mutateState { it.copy(level = 5, divineWeathers = emptyList()) }
        repeat(40) {
            method.invoke(viewModel, true)
            assertFalse(
                "weather offered below level $12",
                viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }
            )
            mutateState { it.copy(level = 5) }
        }

        // Already holding two: never offer a third
        mutateState { it.copy(level = 20, divineWeathers = listOf(DivineWeather.HAIL, DivineWeather.FROST)) }
        repeat(40) {
            method.invoke(viewModel, true)
            assertFalse(
                "a third weather was offered",
                viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }
            )
            mutateState { it.copy(level = 20) }
        }

        // Late and holding none: it shows up eventually
        mutateState { it.copy(level = 20, divineWeathers = emptyList()) }
        var offered = false
        repeat(80) {
            method.invoke(viewModel, true)
            if (viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.type == "weather" }) offered = true
            mutateState { it.copy(level = 20, divineWeathers = emptyList()) }
        }
        assertTrue("weather never offered at level 20", offered)
    }

    @Test
    fun `the out card is offered once its counter has been met, and not before`() {
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true

        // Never met a shield wall: no Shieldbreaker on offer
        mutateState { it.copy(level = 14, seenCounters = emptySet(), hasShieldbreaker = false) }
        repeat(20) {
            method.invoke(viewModel, true)
            assertFalse(
                "Shieldbreaker offered before the player ever saw a shield wall",
                viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.id == "counter_shieldbreaker" }
            )
            mutateState { it.copy(level = 14, seenCounters = emptySet(), hasShieldbreaker = false) }
        }

        // Met one: the answer is always in the pool
        mutateState { it.copy(level = 14, seenCounters = setOf(EnemyFactory.COUNTER_SHIELD_WALL), hasShieldbreaker = false) }
        method.invoke(viewModel, true)
        assertTrue(
            "Shieldbreaker never offered after meeting a shield wall",
            viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.id == "counter_shieldbreaker" }
        )

        // Already holding it: never offered again
        mutateState { it.copy(level = 14, seenCounters = setOf(EnemyFactory.COUNTER_SHIELD_WALL), hasShieldbreaker = true) }
        method.invoke(viewModel, true)
        assertFalse(
            "Shieldbreaker offered to a player who already has it",
            viewModel.uiState.value.pendingLevelUpChoices.any { c -> c.id == "counter_shieldbreaker" }
        )
    }

    @Test
    fun `taking the out card sets the flag the engine reads`() {
        val card = LevelUpChoice("counter_armor_piercing", "Armour-Piercing Stitch", "", "counter", "counter_armor_piercing")
        mutateState { it.copy(pendingLevelUpChoices = listOf(card)) }
        viewModel.selectLevelUpChoice("counter_armor_piercing")
        assertTrue(viewModel.uiState.value.hasArmorPiercing)
    }

    @Test
    fun `Thrice-Blessed never reoffers a consumed follower`() {
        mutateState {
            it.copy(
                level = 8,
                unlockedAncillaries = listOf(Ancillary.SQUIRE),
                tripledFollowerIds = setOf(Ancillary.SQUIRE.id)
            )
        }
        repeat(100) {
            endBattle(true)
            assertFalse(
                "consumed follower was offered again",
                viewModel.uiState.value.pendingLevelUpChoices.any { choice ->
                    choice.id == "triple_${Ancillary.SQUIRE.id}"
                }
            )
            mutateState { it.copy(level = 8) }
        }
    }

    @Test
    fun `Thrice-Blessed weighting favors an existing twin three to one`() {
        val followers = listOf(Ancillary.SQUIRE, Ancillary.SQUIRE, Ancillary.HERALD)
        val random = kotlin.random.Random(42)
        var squirePicks = 0
        var heraldPicks = 0
        repeat(1000) {
            when (pickTripleCandidate(followers, emptySet(), random)) {
                Ancillary.SQUIRE -> squirePicks++
                Ancillary.HERALD -> heraldPicks++
                else -> {}
            }
        }
        val ratio = squirePicks.toFloat() / heraldPicks
        assertTrue("weighted ratio was $ratio", ratio in 2.5f..3.5f)
    }

    @Test
    fun `rally produces twins fifteen percent of the time`() {
        val random = kotlin.random.Random(99)
        val twinRate = (0 until 1000).count { rollFollowerCopies(random) == 2 } / 1000f
        assertTrue("twins rate was $twinRate", twinRate in 0.10f..0.20f)
    }

    @Test
    fun `front pallbearers inherit the lords full upgraded kit`() {
        val sword = GameData.WEAPON_HEADS.first { it.id == "head_sword" }
        val handle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" }
        val shield = GameData.SHIELDS.first { it.id != "shield_none" }
        mutateState {
            it.copy(
                isThroneMode = true,
                weaponHead = sword,
                weaponHandle = handle,
                shield = shield,
                extraAttachments = listOf("head_axe"),
                handleExtensionCount = 2,
                rangedUpgrades = listOf("multishot"),
                shieldUpgrades = listOf("oak_reinforcing", "iron_plating"),
                brawlerUpgrades = listOf("brass_knuckles"),
                isDualWielding = false
            )
        }

        viewModel.startBattle()

        val player = viewModel.playerState.value!!
        val bearers = viewModel.enemiesState.value
            .filter { it.id.startsWith("pallbearer_") }
            .sortedBy { it.pallbearerIndex }
        for (front in bearers.take(2)) {
            assertEquals(listOf("head_axe"), front.extraAttachments.map { it.id })
            assertEquals(2, front.handleExtensionCount)
            assertEquals(listOf("multishot"), front.rangedUpgrades)
            assertEquals(listOf("oak_reinforcing", "iron_plating"), front.shieldUpgrades)
            assertEquals(listOf("brass_knuckles"), front.brawlerUpgrades)
        }
        assertEquals(player.shieldHp, bearers[1].shieldHp, 0f)
        for (rear in bearers.drop(2)) {
            assertEquals("head_bare", rear.weaponHead.id)
            assertEquals("handle_fists", rear.weaponHandle.id)
            assertEquals("shield_none", rear.shield.id)
            assertTrue(rear.extraAttachments.isEmpty())
            assertEquals("armor_bare", rear.armor.id)
        }
    }

    @Test
    fun `chariot collapses if armor is too heavy`() {
        // headgear must be pinned: BattleSimState defaults it to a *random* piece (0-6kg),
        // which silently decided whether this loadout crossed the weight limit
        mutateState { it.copy(
            unlockedAncillaries = listOf(Ancillary.CHARIOT),
            // Owning a mount no longer equips it — activeMount is the source of truth now.
            activeMount = Ancillary.CHARIOT,
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif")
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertFalse(player.isChariot)
        val bgObjects = viewModel.uiState.value.backgroundObjects
        assertTrue(bgObjects.any { it.type == BackgroundObjectType.BROKEN_CHARIOT })
    }

    @Test
    fun `silken garments prevents chariot collapse`() {
        mutateState { it.copy(
            unlockedAncillaries = listOf(Ancillary.CHARIOT),
            // Owning a mount no longer equips it — activeMount is the source of truth now.
            activeMount = Ancillary.CHARIOT,
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif"),
            hasSilkenGarments = true
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertTrue(player.isChariot)
    }

    @Test
    fun `silken_garments appears in level up choices if over limit`() {
        mutateState { it.copy(
            armor = GameData.ARMOR_PIECES.first { a -> a.id == "armor_scale" },
            headgear = GameData.HEADGEAR_PIECES.first { h -> h.id == "helm_none" },
            extraArmors = listOf("armor_gauntlets", "armor_boots", "armor_coif"),
            level = 5
        )}
        val method = GameViewModel::class.java.getDeclaredMethod("endBattle", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        var found = false
        repeat(50) {
            method.invoke(viewModel, true)
            println("CHOICES: " + viewModel.uiState.value.pendingLevelUpChoices.map { it.id })
            val choices = viewModel.uiState.value.pendingLevelUpChoices
            if (choices.any { it.id == "silken_garments" }) {
                found = true
            }
            viewModel.selectLevelUpChoice(choices.first().id)
        }
        assertTrue(found)
    }

    @Test
    fun `battle starts with selected mount when multiple mounts unlocked`() {
        mutateState { it.copy(
            unlockedAncillaries = listOf(Ancillary.CHARIOT, Ancillary.WARHORSE),
            activeMount = Ancillary.WARHORSE
        )}
        viewModel.startBattle()
        val player = viewModel.playerState.value!!
        assertFalse(player.isChariot)
        assertTrue(player.isMounted)
        assertEquals(80f, player.mountHp)
    }

    @Test
    fun `transient effect tick preserves list identity when membership is unchanged`() {
        val popup = CombatPopup("10", 5f, 6f, age = 0.2f)
        val particle = BloodParticle(1f, 2f, 3f, 4f, age = 0.2f, maxAge = 10f)
        val popups = listOf(popup)
        val particles = listOf(particle)
        setPrivateListFlow("_popupsState", popups)
        setPrivateListFlow("_particlesState", particles)

        updateTransientEffects(0.033f)

        assertSame(popups, viewModel.popupsState.value)
        assertSame(particles, viewModel.particlesState.value)
        assertEquals(0.233f, popup.age, 0.0001f)
        assertEquals(0.233f, particle.age, 0.0001f)
    }

    @Test
    fun `transient effect tick removes expired entries and caps spawned particles`() {
        val expiringPopup = CombatPopup("10", 5f, 6f, age = 1.19f)
        val expiringParticle = BloodParticle(1f, 2f, 3f, 4f, age = 0.99f, maxAge = 1f)
        val popups = listOf(expiringPopup)
        val particles = listOf(expiringParticle)
        setPrivateListFlow("_popupsState", popups)
        setPrivateListFlow("_particlesState", particles)

        val bufferField = GameViewModel::class.java.getDeclaredField("particleBuffer")
        bufferField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val buffer = bufferField.get(viewModel) as MutableList<BloodParticle>
        val spawned = List(125) { index ->
            BloodParticle(index.toFloat(), 0f, 0f, 0f, maxAge = 10f)
        }
        buffer.addAll(spawned)

        updateTransientEffects(0.02f)

        assertNotSame(popups, viewModel.popupsState.value)
        assertNotSame(particles, viewModel.particlesState.value)
        assertTrue(viewModel.popupsState.value.isEmpty())
        assertEquals(120, viewModel.particlesState.value.size)
        assertEquals(spawned.takeLast(120), viewModel.particlesState.value)
        assertTrue(buffer.isEmpty())
    }
}
