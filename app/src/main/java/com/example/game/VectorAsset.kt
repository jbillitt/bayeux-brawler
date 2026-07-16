package com.example.game

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.json.JSONObject

/**
 * Data-driven art. A building's shape and colours live in `assets/art/<id>.json` so they can be
 * edited in the vector builder without a compiler — or a language model — in the loop.
 *
 * Deliberately parsed with the platform's own org.json: the schema is small, and this keeps the
 * pipeline free of a serialization dependency and a Gradle plugin.
 *
 * What stays in Kotlin: anything *seeded*. A palisade whose posts differ per fort cannot be a static
 * path list — freezing it would give every motte in the game the same wall. Such art is drawn by
 * code over the top of the asset, positioned by a named [anchors] entry, so moving the asset in the
 * builder moves the code-drawn art with it. See BuildingRenderer.drawPalisadeRun.
 *
 * Coordinates are relative to the asset's own origin (the object's centre, ground line at y=0),
 * matching what the Kotlin draw functions already used.
 */
data class VectorAsset(
    val id: String,
    val palette: Map<String, Color>,
    val layers: List<Layer>,
    /** Named points procedural code hangs off. Not drawn. */
    val anchors: Map<String, Anchor> = emptyMap(),
    /**
     * Present = this asset spawns itself into levels. The game enumerates assets/art/ at startup, so
     * a new .json file with a spawn block becomes a building in the game with no Kotlin changes at
     * all. Absent = the asset is drawn by code that asks for it by name (the ship, the forts).
     */
    val spawn: Spawn? = null
) {
    data class Spawn(val weight: Int, val hp: Float, val minLevel: Int)

    data class Layer(
        val id: String,
        val fill: String?,      // palette key
        val stroke: String?,    // palette key
        val strokeWidth: Float,
        val stitched: Boolean,  // fill with the tapestry stitch texture rather than flat
        val commands: List<Cmd>
    )

    data class Anchor(val x0: Float, val x1: Float, val y: Float)

    /** M/L/Q/C/Z plus the shorthands the builder emits. */
    data class Cmd(val op: String, val args: List<Float>)

    companion object {
        private val cache = HashMap<String, VectorAsset?>()
        private var appContext: Context? = null

        /**
         * Hand the loader an application context once, at startup. The draw functions run deep in a
         * DrawScope with no context of their own, so they reach the art through [cached].
         */
        fun init(context: Context) {
            appContext = context.applicationContext
            cache.clear()
        }

        /** The asset for [id], or null if it is missing or malformed — callers then draw nothing. */
        fun cached(id: String): VectorAsset? {
            if (cache.containsKey(id)) return cache[id]
            val ctx = appContext
            val asset = if (ctx == null) null else try {
                parse(ctx.assets.open("art/$id.json").bufferedReader().use { it.readText() })
            } catch (e: java.io.IOException) {
                null // no such asset — fine, the caller falls back
            } catch (e: org.json.JSONException) {
                android.util.Log.e("VectorAsset", "art/$id.json is malformed", e)
                null
            }
            cache[id] = asset
            return asset
        }

        fun clearCache() = cache.clear()

        /**
         * Every asset in assets/art/ that declares a spawn block. This is the whole reason a new
         * building needs no code: drop `my_hall.json` in the folder with a spawn block and the level
         * generator picks it up on the next launch.
         */
        fun spawnable(): List<VectorAsset> {
            val ctx = appContext ?: return emptyList()
            val files = try {
                ctx.assets.list("art").orEmpty()
            } catch (e: java.io.IOException) {
                return emptyList()
            }
            return files
                .filter { it.endsWith(".json") }
                .mapNotNull { cached(it.removeSuffix(".json")) }
                .filter { it.spawn != null }
        }

        fun parse(text: String): VectorAsset {
            val root = JSONObject(text)

            val paletteJson = root.getJSONObject("palette")
            val palette = paletteJson.keys().asSequence().associateWith { key ->
                parseColor(paletteJson.getString(key))
            }

            val layersJson = root.getJSONArray("layers")
            val layers = (0 until layersJson.length()).map { i ->
                val l = layersJson.getJSONObject(i)
                val cmdsJson = l.getJSONArray("commands")
                val cmds = (0 until cmdsJson.length()).map { c ->
                    val cmd = cmdsJson.getJSONArray(c)
                    val args = (1 until cmd.length()).map { a -> cmd.getDouble(a).toFloat() }
                    Cmd(cmd.getString(0).uppercase(), args)
                }
                Layer(
                    id = l.optString("id", "layer_$i"),
                    fill = l.optString("fill").ifEmpty { null },
                    stroke = l.optString("stroke").ifEmpty { null },
                    strokeWidth = l.optDouble("strokeWidth", 2.0).toFloat(),
                    stitched = l.optBoolean("stitched", true),
                    commands = cmds
                )
            }

            val anchors = root.optJSONObject("anchors")?.let { a ->
                a.keys().asSequence().associateWith { key ->
                    val o = a.getJSONObject(key)
                    Anchor(
                        x0 = o.getDouble("x0").toFloat(),
                        x1 = o.getDouble("x1").toFloat(),
                        y = o.getDouble("y").toFloat()
                    )
                }
            } ?: emptyMap()

            val spawn = root.optJSONObject("spawn")?.let { s ->
                Spawn(
                    weight = s.optInt("weight", 1),
                    hp = s.optDouble("hp", 300.0).toFloat(),
                    minLevel = s.optInt("minLevel", 2)
                )
            }

            return VectorAsset(root.getString("id"), palette, layers, anchors, spawn)
        }

        /** "#RRGGBB" or "0xFFRRGGBB" — the builder writes the former, Kotlin art used the latter. */
        fun parseColor(s: String): Color {
            val hex = s.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            val v = hex.toLong(16)
            return if (hex.length <= 6) Color(0xFF000000L or v) else Color(v)
        }
    }
}

/**
 * Draws [asset] with its origin at ([cx], [cy]). [paletteOverride] swaps individual palette entries
 * (damage states, faction colours) without touching the file.
 */
internal fun DrawScope.drawVectorAsset(
    asset: VectorAsset,
    cx: Float,
    cy: Float,
    paletteOverride: Map<String, Color> = emptyMap()
) {
    val scope = this
    for (layer in asset.layers) {
        val path = Path()
        var i = 0
        for (cmd in layer.commands) {
            val a = cmd.args
            when (cmd.op) {
                "M" -> path.moveTo(cx + a[0], cy + a[1])
                "L" -> path.lineTo(cx + a[0], cy + a[1])
                "Q" -> path.quadraticTo(cx + a[0], cy + a[1], cx + a[2], cy + a[3])
                "C" -> path.cubicTo(cx + a[0], cy + a[1], cx + a[2], cy + a[3], cx + a[4], cy + a[5])
                "Z" -> path.close()
                "RECT" -> path.addRect(Rect(cx + a[0], cy + a[1], cx + a[2], cy + a[3]))
                "OVAL" -> path.addOval(Rect(cx + a[0], cy + a[1], cx + a[2], cy + a[3]))
                "CIRCLE" -> path.addOval(
                    Rect(cx + a[0] - a[2], cy + a[1] - a[2], cx + a[0] + a[2], cy + a[1] + a[2])
                )
                "LINE" -> {
                    // A bare stroke, not part of the path — drawn immediately in the layer's colour
                    val col = layer.stroke?.let { resolve(asset, it, paletteOverride) }
                        ?: layer.fill?.let { resolve(asset, it, paletteOverride) }
                        ?: ThreadColor
                    scope.drawLine(
                        col,
                        Offset(cx + a[0], cy + a[1]),
                        Offset(cx + a[2], cy + a[3]),
                        strokeWidth = layer.strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
            i++
        }

        layer.fill?.let { key ->
            val col = resolve(asset, key, paletteOverride)
            if (layer.stitched) drawStitchedFill(scope, path, col) else scope.drawPath(path, col)
        }
        layer.stroke?.let { key ->
            scope.drawPath(path, resolve(asset, key, paletteOverride), style = Stroke(width = layer.strokeWidth))
        }
        // Every shape gets the tapestry outline unless it asked for its own stroke
        if (layer.stroke == null && layer.fill != null) {
            scope.drawPath(path, ThreadColor, style = StitchedStroke)
        }
    }
}

private fun resolve(asset: VectorAsset, key: String, override: Map<String, Color>): Color =
    override[key] ?: asset.palette[key] ?: Color.Magenta // magenta = a palette key that isn't there
