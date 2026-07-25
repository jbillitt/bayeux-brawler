package com.example.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdGateTest {

    @Test
    fun debugBuildsNeverShowAds() {
        assertFalse(AdGate.adsAllowed(isDebug = true, purchased = false))
        assertFalse(AdGate.adsAllowed(isDebug = true, purchased = true))
    }

    @Test
    fun aPurchaseRemovesAds() {
        assertFalse(AdGate.adsAllowed(isDebug = false, purchased = true))
    }

    @Test
    fun aFreeReleasePlayerSeesAds() {
        assertTrue(AdGate.adsAllowed(isDebug = false, purchased = false))
    }

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
     * The unit-test JVM runs the debug variant, so this is the real guarantee that the build a
     * developer plays on their own device never requests an ad, whatever the profile says.
     */
    @Test
    fun theDebugBuildThisTestRunsInAllowsNoAds() {
        assertFalse("a debug build must never request an ad", AdGate.adsAllowedNow())
    }
}
