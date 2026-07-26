package com.example.game

import com.example.BuildConfig

/**
 * The only thing that decides whether an ad may show. Every call site asks here, so the debug and
 * purchase kill switches cannot be missed at one of them.
 *
 * Why a debug kill switch at all: clicking your own live ads is the standard route to an AdMob
 * account suspension, and a debug build is exactly where a developer clicks things.
 */
object AdGate {

    /** Deaths between interstitials. Tunable on device. */
    const val ADS_PER_DEATHS = 3

    /**
     * Runtime master switch, OFF by default so testers see no ads at all. Turned on from the
     * burger menu ("Enable test ads") when the ad placements themselves need exercising.
     * Deliberately not persisted: a tester who enables it should get a clean slate next launch.
     */
    @Volatile
    var testAdsEnabled: Boolean = false

    /**
     * The debug kill switch used to outrank the toggle unconditionally, which made "Enable test
     * ads" a button that did nothing at all in the only kind of build a tester ever runs. The
     * switch may now open the debug lock, but ONLY when the build is wired to Google's own test
     * ad units — those serve fake ads, earn nothing, and are safe to click. A debug build carrying
     * REAL ad unit ids stays locked shut whatever the toggle says: that is the case the kill
     * switch exists for, since clicking your own live ads is how AdMob accounts get suspended.
     */
    fun adsAllowed(
        isDebug: Boolean,
        purchased: Boolean,
        enabled: Boolean = testAdsEnabled,
        testAdIds: Boolean = BuildConfig.USING_TEST_AD_IDS
    ): Boolean = enabled && !purchased && (!isDebug || testAdIds)

    fun shouldShowInterstitial(totalDeaths: Int): Boolean =
        totalDeaths > 0 && totalDeaths % ADS_PER_DEATHS == 0

    /** Convenience for callers: reads BuildConfig and the cached profile. */
    fun adsAllowedNow(): Boolean =
        adsAllowed(isDebug = BuildConfig.DEBUG, purchased = GameProfile.cached.adFreePurchased)
}
