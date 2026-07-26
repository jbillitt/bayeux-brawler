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
    @Config(qualifiers = "+w440dp-h2000dp")
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
