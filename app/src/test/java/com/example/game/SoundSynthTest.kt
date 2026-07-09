package com.example.game

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundSynthTest {

    @Test
    fun `sound types exist and can be referenced`() {
        assertNotNull(SoundType.CLANG)
        assertNotNull(SoundType.SWOOSH)
        assertNotNull(SoundType.THWACK)
        assertNotNull(SoundType.OUCH)
        assertNotNull(SoundType.HUZZAH)
    }
}
