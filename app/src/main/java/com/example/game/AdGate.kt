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

    fun adsAllowed(isDebug: Boolean, purchased: Boolean): Boolean = !isDebug && !purchased

    fun shouldShowInterstitial(totalDeaths: Int): Boolean =
        totalDeaths > 0 && totalDeaths % ADS_PER_DEATHS == 0

    /** Convenience for callers: reads BuildConfig and the cached profile. */
    fun adsAllowedNow(): Boolean =
        adsAllowed(isDebug = BuildConfig.DEBUG, purchased = GameProfile.cached.adFreePurchased)
}
