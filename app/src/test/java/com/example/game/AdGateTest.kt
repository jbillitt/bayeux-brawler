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
     * The switch is a DEBUG affordance, not a master switch.
     *
     * It used to gate release builds too, and since a tester and a player run the same release
     * binary that meant the shipped app served no ads to anyone unless they found the developer
     * menu. A release build now serves ads to a non-purchasing player with the switch untouched;
     * a debug build still stays silent until it is turned on, which is the half worth keeping.
     */
    @Test
    fun theSwitchGatesDebugBuildsOnlyAndDefaultsOff() {
        assertTrue(
            "a shipped build must serve ads without anyone opening the developer menu",
            AdGate.adsAllowed(isDebug = false, purchased = false, enabled = false)
        )
        assertFalse(
            "a debug build stays silent until the switch is thrown",
            AdGate.adsAllowed(isDebug = true, purchased = false, enabled = false, testAdIds = true)
        )
        assertFalse("the runtime switch must default to off", AdGate.testAdsEnabled)
    }

    /**
     * The first session decides whether there is a second one, so no interstitial lands in it.
     */
    @Test
    fun noInterstitialBeforeTheGracePeriodIsOver() {
        AdGate.resetInterstitialClock()
        assertFalse(
            "death 3 is inside the grace period, cadence or not",
            AdGate.shouldShowInterstitial(3)
        )
        assertTrue(
            "death 6 is the first one eligible",
            AdGate.shouldShowInterstitial(AdGate.FIRST_INTERSTITIAL_AFTER_DEATHS)
        )
    }

    @org.junit.Before
    fun clearAdClock() = AdGate.resetInterstitialClock()

    /** Every third death, but not until the grace period is served — so 6 and 9, never 3. */
    @Test
    fun theInterstitialFiresOnEveryThirdDeathOnceTheGraceIsServed() {
        assertFalse(AdGate.shouldShowInterstitial(1))
        assertFalse(AdGate.shouldShowInterstitial(2))
        assertFalse("death 3 is on-cadence but inside the grace period", AdGate.shouldShowInterstitial(3))
        assertFalse(AdGate.shouldShowInterstitial(4))
        assertTrue(AdGate.shouldShowInterstitial(6))
        assertFalse(AdGate.shouldShowInterstitial(7))
        assertTrue(AdGate.shouldShowInterstitial(9))
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
        assertTrue("the first is always allowed", AdGate.shouldShowInterstitial(6, start))
        AdGate.markInterstitialShown(start)

        assertFalse(
            "an ad 30s after the last one is the churn trigger",
            AdGate.shouldShowInterstitial(9, start + 30_000L)
        )
        assertFalse(
            "still too soon just under the gap",
            AdGate.shouldShowInterstitial(9, start + AdGate.MIN_INTERSTITIAL_GAP_MS - 1)
        )
        assertTrue(
            "once the quiet period is served, the count rules again",
            AdGate.shouldShowInterstitial(9, start + AdGate.MIN_INTERSTITIAL_GAP_MS)
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
