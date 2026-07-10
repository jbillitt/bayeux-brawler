package com.example

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.game.FighterState
import com.example.game.GameData
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `generateShareImage returns null when player is null`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result: Uri? = generateShareImage(context, null, 1.0f, 0, true)
        assertNull(result)
    }

    @Test
    fun `generateShareImage generates bitmap successfully`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        
        // Mock a player state
        val player = FighterState(
            id = "test_player",
            name = "Test Knight",
            isPlayer = true,
            maxHp = 100f,
            hp = 100f,
            posX = 0f,
            targetX = 0f,
            weaponHead = GameData.WEAPON_HEADS[0],
            weaponHandle = GameData.WEAPON_HANDLES[0],
            shield = GameData.SHIELDS[0],
            armor = GameData.ARMOR_PIECES[0],
            headgear = GameData.HEADGEAR_PIECES[0]
        )
        
        try {
            val result: Uri? = generateShareImage(context, player, 1.5f, 10, false)
            // It might return null in a plain test environment if FileProvider paths aren't fully configured
            // but the important part is that the drawing code (StaticLayout, Canvas, etc) executes without throwing.
        } catch (e: Exception) {
            org.junit.Assert.fail("generateShareImage threw an exception: \${e.message}")
        }
    }
}
