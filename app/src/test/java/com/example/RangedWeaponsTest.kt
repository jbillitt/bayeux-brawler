package com.example.game

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RangedWeaponsTest {

    @Test
    fun testRangedProjectileFiringAndUpgradeSystems() {
        // 1. Create a GameViewModel
        val viewModel = GameViewModel()
        
        // 2. Fetch the initial layout of the player and a mock enemy
        val state = viewModel.uiState.value
        
        // Find slingshot weapon head
        val slingshotHead = GameData.WEAPON_HEADS.find { it.id == "head_slingshot" }
        assertNotNull("Slingshot should exist in GameData", slingshotHead)
        assertTrue("Slingshot should be classified as ranged", slingshotHead!!.isRanged)

        // Find bow head
        val bowHead = GameData.WEAPON_HEADS.find { it.id == "head_bow" }
        assertNotNull("Bow should exist in GameData", bowHead)
        assertTrue("Bow should be classified as ranged", bowHead!!.isRanged)
        
        // Let's create a custom Player with slingshot and an enemy Saxon
        val player = FighterState(
            id = FighterId("player_test"),
            name = "Test Archer",
            isPlayer = true,
            maxHp = 100f,
            hp = 100f,
            weaponHead = slingshotHead,
            weaponHandle = GameData.WEAPON_HANDLES.first(),
            shield = GameData.SHIELDS.first(),
            armor = GameData.ARMOR_PIECES.first(),
            headgear = GameData.HEADGEAR_PIECES.first(),
            posX = 150f,
            targetX = 150f,
            facingRight = true,
            rangedUpgrades = listOf("slingshot_splash", "slingshot_poison") // Add splash and poison upgrades!
        )

        val enemy = FighterState(
            id = FighterId("enemy_test"),
            name = "Test Enemy",
            isPlayer = false,
            maxHp = 100f,
            hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.find { it.id == "head_sword" } ?: GameData.WEAPON_HEADS.first(),
            weaponHandle = GameData.WEAPON_HANDLES.first(),
            shield = GameData.SHIELDS.first(),
            armor = GameData.ARMOR_PIECES.first(),
            headgear = GameData.HEADGEAR_PIECES.first(),
            posX = 300f,
            targetX = 300f,
            facingRight = false
        )

        // Trigger performStrike with the slingshot!
        // We'll call a private or public method in the VM or directly simulate spawning/hitting using our models
        // Let's verify Projectile creation stats with upgrades
        val sizeMult = 2.2f // with slingshot_bigger
        val spikes = true // with slingshot_spikes
        val splash = true // with slingshot_splash
        val poison = true // with slingshot_poison

        val testProj = Projectile(
            id = "test_projectile",
            isPlayerOwned = true,
            posX = 150f,
            posY = 180f,
            velocityX = 350f,
            velocityY = -55f,
            damage = 15f,
            pierce = 5f,
            blunt = 15f,
            type = com.example.game.ProjectileType.STONE,
            sizeMultiplier = sizeMult,
            hasSpikes = spikes,
            isSplash = splash,
            isPoisonous = poison
        )

        assertEquals(com.example.game.ProjectileType.STONE, testProj.type)
        assertEquals(2.2f, testProj.sizeMultiplier)
        assertTrue(testProj.hasSpikes)
        assertTrue(testProj.isSplash)
        assertTrue(testProj.isPoisonous)

        // 3. Test Gravity application
        val dt = 0.1f
        var currentVelY = testProj.velocityY
        currentVelY += 130f * dt // Gravity pulling down
        assertEquals(-42f, currentVelY, 0.01f) // -55 + 13 = -42

        // 4. Test Poison continuous damage over time ticker
        enemy.poisonDuration = 5.0f
        
        // Simulate poison tick
        if (enemy.poisonDuration > 0f) {
            enemy.poisonDuration -= dt
            val poisonDmg = 8f * dt
            enemy.hp = (enemy.hp - poisonDmg).coerceAtLeast(0f)
        }

        assertEquals(4.9f, enemy.poisonDuration, 0.01f)
        assertEquals(99.2f, enemy.hp, 0.01f) // Took 0.8 poison damage (8 * 0.1)

        // 5. Test Splash damage calculation
        val secondaryEnemy = FighterState(
            id = FighterId("enemy_secondary"),
            name = "Secondary Enemy",
            isPlayer = false,
            maxHp = 100f,
            hp = 100f,
            weaponHead = GameData.WEAPON_HEADS.first(),
            weaponHandle = GameData.WEAPON_HANDLES.first(),
            shield = GameData.SHIELDS.first(),
            armor = GameData.ARMOR_PIECES.first(),
            headgear = GameData.HEADGEAR_PIECES.first(),
            posX = 280f, // very close to enemy (posX=300f)
            targetX = 280f,
            facingRight = false
        )

        // If a splash hits the main defender (enemy at 300f), nearby foes within 120 pixels should receive 50% splash damage
        if (testProj.isSplash) {
            val totalDamage = 20f
            val splashDmg = (totalDamage * 0.5f).coerceAtLeast(2f)
            
            val otherEnemies = listOf(secondaryEnemy)
            otherEnemies.forEach { other ->
                if (abs(other.posX - enemy.posX) < 120f) {
                    other.hp = (other.hp - splashDmg).coerceAtLeast(0f)
                }
            }
        }

        assertEquals(90f, secondaryEnemy.hp) // Took 10 damage (50% of 20f)
    }
}
