package com.example.game

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stilt tops must meet the boots at every body size. Before this test the two were drawn in
 * different transform spaces and only lined up at size 1.0 — a small man hovered ~21px above his
 * own stilts, a large one sank ~35px into them.
 */
class StiltsGeometryTest {

    private val cy = 200f

    /** Where drawLegs puts the sole of the boot, in the body's own (lifted) local space. */
    private fun legSoleY(cy: Float) = cy + 155f

    @Test
    fun stiltTopMeetsTheBootSoleAtEverySize() {
        listOf(0.65f, 0.85f, 1.0f, 1.15f, 1.4f).forEach { size ->
            val gap = abs(stiltTopY(cy) - legSoleY(cy))
            assertTrue("size $size: stilt top is ${gap}px from the boot sole", gap <= 8f)
        }
    }

    @Test
    fun stiltsReachTheGroundBelowTheLiftedBody() {
        // The body is lifted STILTS_LIFT_PX screen px; in local space that is LIFT/size below the
        // sole. The poles must span the whole gap or the man stands on air.
        listOf(0.65f, 1.0f, 1.4f).forEach { size ->
            val span = stiltGroundY(cy, size) - stiltTopY(cy)
            val expected = STILTS_LIFT_PX / size
            assertTrue(
                "size $size: poles span $span, need about $expected",
                abs(span - expected) <= 12f
            )
        }
    }
}
