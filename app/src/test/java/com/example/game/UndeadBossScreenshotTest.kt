package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The five bosses in all three states, so the grave-pallor can actually be seen rather than
 * trusted. Each row is one tier: living, undead, barrow-king.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w850dp-h393dp-land-xhdpi", sdk = [34])
class UndeadBossScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun loadArt() {
        VectorAsset.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    @Test
    fun everyBossInEveryTier() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF1E6CC))) {
                BossTier.values().forEach { tier ->
                    Row {
                        BossType.values().forEach { type ->
                            Canvas(modifier = Modifier.width(168.dp).height(130.dp)) {
                                val boss = EnemyFactory.createBoss(type, level = 60, tier = tier)
                                    .copy(posX = 0f, animFrame = 0f, facingRight = true)
                                TapestryRenderer.drawCharacter(
                                    this, boss, scale = 0.55f, isBattleActive = false
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/undead_bosses.png")
    }

    /** A player who reached 136 met William nine times running. The cycle is the actual fix. */
    @Test
    fun lateLevelsCycleTheBossesInsteadOfRepeatingWilliam() {
        val lateBosses = (60..200 step 10).map { BossSchedule.forLevel(it) }
        assertTrue("every tenth level past 50 must still be a boss", lateBosses.all { it != null })
        assertEquals(
            "all five bosses should come round again, not just William",
            BossType.values().toSet(), lateBosses.filterNotNull().toSet()
        )
        // The originals keep their own levels.
        assertEquals(BossType.HAROLD_GODWINSON, BossSchedule.forLevel(10))
        assertEquals(BossType.MAGOG, BossSchedule.forLevel(50))
        assertEquals(null, BossSchedule.forLevel(55))
    }

    @Test
    fun tiersRiseWithTheCampaign() {
        assertEquals(BossTier.LIVING, BossSchedule.tierForLevel(50))
        assertEquals(BossTier.UNDEAD, BossSchedule.tierForLevel(60))
        assertEquals(BossTier.UNDEAD, BossSchedule.tierForLevel(120))
        assertEquals(BossTier.SUPER_UNDEAD, BossSchedule.tierForLevel(130))
        assertEquals(BossTier.SUPER_UNDEAD, BossSchedule.tierForLevel(136))
    }

    /** The risen are harder to put down, and harder to keep down. */
    @Test
    fun theUndeadAreTougherAndResistCrowdControlBetter() {
        val living = EnemyFactory.createBoss(BossType.GOG, 60, BossTier.LIVING)
        val undead = EnemyFactory.createBoss(BossType.GOG, 60, BossTier.UNDEAD)
        val superU = EnemyFactory.createBoss(BossType.GOG, 60, BossTier.SUPER_UNDEAD)

        assertTrue("undead must carry more hp", undead.maxHp > living.maxHp)
        assertTrue("a barrow-king more still", superU.maxHp > undead.maxHp)
        assertTrue("undead must resist cc better", undead.ccResist < living.ccResist)
        assertTrue("a barrow-king better again", superU.ccResist < undead.ccResist)
        assertTrue("the risen keep their name", undead.name.startsWith("Undead "))
        // Distinct ids, or two tiers of the same boss would collide in any id-keyed lookup.
        assertTrue(living.id != undead.id && undead.id != superU.id)
    }
}
