package com.example.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdGateTest {

    /**
     * The kill switch that protects the AdMob account: a debug build wired to REAL ad units must
     * never request an ad, whatever the tester toggles. This is the one that must not regress —
     * clicking your own live ads is how accounts get suspended.
     */
    @Test
    fun debugBuildsWithLiveAdIdsNeverShowAds() {
        assertFalse(AdGate.adsAllowed(isDebug = true, purchased = false, enabled = true, testAdIds = false))
        assertFalse(AdGate.adsAllowed(isDebug = true, purchased = true, enabled = true, testAdIds = false))
    }

    /**
     * ...but on Google's own test ad units, which serve fakes and earn nothing, the burger-menu
     * switch has to actually do something. It previously could not: the debug lock outranked it,
     * so "Enable test ads" was inert in the only build a tester ever runs.
     */
    @Test
    fun theTestAdsSwitchWorksOnADebugBuildUsingTestAdIds() {
        assertTrue(AdGate.adsAllowed(isDebug = true, purchased = false, enabled = true, testAdIds = true))
        assertFalse(
            "the switch is still the master control",
            AdGate.adsAllowed(isDebug = true, purchased = false, enabled = false, testAdIds = true)
        )
        assertFalse(
            "a purchase still removes ads",
            AdGate.adsAllowed(isDebug = true, purchased = true, enabled = true, testAdIds = true)
        )
    }

    @Test
    fun aPurchaseRemovesAds() {
        assertFalse(AdGate.adsAllowed(isDebug = false, purchased = true, enabled = true))
    }

    @Test
    fun aFreeReleasePlayerSeesAdsOnceTheyAreEnabled() {
        assertTrue(AdGate.adsAllowed(isDebug = false, purchased = false, enabled = true))
    }

    /**
     * Testers get a build with no ads in it at all until someone deliberately turns them on from
     * the burger menu. This is the guarantee that the default is silence, not the placement logic.
     */
    @Test
    fun adsAreOffUntilExplicitlyEnabled() {
        assertFalse(
            "a tester build must show no ads by default",
            AdGate.adsAllowed(isDebug = false, purchased = false, enabled = false)
        )
        assertFalse("the runtime switch must default to off", AdGate.testAdsEnabled)
    }

    @org.junit.Before
    fun clearAdClock() = AdGate.resetInterstitialClock()

    @Test
    fun theInterstitialFiresOnEveryThirdDeath() {
        assertFalse(AdGate.shouldShowInterstitial(1))
        assertFalse(AdGate.shouldShowInterstitial(2))
        assertTrue(AdGate.shouldShowInterstitial(3))
        assertFalse(AdGate.shouldShowInterstitial(4))
        assertTrue(AdGate.shouldShowInterstitial(6))
    }

    @Test
    fun deathZeroIsNotAnAdBreak() {
        assertFalse(AdGate.shouldShowInterstitial(0))
    }

    /**
     * The churn case this guard exists for: someone stuck on a boss dies in quick bursts, so the
     * death counter comes due again within a couple of minutes and serves an ad on the second
     * death of a losing streak.
     */
    @Test
    fun aSecondInterstitialIsHeldBackWhileTheLastOneIsStillRecent() {
        val start = 1_000_000L
        assertTrue("the first is always allowed", AdGate.shouldShowInterstitial(3, start))
        AdGate.markInterstitialShown(start)

        assertFalse(
            "an ad 30s after the last one is the churn trigger",
            AdGate.shouldShowInterstitial(6, start + 30_000L)
        )
        assertFalse(
            "still too soon just under the gap",
            AdGate.shouldShowInterstitial(6, start + AdGate.MIN_INTERSTITIAL_GAP_MS - 1)
        )
        assertTrue(
            "once the quiet period is served, the count rules again",
            AdGate.shouldShowInterstitial(6, start + AdGate.MIN_INTERSTITIAL_GAP_MS)
        )
    }

    /** The clock must never override the count — a long gap does not make death 4 an ad break. */
    @Test
    fun theQuietPeriodElapsingDoesNotItselfTriggerAnAd() {
        val start = 1_000_000L
        AdGate.markInterstitialShown(start)
        assertFalse(AdGate.shouldShowInterstitial(4, start + 10 * AdGate.MIN_INTERSTITIAL_GAP_MS))
        assertFalse(AdGate.shouldShowInterstitial(5, start + 10 * AdGate.MIN_INTERSTITIAL_GAP_MS))
    }

    /**
     * The unit-test JVM runs the debug variant, so this is the real guarantee that the build a
     * developer plays on their own device never requests an ad, whatever the profile says.
     */
    @Test
    fun theDebugBuildThisTestRunsInAllowsNoAds() {
        assertFalse("a debug build must never request an ad", AdGate.adsAllowedNow())
    }
}

/**
 * The rewarded ad's payout is a pure function of state, so the part that matters — the player
 * actually gets more choice — is testable without the ad SDK, which is not unit-testable at all.
 */
class AdRewardTest {

    private fun state() = BattleSimState()

    @Test
    fun theRewardAddsExtraChoices() {
        val extra = GameViewModel.rewardChoices(state(), kotlin.random.Random(7))
        assertTrue("the reward must actually give something", extra.isNotEmpty())
    }

    @Test
    fun theRewardNeverOffersAnAttachmentAlreadyWorn() {
        // Every attachable head already attached: the attachment card must drop out entirely.
        val allHeads = GameData.WEAPON_HEADS
            .filter {
                it.id !in listOf("head_bare", "head_bow", "head_longbow", "head_slingshot") &&
                    it.id !in GameData.STRANGE_HEAD_IDS
            }
            .map { it.id }
        val extra = GameViewModel.rewardChoices(
            state().copy(extraAttachments = allHeads),
            kotlin.random.Random(7)
        )
        assertTrue(
            "offered a duplicate attachment: ${extra.map { it.id }}",
            extra.none { it.type == "attachment" }
        )
    }

    @Test
    fun theRewardNeverOffersAnArmourLayerAlreadyWorn() {
        // Asserts the actual rule — nothing already worn comes back — rather than "no armour card
        // at all", which was only equivalent while those five were the entire layer pool. Adding
        // the capes made the old proxy fail without anything being wrong.
        val worn = listOf(
            "armor_gauntlets", "armor_boots", "armor_coif",
            "armor_greaves", "armor_spaulders"
        )
        val extra = GameViewModel.rewardChoices(
            state().copy(extraArmors = worn),
            kotlin.random.Random(7)
        )
        assertTrue(
            "offered a layer already worn: ${extra.map { it.itemId }}",
            extra.none { it.type == "armor" && it.itemId in worn }
        )
    }
}
