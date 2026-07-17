package com.example.game

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

data class BackdropKey(val level: Int, val borderSeed: Long, val w: Int, val h: Int)

internal fun staticBackgroundVisualSeed(
    initial: Long,
    objects: List<BackgroundObject>,
    worldZoom: Float
): Long = objects.fold(initial) { hash, bg ->
    var value = hash * 31L + bg.id.hashCode()
    value = value * 31L + bg.type.ordinal
    value = value * 31L + bg.posX.toBits()
    value = value * 31L + bg.posY.toBits()
    value = value * 31L + bg.width.toBits()
    value = value * 31L + bg.artId.hashCode()
    value * 31L + bg.seed
} * 31L + worldZoom.toBits()

internal class BackdropCachePolicy<T> {
    private var cachedKey: BackdropKey? = null
    private var cachedValue: T? = null

    fun valueFor(key: BackdropKey, create: () -> T): T {
        if (key == cachedKey) return checkNotNull(cachedValue)
        return create().also {
            cachedKey = key
            cachedValue = it
        }
    }
}

class TapestryBackdropCache {
    private val policy = BackdropCachePolicy<ImageBitmap>()

    fun bitmapFor(key: BackdropKey, drawStatic: DrawScope.() -> Unit): ImageBitmap {
        require(key.w > 0 && key.h > 0) { "Backdrop dimensions must be positive" }
        return policy.valueFor(key) {
            val bitmap = ImageBitmap(key.w, key.h)
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(bitmap),
                size = Size(key.w.toFloat(), key.h.toFloat()),
                block = drawStatic
            )
            bitmap
        }
    }
}
