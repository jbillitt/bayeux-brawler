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
            strikeXs = listOf(
                innerFieldRect.left + width * 0.52f,
                innerFieldRect.left + width * 0.72f
            ),
            skyY = innerFieldRect.top,
            groundY = innerFieldRect.top + height * 0.78f,
            jaggedXRadius = min(width * 0.03f, height * 0.06f),
            burstStartRadius = max(18f, shortestSide * 0.02f),
            burstGrowthRadius = max(70f, shortestSide * 0.08f)
        )

        DivineWeather.FLOOD -> {
            val backExtent = max(width * 0.08f, height * 0.16f)
            val frontExtent = max(width * 0.025f, height * 0.04f)
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
    }
}
