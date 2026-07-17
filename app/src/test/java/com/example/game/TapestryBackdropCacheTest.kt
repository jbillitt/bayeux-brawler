package com.example.game

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class TapestryBackdropCacheTest {

    @Test
    fun `same key returns the same value and creates once`() {
        val cache = BackdropCachePolicy<Any>()
        val key = BackdropKey(level = 3, borderSeed = 42L, w = 64, h = 32)
        var creates = 0

        val first = cache.valueFor(key) { creates++; Any() }
        val second = cache.valueFor(key) { creates++; Any() }

        assertSame(first, second)
        assertEquals(1, creates)
    }

    @Test
    fun `level seed and dimensions each invalidate the cache`() {
        val cache = BackdropCachePolicy<Any>()
        val base = BackdropKey(level = 3, borderSeed = 42L, w = 64, h = 32)
        var creates = 0
        var previous = cache.valueFor(base) { creates++; Any() }

        listOf(
            base.copy(level = 4),
            base.copy(level = 4, borderSeed = 43L),
            base.copy(level = 4, borderSeed = 43L, w = 65),
            base.copy(level = 4, borderSeed = 43L, w = 65, h = 33)
        ).forEach { key ->
            val next = cache.valueFor(key) { creates++; Any() }
            assertNotSame(previous, next)
            previous = next
        }

        assertEquals(5, creates)
    }

    @Test
    fun `world backdrop key ignores live damage but tracks immutable scenery`() {
        val bg = BackgroundObject(
            id = "hall", type = BackgroundObjectType.FEASTING_HALL,
            posX = 400f, width = 500f, hp = 100f, maxHp = 100f, seed = 7
        )
        val initial = staticBackgroundVisualSeed(42L, listOf(bg), 1f)

        bg.hp = 25f
        bg.isDestroyed = true
        bg.stuckArrowsFromLeft = 4
        assertEquals(initial, staticBackgroundVisualSeed(42L, listOf(bg), 1f))

        assertNotEquals(
            initial,
            staticBackgroundVisualSeed(42L, listOf(bg.copy(posX = 401f)), 1f)
        )
    }
}
