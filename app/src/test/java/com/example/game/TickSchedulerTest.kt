package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TickSchedulerTest {

    @Test
    fun `two milliseconds of lateness per tick does not accumulate drift`() {
        val scheduler = TickScheduler(periodMs = 33L)
        var now = 0L
        val ticks = mutableListOf<Long>()

        repeat(100) {
            val requestedDelay = scheduler.nextDelay(now)
            now += requestedDelay + 2L
            ticks += now
        }

        val spacings = ticks.zipWithNext { a, b -> b - a }
        assertEquals(33.0, spacings.average(), 0.0)
        assertTrue(spacings.all { it == 33L })
    }

    @Test
    fun `a large stall permits only one immediate catch-up tick`() {
        val scheduler = TickScheduler(periodMs = 33L)
        assertEquals(33L, scheduler.nextDelay(0L))

        val stalledAt = 233L
        assertEquals(0L, scheduler.nextDelay(stalledAt))
        assertEquals(33L, scheduler.nextDelay(stalledAt))
    }
}
