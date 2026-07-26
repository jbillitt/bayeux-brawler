package com.example

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.game.DivineWeather
import kotlin.math.max
import kotlin.math.min

internal const val TAPESTRY_BORDER_BAND_PX = 40f

internal fun innerFieldRect(canvasSize: Size, outerBorderPx: Float = 2f): Rect {
    val horizontalInset = outerBorderPx.coerceIn(0f, canvasSize.width / 2f)
    val verticalInset =
        (TAPESTRY_BORDER_BAND_PX + outerBorderPx).coerceIn(0f, canvasSize.height / 2f)
    return Rect(
        left = horizontalInset,
        top = verticalInset,
        right = canvasSize.width - horizontalInset,
        bottom = canvasSize.height - verticalInset
    )
}

internal sealed interface WeatherFlourishGeometry {
    val fieldRect: Rect
    val washRect: Rect
        get() = fieldRect
}

internal data class LightningFlourishGeometry(
    override val fieldRect: Rect,
    val strikeXs: List<Float>,
    val skyY: Float,
    val groundY: Float,
    val jaggedXRadius: Float,
    val burstStartRadius: Float,
    val burstGrowthRadius: Float
) : WeatherFlourishGeometry

internal data class FloodFlourishGeometry(
    override val fieldRect: Rect,
    val edgeX: Float,
    val sweepStartEdgeX: Float,
    val sweepEndEdgeX: Float,
    val backExtent: Float,
    val frontExtent: Float,
    val sprayBackExtent: Float,
    val sprayFrontExtent: Float,
    val crestY: Float,
    val crestDipY: Float
) : WeatherFlourishGeometry

internal data class HailFlourishGeometry(
    override val fieldRect: Rect,
    val dropRect: Rect,
    val groundY: Float,
    val impactBottomY: Float,
    val streakMinLength: Float,
    val streakMaxLength: Float
) : WeatherFlourishGeometry

internal data class FrostFlourishGeometry(
    override val fieldRect: Rect,
    val bandTopY: Float,
    val crystalRect: Rect,
    val crystalStartRadius: Float,
    val crystalMaxRadius: Float
) : WeatherFlourishGeometry

internal data class FrogsFlourishGeometry(
    override val fieldRect: Rect,
    val dropRect: Rect,
    val groundY: Float,
    val impactBottomY: Float
) : WeatherFlourishGeometry

internal fun weatherFlourishGeometry(
    weather: DivineWeather,
    innerFieldRect: Rect,
    progress: Float
): WeatherFlourishGeometry {
    require(innerFieldRect.width >= 0f && innerFieldRect.height >= 0f)
    val p = progress.coerceIn(0f, 1f)
    val width = innerFieldRect.width
    val height = innerFieldRect.height
    val shortestSide = min(width, height)

    return when (weather) {
        DivineWeather.LIGHTNING -> LightningFlourishGeometry(
            fieldRect = innerFieldRect,
            // Five bolts spanning the whole field. Two at 0.52 and 0.72 left the entire left
            // third of a landscape screen untouched, so a storm called down on the host struck
            // only the right-hand side of it. Irregular spacing so it does not read as a comb.
            strikeXs = listOf(0.09f, 0.27f, 0.48f, 0.68f, 0.89f).map {
                innerFieldRect.left + width * it
            },
            skyY = innerFieldRect.top,
            // Was 0.78 — the bolts stopped in mid-air well above the line the men stand on.
            groundY = innerFieldRect.top + height * 0.88f,
            jaggedXRadius = min(width * 0.03f, height * 0.06f),
            burstStartRadius = max(18f, shortestSide * 0.02f),
            burstGrowthRadius = max(70f, shortestSide * 0.08f)
        )

        DivineWeather.FLOOD -> {
            // A wall of water, not a floating pane. At 0.08 of the width the wave front was a
            // small box drifting across the field, which is what read as a "prebaked weather box"
            // rather than the sea coming in.
            val backExtent = max(width * 0.34f, height * 0.16f)
            val frontExtent = max(width * 0.09f, height * 0.04f)
            val strokeAllowance = 4f
            val start = innerFieldRect.left - frontExtent - strokeAllowance
            val end = innerFieldRect.right + backExtent + strokeAllowance
            FloodFlourishGeometry(
                fieldRect = innerFieldRect,
                edgeX = start + (end - start) * p,
                sweepStartEdgeX = start,
                sweepEndEdgeX = end,
                backExtent = backExtent,
                frontExtent = frontExtent,
                sprayBackExtent = backExtent * 0.20f,
                sprayFrontExtent = frontExtent * 1.50f,
                crestY = innerFieldRect.top,
                crestDipY = innerFieldRect.top + height * 0.25f
            )
        }

        DivineWeather.HAIL -> HailFlourishGeometry(
            fieldRect = innerFieldRect,
            dropRect = innerFieldRect,
            groundY = innerFieldRect.top + height * 0.80f,
            impactBottomY = innerFieldRect.top + height * 0.90f,
            streakMinLength = max(10f, shortestSide * 0.01f),
            streakMaxLength = max(20f, shortestSide * 0.02f)
        )

        DivineWeather.FROST -> FrostFlourishGeometry(
            fieldRect = innerFieldRect,
            bandTopY = innerFieldRect.top + height * 0.55f,
            crystalRect = Rect(
                left = innerFieldRect.left,
                top = innerFieldRect.top + height * 0.60f,
                right = innerFieldRect.right,
                bottom = innerFieldRect.top + height * 0.95f
            ),
            crystalStartRadius = max(6f, shortestSide * 0.006f),
            crystalMaxRadius = max(15f, shortestSide * 0.015f)
        )

        DivineWeather.FROGS -> FrogsFlourishGeometry(
            fieldRect = innerFieldRect,
            dropRect = innerFieldRect,
            groundY = innerFieldRect.top + height * 0.80f,
            impactBottomY = innerFieldRect.top + height * 0.92f
        )
    }
}
