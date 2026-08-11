package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * The trailer's closing card, at 1920x1080.
 *
 * Everything on it is the game's own renderer — the fallen are real `FighterState`s drawn by
 * `TapestryRenderer.drawCharacter`, so their gear, their blood pools and their death poses are the
 * ones the game actually produces. The title is real text converted to a Path and run through
 * `drawStitchedFill`, which clips couching stitches inside it: woven, not merely outlined.
 *
 * Landscape mdpi so one dp is one pixel and the PNG comes out at exactly 1920x1080.
 *
 * Run just this: gradle testDebugUnitTest --tests "*SplashScreenshotTest*" -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1920dp-h1080dp-land-mdpi", sdk = [34])
class SplashScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val linen = Color(0xFFF1E6CC)
    private val oxblood = Color(0xFF6E1414)
    private val titleRed = Color(0xFF9A3324)

    @Before
    fun loadArt() {
        VectorAsset.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    private fun fallen(
        tag: String, head: String, handle: String, shield: String, armor: String, helm: String,
        death: DeathType, facing: Boolean, sz: Float
    ) = FighterState(
        id = FighterId("fallen_$tag"), name = "Fallen", isPlayer = false, maxHp = 100f, hp = 0f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
        shield = GameData.SHIELDS.first { it.id == shield },
        armor = GameData.ARMOR_PIECES.first { it.id == armor },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
        posX = 150f, targetX = 150f, facingRight = facing, size = sz,
        hairColor = Color(0xFF3A2E24), hairStyle = "short",
        isDead = true, deathType = death, animFrame = 6f
    )

    /**
     * The narrow worked band the real artefact runs along its top and bottom edge.
     *
     * Every line goes through [drawStitchedOutline] rather than drawLine, so it picks up the same
     * wandering wool edge as everything else in the game. Ruled straight lines were the tell that
     * the border was drawn by a machine while the men above it were sewn by a person.
     */
    private fun border(scope: DrawScope, y: Float, w: Float) {
        val thread = Color(0xFF2C2219)
        val rules = Path().apply {
            moveTo(0f, y); lineTo(w, y)
            moveTo(0f, y + 38f); lineTo(w, y + 38f)
        }
        val zig = Path().apply {
            moveTo(0f, y + 32f)
            var x = 0f
            var up = true
            while (x < w) {
                x += 26f
                lineTo(x, if (up) y + 6f else y + 32f)
                up = !up
            }
        }
        // Couching knots in the compartments, the little anchor stitches that hold a laid thread.
        val knots = Path()
        var kx = 13f
        while (kx < w) {
            knots.moveTo(kx, y + 17f)
            knots.lineTo(kx + 5f, y + 21f)
            kx += 52f
        }
        scope.drawStitchedOutline(rules, thread)
        scope.drawStitchedOutline(zig, thread)
        scope.drawStitchedOutline(knots, thread.copy(alpha = 0.55f))
    }

    /**
     * A live beast, drawn by whichever archetype branch the renderer keeps for it.
     *
     * [fur] goes into hairColor, which is what the cynocephalus draws its head in — the near-black
     * used for human hair made the dog head invisible against a fur jacket of almost the same value.
     */
    private fun beast(tag: String, arch: EnemyArchetype, head: String, handle: String,
                      armor: String, facing: Boolean, sz: Float,
                      fur: Color = Color(0xFF8A6B45)) = FighterState(
        id = FighterId("beast_$tag"), name = tag, isPlayer = false, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
        shield = GameData.SHIELDS.first { it.id == "shield_none" },
        armor = GameData.ARMOR_PIECES.first { it.id == armor },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == "helm_none" },
        posX = 150f, targetX = 150f, facingRight = facing, size = sz,
        hairColor = fur, hairStyle = "short",
        archetype = arch, animFrame = 1.2f
    )

    private fun bloodSplat(scope: DrawScope, cx: Float, cy: Float, r: Float, seed: Int) {
        // A gout with a ragged rim — a plain circle reads as a berry, which is the note the battle
        // screen's own splats were tuned against.
        val p = Path()
        val pts = 11
        for (i in 0 until pts) {
            val a = (i / pts.toFloat()) * 6.2832f
            // Narrow band: a wide one turns eleven points into a star. Blood pools, it doesn't spike.
            val wob = r * (0.88f + ((seed * 7 + i * 31) % 13) / 52f)
            val x = cx + cos(a) * wob
            val y = cy + sin(a) * wob * 0.72f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawStitchedFill(scope, p, oxblood)
    }

    @Test
    fun trailerEndCard() {
        // Death poses chosen for silhouette variety, gear for "every arm of the levy was here".
        val cast = listOf(
            Pair(105, 350) to fallen("a", "head_spear", "handle_long", "shield_none",
                "armor_padded", "helm_conical", DeathType.FALL_BACK, true, 1.05f),
            Pair(310, 410) to fallen("b", "head_broadsword", "handle_medium", "shield_buckler",
                "armor_chainmail", "helm_spangen", DeathType.FACEPLANT, false, 1.15f),
            Pair(600, 362) to fallen("c", "head_pitchfork", "handle_long", "shield_none",
                "armor_bare", "helm_none", DeathType.CARTWHEEL, true, 0.95f),
            Pair(900, 418) to fallen("d", "head_club", "handle_short", "shield_buckler",
                "armor_leather", "helm_kettle", DeathType.PANCAKE, false, 1.1f),
            // Not KNOCKED_FLYING — it hurls the body clear of its own cell and gets clipped, leaving
            // just the dagger hanging in the air with nobody attached to it.
            Pair(1180, 356) to fallen("e", "head_dagger", "handle_short", "shield_none",
                "armor_padded", "helm_none", DeathType.FALL_BACK, false, 1.0f),
            Pair(1430, 404) to fallen("f", "head_bow", "handle_fists", "shield_none",
                "armor_leather", "helm_conical", DeathType.FACEPLANT, true, 1.05f)
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(linen)) {
                // Ground layer: borders, spilt blood, the smoke still going up off the field.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    border(this, 18f, w)
                    border(this, size.height - 52f, w)

                    // All on the ground line, never above it — a splat drawn in clear air over the
                    // bodies reads as a speck of dirt on the lens rather than as spilt blood.
                    listOf(
                        Triple(230f, 838f, 52f), Triple(540f, 872f, 38f), Triple(815f, 852f, 58f),
                        Triple(1130f, 880f, 42f), Triple(1420f, 845f, 48f), Triple(1690f, 868f, 34f)
                    ).forEachIndexed { i, (x, y, r) -> bloodSplat(this, x, y, r, i + 1) }

                    // Spent shafts lying where they fell.
                    listOf(
                        Triple(150f, 928f, -14f), Triple(430f, 948f, 9f), Triple(700f, 916f, 5f),
                        Triple(1010f, 936f, -6f), Triple(1330f, 946f, 17f), Triple(1620f, 924f, -11f)
                    ).forEach { (x, y, deg) ->
                        val rad = deg * 0.01745f
                        val dx = cos(rad) * 78f
                        val dy = sin(rad) * 78f
                        drawLine(
                            Color(0xFF8C6F47), Offset(x - dx, y - dy), Offset(x + dx, y + dy),
                            strokeWidth = 5f, cap = StrokeCap.Round
                        )
                        drawLine(
                            Color(0xFF9EA3A8), Offset(x + dx * 0.78f, y + dy * 0.78f),
                            Offset(x + dx, y + dy), strokeWidth = 8f, cap = StrokeCap.Round
                        )
                    }
                }

                // The fallen, each in its own cell so drawCharacter keeps its own ground line. The
                // cells are wider than a standing man needs because these are lying down, and the
                // staggered y is what stops six bodies reading as one tidy row.
                cast.forEach { (at, man) ->
                    Canvas(
                        modifier = Modifier
                            .offset(at.first.dp, at.second.dp)
                            .width(430.dp).height(530.dp)
                    ) {
                        TapestryRenderer.drawCharacter(this, man, scale = 1.28f, isBattleActive = true)
                    }
                }

                // The Dog-Head of the East, down among them — the game's own cynocephalus branch,
                // not a dog drawn for this card.
                Canvas(
                    modifier = Modifier.offset(795.dp, 372.dp).width(430.dp).height(530.dp)
                ) {
                    TapestryRenderer.drawCharacter(
                        this,
                        fallen("dog", "head_spear", "handle_medium", "shield_none",
                            "armor_fur", "helm_none", DeathType.FALL_BACK, false, 1.15f)
                            .copy(
                                archetype = EnemyArchetype.CYNOCEPHALUS,
                                hairColor = Color(0xFF8A6B45)  // the head is drawn in hairColor
                            ),
                        scale = 1.28f, isBattleActive = true
                    )
                }

                // The Rebel Snail, still going, bottom left. Marginalia snails belong in the margin
                // of the page, which is exactly where this one is put.
                Canvas(
                    modifier = Modifier.offset(30.dp, 610.dp).width(320.dp).height(400.dp)
                ) {
                    TapestryRenderer.drawCharacter(
                        this,
                        beast("snail", EnemyArchetype.REBEL_SNAIL, "head_bare", "handle_fists",
                            "armor_bare", true, 0.95f),
                        scale = 1.0f, isBattleActive = true
                    )
                }

                // Lettering, over everything.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f

                    val titlePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 172f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val title = "BAYEUX BRAWLERS"
                    val glyphs = android.graphics.Path()
                    titlePaint.getTextPath(title, 0, title.length, cx, 300f, glyphs)
                    val woven = glyphs.asComposePath()
                    // Same treatment every shield and tunic in the game gets: couched fill inside a
                    // wandering wool outline. This is what makes the letters read as worked thread
                    // rather than as a font dropped on top of a picture.
                    drawStitchedFill(this, woven, titleRed)
                    drawStitchedOutline(woven, Color(0xFF2C2219))

                    val subPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 44f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val sub = "AVAILABLE ON ANDROID FROM THE GOOGLE PLAY STORE NOW"
                    val subGlyphs = android.graphics.Path()
                    subPaint.getTextPath(sub, 0, sub.length, cx, 392f, subGlyphs)
                    val subPath = subGlyphs.asComposePath()
                    drawStitchedFill(this, subPath, Color(0xFF3B2F21))
                    drawPath(subPath, Color(0xFF2C2219), style = Stroke(width = 1.6f))

                    // A rule under the line, the way a caption is closed off on the artefact.
                    drawLine(
                        Color(0xFF2C2219), Offset(cx - 470f, 424f), Offset(cx + 470f, 424f),
                        strokeWidth = 2.5f
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/trailer_end_card.png")
    }

    // ---------------------------------------------------------------------------------------
    // Play Store listing assets.
    //
    // The app is screenOrientation="landscape", so every phone screenshot here is 1920x1080.
    // A portrait set would advertise an experience the game never shows.
    // ---------------------------------------------------------------------------------------

    private fun warrior(
        tag: String, head: String, handle: String, shield: String, armor: String, helm: String,
        facing: Boolean, sz: Float, attacking: Boolean = false, swing: Float = 0f,
        mounted: Boolean = false, chariot: Boolean = false, stilts: Boolean = false,
        arch: EnemyArchetype? = null
    ) = FighterState(
        id = FighterId("w_$tag"), name = tag, isPlayer = false, maxHp = 100f, hp = 100f,
        weaponHead = GameData.WEAPON_HEADS.first { it.id == head },
        weaponHandle = GameData.WEAPON_HANDLES.first { it.id == handle },
        shield = GameData.SHIELDS.first { it.id == shield },
        armor = GameData.ARMOR_PIECES.first { it.id == armor },
        headgear = GameData.HEADGEAR_PIECES.first { it.id == helm },
        posX = 150f, targetX = 150f, facingRight = facing, size = sz,
        hairColor = Color(0xFF3A2E24), hairStyle = "short",
        isAttacking = attacking, swingProgress = swing, animFrame = 1.3f,
        isMounted = mounted, isChariot = chariot, isStilts = stilts, archetype = arch
    )

    private fun scenery(type: BackgroundObjectType, seed: Int = 1) = BackgroundObject(
        id = "shot_${type.name}_$seed", type = type, posX = 0f, width = 300f,
        hp = 500f, maxHp = 500f, seed = seed
    )

    /**
     * One staged frame: linen, the worked border top and bottom, then whatever the scene puts on it.
     * Kept identical across the set so the four screenshots read as one listing rather than four.
     */
    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(linen)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                border(this, 14f, size.width)
                border(this, size.height - 56f, size.width)
            }
            content()
        }
    }

    /**
     * The feature graphic: 1024x500 exactly, which Play requires and does not rescale.
     *
     * Deliberately carries NO store badge, no device frame and no "available now" line — Play's
     * asset rules forbid Google Play branding and device images inside the feature graphic, and the
     * end card already does that job in the trailer. Everything that matters sits well inside the
     * middle, because this crops differently on every surface it appears on.
     */
    @Test
    @Config(qualifiers = "w1024dp-h500dp-land-mdpi", sdk = [34])
    fun featureGraphic() {
        composeTestRule.setContent {
            Stage {
                // Cells must END above the bottom border: at 500px tall there is no room to spare,
                // and an over-long cell simply pushes the man off the bottom of the graphic.
                Canvas(modifier = Modifier.offset(10.dp, 165.dp).width(320.dp).height(300.dp)) {
                    TapestryRenderer.drawCharacter(
                        this,
                        warrior("fg1", "head_spear", "handle_long", "shield_kite",
                            "armor_chainmail", "helm_conical", true, 1.0f, true, 0.45f),
                        scale = 0.95f, isBattleActive = true
                    )
                }
                Canvas(modifier = Modifier.offset(700.dp, 160.dp).width(320.dp).height(300.dp)) {
                    TapestryRenderer.drawCharacter(
                        this,
                        warrior("fg2", "head_broadsword", "handle_medium", "shield_buckler",
                            "armor_leather", "helm_spangen", false, 1.05f, true, 0.6f),
                        scale = 0.95f, isBattleActive = true
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 96f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val t = "BAYEUX BRAWLERS"
                    val glyphs = android.graphics.Path()
                    paint.getTextPath(t, 0, t.length, cx, 210f, glyphs)
                    val woven = glyphs.asComposePath()
                    drawStitchedFill(this, woven, titleRed)
                    drawStitchedOutline(woven, Color(0xFF2C2219))

                    val sub = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 34f
                        typeface = android.graphics.Typeface.SERIF
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val s = "AN AUTO-BATTLER STITCHED INTO 1066"
                    val sg = android.graphics.Path()
                    sub.getTextPath(s, 0, s.length, cx, 268f, sg)
                    drawStitchedFill(this, sg.asComposePath(), Color(0xFF3B2F21))
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/play_feature_graphic.png")
    }

    /** Screenshot 1 — the line meets. What the game actually spends its time doing. */
    @Test
    fun playShotClash() {
        composeTestRule.setContent {
            Stage {
                listOf(
                    Triple(60, BackgroundObjectType.MOTTE, 0.72f),
                    Triple(740, BackgroundObjectType.FEASTING_HALL, 0.66f),
                    Triple(1400, BackgroundObjectType.CASTLE_WALL, 0.72f)
                ).forEach { (x, type, sc) ->
                    Canvas(modifier = Modifier.offset(x.dp, 90.dp).width(460.dp).height(470.dp)) {
                        drawBackgroundObject(this, scenery(type), size.width / 2f, sc)
                    }
                }
                // Three facing right, three facing left, and the inner pair close enough to actually
                // meet. Evenly spaced with a gap in the middle read as a parade, not a battle.
                listOf(
                    Triple(20, "head_spear", true), Triple(300, "head_broadsword", true),
                    Triple(580, "head_axe", true), Triple(800, "head_club", false),
                    Triple(1080, "head_dagger", false), Triple(1360, "head_spear", false)
                ).forEachIndexed { i, (x, head, facing) ->
                    Canvas(modifier = Modifier.offset(x.dp, 255.dp).width(560.dp).height(760.dp)) {
                        TapestryRenderer.drawCharacter(
                            this,
                            warrior("c$i", head, if (i % 2 == 0) "handle_medium" else "handle_short",
                                if (facing) "shield_kite" else "shield_buckler",
                                if (i % 3 == 0) "armor_chainmail" else "armor_padded",
                                if (i % 2 == 0) "helm_conical" else "helm_spangen",
                                facing, 1.0f + (i % 3) * 0.06f, true, 0.3f + i * 0.1f),
                            scale = 2.0f, isBattleActive = true
                        )
                    }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    listOf(
                        Triple(330f, 880f, 44f), Triple(880f, 902f, 52f), Triple(1350f, 872f, 38f)
                    ).forEachIndexed { i, (x, y, r) -> bloodSplat(this, x, y, r, i + 3) }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/play_shot_1_clash.png")
    }

    /** Screenshot 2 — the siege. Take the wall and the gate opens. */
    @Test
    fun playShotSiege() {
        composeTestRule.setContent {
            Stage {
                // The gate is the subject, so it has to dominate and stand ON the ground the men are
                // on. At its old scale it read as a doorway hanging in mid-air.
                Canvas(modifier = Modifier.offset(510.dp, 40.dp).width(900.dp).height(980.dp)) {
                    val x = size.width / 2f
                    drawLiveCastleGate(this, scenery(BackgroundObjectType.CASTLE_GATE), x, 2.6f, 90f, 500f)
                    drawSiegeLadder(this, x - 330f, 2.6f)
                }
                listOf(0, 250, 1210, 1450).forEachIndexed { i, x ->
                    Canvas(modifier = Modifier.offset(x.dp, 255.dp).width(560.dp).height(760.dp)) {
                        TapestryRenderer.drawCharacter(
                            this,
                            warrior("s$i", if (i % 2 == 0) "head_axe" else "head_spear",
                                "handle_medium", "shield_kite", "armor_chainmail", "helm_conical",
                                i < 2, 1.05f, true, 0.25f + i * 0.15f),
                            scale = 2.0f, isBattleActive = true
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/play_shot_2_siege.png")
    }

    /** Screenshot 3 — what you can end up riding. The bear sells this one on its own. */
    @Test
    fun playShotMounts() {
        composeTestRule.setContent {
            Stage {
                listOf(
                    Triple(80, "horse", false), Triple(560, "chariot", true),
                    Triple(1060, "stilts", false), Triple(1500, "crown", false)
                ).forEachIndexed { i, (x, kind, chariot) ->
                    Canvas(modifier = Modifier.offset(x.dp, 195.dp).width(560.dp).height(830.dp)) {
                        TapestryRenderer.drawCharacter(
                            this,
                            warrior("m$i", "head_sword", "handle_medium", "shield_kite",
                                "armor_chainmail", if (kind == "crown") "helm_crown" else "helm_conical",
                                true, if (kind == "stilts") 1.0f else 1.1f,
                                mounted = true, chariot = chariot, stilts = kind == "stilts"),
                            scale = 1.85f, isBattleActive = true
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/play_shot_3_mounts.png")
    }

    /** Screenshot 4 — what comes out of the margins at you later on. */
    @Test
    fun playShotBeasts() {
        composeTestRule.setContent {
            Stage {
                // 1.4, not the 1.9 the plain fighters take: a snail is drawn wide rather than tall,
                // so it ran off the left edge, and the cynocephalus's head cleared the top of its
                // own cell and was clipped — a Dog-Head with no head rather defeats the purpose.
                Canvas(modifier = Modifier.offset(190.dp, 300.dp).width(560.dp).height(700.dp)) {
                    TapestryRenderer.drawCharacter(
                        this,
                        beast("snailBig", EnemyArchetype.REBEL_SNAIL, "head_bare", "handle_fists",
                            "armor_bare", true, 1.35f),
                        scale = 1.4f, isBattleActive = true
                    )
                }
                Canvas(modifier = Modifier.offset(700.dp, 300.dp).width(560.dp).height(700.dp)) {
                    TapestryRenderer.drawCharacter(
                        this,
                        beast("dogHead", EnemyArchetype.CYNOCEPHALUS, "head_spear", "handle_medium",
                            "armor_fur", false, 1.25f),
                        scale = 1.4f, isBattleActive = true
                    )
                }
                Canvas(modifier = Modifier.offset(1190.dp, 300.dp).width(560.dp).height(700.dp)) {
                    TapestryRenderer.drawCharacter(
                        this,
                        warrior("hero", "head_broadsword", "handle_medium", "shield_kite",
                            "armor_chainmail", "helm_spangen", false, 1.15f, true, 0.5f),
                        scale = 1.4f, isBattleActive = true
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    bloodSplat(this, 980f, 900f, 50f, 4)
                    bloodSplat(this, 1420f, 880f, 40f, 6)
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/play_shot_4_beasts.png")
    }
}
