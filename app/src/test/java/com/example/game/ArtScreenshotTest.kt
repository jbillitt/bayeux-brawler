package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the game's own draw functions to PNGs so the art can be looked at without a device.
 *
 * These are NOT golden-image assertions — nothing fails on a pixel diff. They exist so a human can
 * open `app/src/test/screenshots/` and see what the renderer actually draws. The vector builder
 * cannot do this job: its parser only understands literal moveTo/lineTo coordinates, so it renders
 * nothing at all for procedural (looped, seeded) art.
 *
 * Run just these: gradle testDebugUnitTest --tests "*ArtScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class ArtScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val linen = Color(0xFFF1E6CC)

    @Before
    fun loadArt() {
        // The JSON-backed buildings draw nothing without this
        VectorAsset.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    private fun bgObject(type: BackgroundObjectType, seed: Int = 1) = BackgroundObject(
        id = "shot_${type.name}_$seed",
        type = type,
        posX = 0f,
        width = 300f,
        hp = 500f,
        maxHp = 500f,
        seed = seed
    )

    /** The procedural pair, several seeds each — "no two identical" is the actual requirement. */
    @Test
    fun proceduralBuildings() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(BackgroundObjectType.BUILDING_BAYEUX, BackgroundObjectType.TOWER_SPIRAL).forEach { type ->
                    listOf(listOf(11, 29), listOf(404, 1066)).forEach { seedRow ->
                        Row {
                            seedRow.forEach { seed ->
                                Canvas(modifier = Modifier.width(206.dp).height(200.dp)) {
                                    drawBackgroundObject(this, bgObject(type, seed), size.width / 2f, 0.45f)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/procedural.png")
    }

    // The set has outgrown a phone screen — the last rows were being clipped away unrendered.
    @Test
    // Tall enough for every row: two more object types pushed the last rows off the bottom, so
    // the harness was quietly not rendering the things it existed to show.
    @Config(qualifiers = "+w440dp-h2400dp")
    fun buildings() {
        val types = BackgroundObjectType.values().toList()
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                types.chunked(2).forEach { row ->
                    Row {
                        row.forEach { type ->
                            Canvas(modifier = Modifier.width(206.dp).height(150.dp)) {
                                drawBackgroundObject(this, bgObject(type), size.width / 2f, 0.5f)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/buildings.png")
    }

    @Test
    fun newContentBackdropsAndGateDamageStates() {
        val backdrops = listOf(
            bgObject(BackgroundObjectType.CASTLE_WALL),
            bgObject(BackgroundObjectType.MOTTE),
            bgObject(BackgroundObjectType.FEASTING_HALL),
            bgObject(BackgroundObjectType.FLEET_CROSSING),
            bgObject(BackgroundObjectType.MONT_SAINT_MICHEL),
            bgObject(BackgroundObjectType.CASTLE_GATE).copy(hp = 500f, maxHp = 500f),
            bgObject(BackgroundObjectType.CASTLE_GATE).copy(hp = 250f, maxHp = 500f),
            bgObject(BackgroundObjectType.CASTLE_GATE).copy(hp = 100f, maxHp = 500f),
            bgObject(BackgroundObjectType.CASTLE_GATE).copy(hp = 0f, maxHp = 500f)
        )
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                backdrops.chunked(3).forEach { row ->
                    Row {
                        row.forEach { bg ->
                            Canvas(modifier = Modifier.width(137.dp).height(150.dp)) {
                                drawBackgroundObject(this, bg, size.width / 2f, 0.42f)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/new_backdrops.png")
    }

    @Test
    fun liveSiegeGateAndLadder() {
        val gate = bgObject(BackgroundObjectType.CASTLE_GATE)
        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(500f, 250f, 100f, 0f).forEach { hp ->
                    Canvas(modifier = Modifier.width(103.dp).height(260.dp)) {
                        val x = size.width / 2f
                        drawLiveCastleGate(this, gate, x, 0.55f, hp, 500f)
                        if (hp == 0f) drawSiegeLadder(this, x, 0.55f)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/live_siege.png")
    }

    /** The mounts, at a small size and a normal one — a little man in a chariot draws a second box. */
    @Test
    fun mounts() {
        fun rider(id: String, size: Float, chariot: Boolean = false, stilts: Boolean = false, crown: Boolean = false) =
            FighterState(
                id = FighterId(id), name = id, isPlayer = true, maxHp = 100f, hp = 100f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == if (crown) "helm_crown" else "helm_none" },
                posX = 0f, targetX = 0f, facingRight = true,
                size = size,
                hairColor = Color(0xFF5A442E), hairStyle = "short",
                isMounted = true,
                isChariot = chariot,
                isStilts = stilts
            )

        val cast = listOf(
            rider("small_chariot", 0.65f, chariot = true),
            rider("big_chariot", 1.3f, chariot = true),
            rider("small_stilts", 0.65f, stilts = true),
            rider("normal_stilts", 1.0f, stilts = true),
            rider("large_stilts", 1.4f, stilts = true),
            rider("small_horse", 0.65f),
            rider("crowned", 1.0f, crown = true)
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { f ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, f, scale = 0.8f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mounts.png")
    }

    @Test
    fun fighters() {
        fun f(
            id: String,
            armor: String = "armor_bare",
            head: String = "head_bare",
            handle: String = "handle_fists",
            warPaint: Int = 0,
            contagious: Boolean = false,
            priest: Boolean = false
        ) = FighterState(
            id = FighterId(id), name = id, isPlayer = false,
            maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == armor },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 0f, targetX = 0f, facingRight = true,
            size = 1.0f,
            hairColor = Color(0xFF5A442E),
            hairStyle = "short",
            warPaint = warPaint,
            isContagious = contagious,
            isWarPriest = priest
        )

        val cast = listOf(
            f("plague_peasant", contagious = true),
            f("war_priest_0", priest = true),
            f("saxon_woad_clothed", armor = "armor_padded", head = "head_sword", handle = "handle_medium", warPaint = 1),
            f("saxon_woad_bare", warPaint = 1),
            f("brawler_bare", armor = "armor_bare"),
            f("saxon_mail", armor = "armor_chainmail", head = "head_axe", handle = "handle_medium")
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { fighter ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, fighter, scale = 1.0f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/fighters.png")
    }

    /**
     * The seven new handles, each carrying an axe head, so a human can confirm the head sockets
     * onto the haft. Oar, femur and plank ride the generic straight-haft path; antler, trumpet,
     * wheelbarrow and anchor each have a bespoke render block. Asserts nothing — eyeball only.
     */
    @Test
    fun newHandles() {
        fun withHandle(handle: String) = FighterState(
            id = FighterId(handle), name = handle, isPlayer = false,
            maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_axe" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            // posX offsets the figure into its canvas; at 0 the left column draws half off-screen.
            posX = 90f, targetX = 90f, facingRight = true, size = 1.0f,
            hairColor = Color(0xFF5A442E), hairStyle = "short"
        )

        val cast = GameData.UNLOCKABLE_HANDLE_IDS.sorted().map { withHandle(it) }

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { fighter ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, fighter, scale = 1.0f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/new_handles.png")
    }

    /**
     * Every weapon head on the same medium haft. The haft runs at ~26.6 degrees above horizontal,
     * so this is the view that shows a head built on the screen axis instead of the haft's — it
     * reads as a kink where the head meets the pole. Asserts nothing — eyeball only.
     */
    // A phone screen fits 2 columns; 35 heads at a readable size need a bigger canvas, so this
    // one test widens the virtual device rather than shrinking the art.
    @Test
    @Config(qualifiers = "+w1100dp-h1400dp")
    fun weaponHeads() {
        fun withHead(head: String) = FighterState(
            id = FighterId(head), name = head, isPlayer = false,
            maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 90f, targetX = 90f, facingRight = true, size = 1.0f,
            hairColor = Color(0xFF5A442E), hairStyle = "short"
        )

        val cast = GameData.WEAPON_HEADS.map { withHead(it.id) }

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(5).forEach { row ->
                    Row {
                        row.forEach { fighter ->
                            Canvas(modifier = Modifier.width(206.dp).height(190.dp)) {
                                TapestryRenderer.drawCharacter(this, fighter, scale = 1.0f, isBattleActive = true)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/weapon_heads.png")
    }

    /**
     * The beasts of the retinue, bare-headed and in the "Arm the Retinue" spangenhelm. Eyeball that
     * each helm sits ON the skull — the dog's above the snout and behind the ear, the raven's over
     * the crown and clear of the beak — rather than floating beside it.
     */
    @Test
    @Config(qualifiers = "+w1100dp-h700dp")
    fun beastsWithAndWithoutPanoplyHelms() {
        fun beast(kind: String, helm: String) = FighterState(
            id = FighterId("$kind#0"), name = kind, isPlayer = true,
            maxHp = 60f, hp = 60f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
            posX = 100f, targetX = 100f, facingRight = true,
            size = if (kind == "raven") 0.85f else 0.6f,
            hairColor = Color.Transparent, hairStyle = "none"
        )

        val cast = listOf(
            beast("wardog", "helm_none"), beast("wardog", "helm_spangen"),
            beast("raven", "helm_none"), beast("raven", "helm_spangen")
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { fighter ->
                            Canvas(modifier = Modifier.width(520.dp).height(330.dp)) {
                                // Zoomed hard: a wardog's helm is ~11px across at play scale,
                                // which is far too small to judge placement from. The raven is
                                // drawn 120px above the ground line, so its cell is shifted down
                                // to keep the bird inside its own canvas.
                                val lift = if (fighter.isKind("raven")) 300f else 0f
                                translate(top = lift) {
                                    TapestryRenderer.drawCharacter(this, fighter, scale = 3.0f, isBattleActive = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/beast_helms.png")
    }

    /**
     * The Moleman beside a plain bare-chested levy, since bare skin is all they used to have to tell
     * them apart. Eyeball the pelt reaching collar to hem and a claw off each hand.
     */
    @Test
    @Config(qualifiers = "+w1100dp-h700dp")
    fun molemanAgainstAPlainBareChest() {
        fun bare(id: String) = FighterState(
            id = FighterId(id), name = id, isPlayer = true, maxHp = 260f, hp = 260f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 100f, targetX = 100f, facingRight = true, size = 1.15f,
            hairColor = Color(0xFF3A2E24), hairStyle = "long"
        )
        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(bare("moleman#0"), bare("brawler#0")).forEach { fighter ->
                    Canvas(modifier = Modifier.width(520.dp).height(700.dp)) {
                        TapestryRenderer.drawCharacter(this, fighter, scale = 2.4f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/moleman.png")
    }

    /**
     * Every stage of the Gaping Frog's attack, left to right: mouth shut and hunting, tongue half
     * out, tongue fully stuck to its victim, reeling back in, and dead on its back.
     *
     * Asserts nothing — this is the eyeball check that the tongue leaves the mouth and not the
     * belly, that the gape reads as a gape, and that a dead frog is obviously dead.
     */
    @Test
    fun giantFrogTongueStages() {
        fun frog(id: String, extend: Float, dying: Boolean = false) = FighterState(
            id = FighterId(id), name = "The Gaping Frog", isPlayer = false, maxHp = 900f, hp = 900f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_bare" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 170f, targetX = 170f, facingRight = true, size = 1.4f,
            hairColor = Color.Transparent, hairStyle = "none",
            archetype = EnemyArchetype.GIANT_FROG,
            tongueExtend = extend,
            isDying = dying, animFrame = if (dying) 6f else 1.2f
        )
        // Stacked, not side by side: the harness surface is only ~360.dp wide, so five panels in a
        // Row put three of them off the edge of the image entirely.
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(
                    frog("frog_shut", 0f),
                    frog("frog_half", 0.5f),
                    frog("frog_full", 1f),
                    frog("frog_reel", 0.75f),
                    frog("frog_dead", 0f, dying = true)
                ).forEach { f ->
                    Canvas(modifier = Modifier.width(360.dp).height(150.dp)) {
                        TapestryRenderer.drawCharacter(this, f, scale = 0.8f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/giant_frog.png")
    }

    /**
     * The overarm throw, through the swing, for both things that use it: a javelin and a bomb.
     * Top row is the javelin, bottom the bomb, each at rest / cocked / mid-whip / follow-through.
     *
     * Eyeball that the arm goes BACK before it goes forward, that the bomb reads as a lit clay pot
     * and not a mace head, and that neither of them looks like a sword swing.
     */
    @Test
    fun overarmThrowStages() {
        fun thrower(head: String, swing: Float, attacking: Boolean) = FighterState(
            id = FighterId("$head-$swing"), name = "Thrower", isPlayer = true, maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_short" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 150f, targetX = 150f, facingRight = true, size = 1f,
            hairColor = Color(0xFF3A2E24), hairStyle = "short",
            isAttacking = attacking, swingProgress = swing
        )
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf("head_javelin", "head_bomb").forEach { head ->
                    Row {
                        listOf(0f to false, 0.4f to true, 0.6f to true, 0.95f to true)
                            .forEach { (swing, attacking) ->
                                Canvas(modifier = Modifier.width(88.dp).height(380.dp)) {
                                    TapestryRenderer.drawCharacter(
                                        this, thrower(head, swing, attacking),
                                        scale = 1.1f, isBattleActive = true
                                    )
                                }
                            }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/overarm_throw.png")
    }

    /**
     * Polyphemus beside Gog, and the meal in progress: a follower lifted off the ground the way
     * tickCyclops holds him.
     *
     * Eyeball that the cyclops has ONE eye and no second eyebrow across it, that he is visibly
     * bigger than the other giant, and that the held man reads as held rather than as floating.
     */
    @Test
    fun cyclopsAndHisMeal() {
        // createBoss parks them at 1250f, which is off the side of a harness canvas.
        val cyclops = EnemyFactory.createBoss(BossType.POLYPHEMUS, 60).copy(posX = 150f, targetX = 150f)
        val gog = EnemyFactory.createBoss(BossType.GOG, 40).copy(posX = 130f, targetX = 130f)
        val held = FighterState(
            id = FighterId("held"), name = "Wretched Aldwin", isPlayer = true, maxHp = 60f, hp = 22f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 150f, targetX = 150f, facingRight = false, size = 1f,
            hairColor = Color(0xFF3A2E24), hairStyle = "short",
            crumpleDuration = 0.5f, visualOffsetY = -150f
        )
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                Row {
                    // Scale is low because these two are drawn 3x a man's height and the head runs
                    // off the top of the canvas otherwise. This row is for the SILHOUETTE — that
                    // the cyclops is obviously the bigger of the two giants.
                    Canvas(modifier = Modifier.width(190.dp).height(200.dp)) {
                        TapestryRenderer.drawCharacter(this, cyclops, scale = 0.26f, isBattleActive = true)
                    }
                    Canvas(modifier = Modifier.width(150.dp).height(200.dp)) {
                        TapestryRenderer.drawCharacter(this, gog, scale = 0.26f, isBattleActive = true)
                    }
                }
                Row {
                    // Same fighter shrunk to man-size so the FACE is legible: one eye, one brow,
                    // no second eyebrow drawn across it. At giant scale the head is 30px and the
                    // eye cannot be checked at all.
                    Canvas(modifier = Modifier.width(200.dp).height(420.dp)) {
                        TapestryRenderer.drawCharacter(
                            this,
                            cyclops.copy(size = 1f, posX = 95f, facingRight = true),
                            scale = 1.9f, isBattleActive = true
                        )
                    }
                    Canvas(modifier = Modifier.width(150.dp).height(420.dp)) {
                        TapestryRenderer.drawCharacter(this, held, scale = 1.1f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/cyclops.png")
    }

    /**
     * The three creatures that climb down out of the margins from level 35: the headless Blemmya
     * with his face in his chest, Reynard the armed fox, and the killer rabbit.
     *
     * Eyeball that the Blemmya has NO head above the shoulders and a legible face on the torso,
     * that the fox reads as a fox and not a dog, and that the rabbit's ears are unmistakable at
     * the size he actually fights at.
     */
    @Test
    fun marginaliaCreatures() {
        fun beast(arch: EnemyArchetype) =
            EnemyFactory.createArchetype(arch, 0, level = 40)
                .copy(posX = 120f, targetX = 120f, facingRight = true)
        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxSize().background(linen)) {
                listOf(
                    EnemyArchetype.BLEMMYA,
                    EnemyArchetype.CRAFTY_FOX,
                    EnemyArchetype.KILLER_RABBIT
                ).forEach { arch ->
                    Canvas(modifier = Modifier.width(118.dp).height(520.dp)) {
                        TapestryRenderer.drawCharacter(this, beast(arch), scale = 1.5f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/marginalia.png")
    }

    /**
     * The three new armour layers, each stacked over chainmail the way they are worn in play, plus
     * all three at once. Asserts nothing — eyeball that greaves sit on the shins, spaulders cap the
     * shoulders without floating, and the surcoat hangs over the mail without hiding it.
     */
    @Test
    fun newArmourLayers() {
        fun layered(id: String, layers: List<String>) = FighterState(
            id = FighterId(id), name = id, isPlayer = false, maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
            posX = 90f, targetX = 90f, facingRight = true, size = 1.0f,
            hairColor = Color(0xFF5A442E), hairStyle = "short",
            extraArmors = layers.map { l -> GameData.ARMOR_PIECES.first { it.id == l } }
        )

        val cast = listOf(
            layered("greaves", listOf("armor_greaves")),
            layered("spaulders", listOf("armor_spaulders")),
            layered("surcoat", listOf("armor_surcoat")),
            layered("all_three", listOf("armor_greaves", "armor_spaulders", "armor_surcoat"))
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                // Rendered large: greaves and spaulders are small plates and cannot be judged at 1.0.
                cast.forEach { fighter ->
                    Canvas(modifier = Modifier.width(412.dp).height(300.dp)) {
                        TapestryRenderer.drawCharacter(this, fighter, scale = 2.2f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/new_armour_layers.png")
    }

    /**
     * The three earned mounts, large enough to judge. Asserts nothing — eyeball that the rider sits
     * on the saddle rather than floating above or sunk into the beast, and that legs reach the ground.
     */
    @Test
    fun newMounts() {
        fun rider(id: String, ox: Boolean = false, mule: Boolean = false, bear: Boolean = false) =
            FighterState(
                id = FighterId(id), name = id, isPlayer = true, maxHp = 100f, hp = 100f,
                weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_sword" },
                weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_medium" },
                shield = GameData.SHIELDS.first { it.id == "shield_none" },
                armor = GameData.ARMOR_PIECES.first { it.id == "armor_chainmail" },
                headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
                posX = 150f, targetX = 150f, facingRight = true, size = 1.0f,
                hairColor = Color(0xFF5A442E), hairStyle = "short",
                isMounted = true, isOx = ox, isMule = mule, isBear = bear,
                animFrame = 1.2f // mid-stride, so the walk cycle is visible
            )

        val cast = listOf(
            rider("war_ox", ox = true),
            rider("pack_mule", mule = true),
            rider("war_bear", bear = true),
            rider("horse_for_comparison")
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.forEach { f ->
                    Canvas(modifier = Modifier.width(412.dp).height(230.dp)) {
                        TapestryRenderer.drawCharacter(this, f, scale = 1.3f, isBattleActive = true)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/new_mounts.png")
    }

    /** The four earned hats and four earned hairstyles. Asserts nothing — eyeball fit on the skull. */
    // Eleven heads no longer fit a phone screen; the last rows were clipped away entirely.
    @Test
    @Config(qualifiers = "+w900dp-h1400dp")
    fun newHatsAndHair() {
        fun head(id: String, helm: String = "helm_none", hair: String = "short") = FighterState(
            id = FighterId(id), name = id, isPlayer = true, maxHp = 100f, hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first { it.id == "head_bare" },
            weaponHandle = GameData.WEAPON_HANDLES.first { it.id == "handle_fists" },
            shield = GameData.SHIELDS.first { it.id == "shield_none" },
            armor = GameData.ARMOR_PIECES.first { it.id == "armor_padded" },
            headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
            posX = 110f, targetX = 110f, facingRight = true, size = 1.0f,
            hairColor = Color(0xFF5A442E), hairStyle = hair
        )

        val cast = listOf(
            head("antlered", helm = "helm_antlered"),
            head("winged", helm = "helm_winged"),
            head("wolf", helm = "helm_wolf"),
            head("pot", helm = "helm_pot"),
            head("hair_norman", hair = "hair_tonsure_norman"),
            head("hair_braids", hair = "hair_braids"),
            head("hair_monk", hair = "hair_tonsure_monk"),
            head("hair_topknot", hair = "hair_topknot"),
            head("hair_mystic", hair = "hair_mystic"),
            head("hair_germanic", hair = "hair_germanic"),
            head("hair_samson", hair = "hair_samson")
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(linen)) {
                cast.chunked(2).forEach { row ->
                    Row {
                        row.forEach { f ->
                            Canvas(modifier = Modifier.width(206.dp).height(150.dp)) {
                                TapestryRenderer.drawCharacter(this, f, scale = 1.9f, isBattleActive = false)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/new_hats_and_hair.png")
    }

    @Test
    fun productionScaleRendererProducesInk() {
        val monk = EnemyFactory.createArchetype(EnemyArchetype.MONK_MILITIA, 0, 10)
            .copy(posX = 260f, targetX = 260f)
        val hardrada = EnemyFactory.createBoss(BossType.HARALD_HARDRADA, 20)
            .copy(posX = 720f, targetX = 720f, isCombatInactive = false)
        val bridge = bgObject(BackgroundObjectType.STAMFORD_BRIDGE)

        val bitmap = ImageBitmap(1000, 350)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(bitmap),
            size = Size(1000f, 350f)
        ) {
            drawRect(linen)
            val productionScale = size.height / 350f
            drawStaticBackgroundObject(this, bridge, size.width / 2f, productionScale)
            TapestryRenderer.drawCharacter(this, monk, productionScale)
            TapestryRenderer.drawCharacter(this, hardrada, productionScale)
        }

        val pixels = bitmap.toPixelMap()
        var inkSamples = 0
        for (y in 0 until pixels.height step 4) {
            for (x in 0 until pixels.width step 4) {
                if (pixels[x, y] != linen) inkSamples++
            }
        }
        assertTrue("production renderer must paint non-linen pixels", inkSamples > 500)
    }
}
