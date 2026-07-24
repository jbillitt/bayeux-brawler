package com.example.game

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render rig for the Asset Magazine (scripts/vector_builder): reads build/magazine_preview/spec.json,
 * draws the requested subject with the game's REAL renderer, and writes frame_N.png + done.marker.
 * No spec file = no-op, so the normal test suite is unaffected. Driven by the magazine's server via
 * `gradle :app:testDebugUnitTest --tests "com.example.game.MagazinePreviewTest" --rerun`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class MagazinePreviewTest {

    private val linen = Color(0xFFF1E6CC)

    @Test
    fun renderSpecFrames() {
        val dir = File("build/magazine_preview")
        val specFile = File(dir, "spec.json")
        if (!specFile.exists()) return
        VectorAsset.init(ApplicationProvider.getApplicationContext())

        val spec = JSONObject(specFile.readText())
        val w = spec.optInt("width", 420)
        val h = spec.optInt("height", 400)
        val scale = spec.optDouble("scale", 1.0).toFloat()
        val frames = spec.optJSONArray("frames") ?: JSONArray().put(JSONObject())

        for (i in 0 until frames.length()) {
            val fr = frames.getJSONObject(i)
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = androidx.compose.ui.graphics.Canvas(bitmap),
                size = Size(w.toFloat(), h.toFloat())
            ) {
                drawRect(linen)
                when (spec.optString("subject", "character")) {
                    "building" -> {
                        val bg = BackgroundObject(
                            id = "magazine_bg",
                            type = BackgroundObjectType.valueOf(spec.optString("building", "BUILDING_BOSHAM")),
                            posX = 0f,
                            width = 300f,
                            hp = spec.optDouble("hp", 500.0).toFloat(),
                            maxHp = 500f,
                            seed = spec.optInt("seed", 1),
                            artId = spec.optString("artId", "").ifEmpty { null }
                        )
                        drawBackgroundObject(this, bg, w / 2f, scale)
                    }
                    "ancillary" -> {
                        val anc = Ancillary.valueOf(spec.optString("ancillary", "SQUIRE"))
                        val fighter = fighterFrom(spec, fr, w)
                        // The follower parades behind the player (posX - 110, or -90 for Lil Guy).
                        // Shift the anchor so the follower himself lands centre-stage, and only
                        // draw the player when he's part of the pose (Lil Guy rides his back).
                        val showPlayer = spec.optBoolean("showPlayer", anc == Ancillary.LIL_GUY)
                        fighter.posX = w / 2f + if (anc == Ancillary.LIL_GUY) 90f else 110f
                        fighter.targetX = fighter.posX
                        TapestryRenderer.drawAncillaries(this, listOf(anc), fighter, scale)
                        if (showPlayer) TapestryRenderer.drawCharacter(this, fighter, scale, isBattleActive = true)
                    }
                    else -> {
                        TapestryRenderer.drawCharacter(this, fighterFrom(spec, fr, w), scale, isBattleActive = true)
                    }
                }
            }
            FileOutputStream(File(dir, "frame_$i.png")).use {
                bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        File(dir, "done.marker").writeText("ok")
    }

    private fun fighterFrom(spec: JSONObject, fr: JSONObject, w: Int): FighterState {
        fun gear(key: String, fallback: String) = spec.optString(key, fallback).ifEmpty { fallback }
        return FighterState(
            id = "magazine", name = "Magazine", isPlayer = true, maxHp = 100f, hp = 100f,
            // firstOrNull: an unknown id from the magazine falls back rather than killing the render
            weaponHead = GameData.WEAPON_HEADS.firstOrNull { it.id == gear("head", "head_sword") } ?: GameData.WEAPON_HEADS.first(),
            weaponHandle = GameData.WEAPON_HANDLES.firstOrNull { it.id == gear("handle", "handle_medium") } ?: GameData.WEAPON_HANDLES.first(),
            shield = GameData.SHIELDS.firstOrNull { it.id == gear("shield", "shield_none") } ?: GameData.SHIELDS.first(),
            armor = GameData.ARMOR_PIECES.firstOrNull { it.id == gear("armor", "armor_chainmail") } ?: GameData.ARMOR_PIECES.first(),
            headgear = GameData.HEADGEAR_PIECES.firstOrNull { it.id == gear("helm", "helm_none") } ?: GameData.HEADGEAR_PIECES.first(),
            posX = w / 2f, targetX = w / 2f,
            facingRight = fr.optBoolean("facingRight", spec.optBoolean("facingRight", true)),
            size = spec.optDouble("size", 1.0).toFloat(),
            hairColor = Color(0xFF5A442E),
            hairStyle = spec.optString("hair", "short"),
            isDualWielding = spec.optBoolean("dualWield", false),
            isMounted = spec.optBoolean("mounted", false) || spec.optBoolean("chariot", false) || spec.optBoolean("stilts", false),
            isChariot = spec.optBoolean("chariot", false),
            isStilts = spec.optBoolean("stilts", false)
        ).apply {
            animFrame = fr.optDouble("animFrame", 0.0).toFloat()
            isAttacking = fr.optBoolean("attacking", false)
            swingProgress = fr.optDouble("swing", 0.0).toFloat()
            if (isMounted) mountHp = 80f
        }
    }
}
