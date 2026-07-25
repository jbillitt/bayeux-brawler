package com.example.game

import android.app.Activity
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Two placements: an interstitial on death, and an opt-in rewarded ad on the level-up screen.
 *
 * Two rules this file exists to enforce:
 *  1. **Consent before ads.** UMP consent is gathered first; the ads SDK is only initialised after.
 *     Serving personalised ads in the EEA/UK without a certified form is a policy violation.
 *  2. **No request is ever constructed when [AdGate.adsAllowedNow] is false** — not loaded and then
 *     suppressed. In a debug build nothing here touches the network at all.
 */
object Ads {

    private const val TAG = "Ads"

    private var initialised = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    /**
     * Gather consent, then initialise the SDK, then preload. Call from the Activity.
     *
     * Ordering is load-bearing: do not move the MobileAds.initialize call ahead of the consent
     * callback.
     */
    fun init(activity: Activity) {
        if (!AdGate.adsAllowedNow()) {
            Log.i(TAG, "ads disabled (debug build or ad-free purchased) — SDK not initialised")
            return
        }
        if (BuildConfig.USING_TEST_AD_IDS) {
            Log.w(TAG, "USING GOOGLE TEST AD IDS — this build earns no revenue. See docs/GOING-LIVE-ADS-AND-IAP.md")
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "consent form error: ${formError.message}")
                    }
                    // Whatever the outcome, only proceed if consent now permits requests.
                    if (consent.canRequestAds()) initialiseSdk(activity)
                }
            },
            { requestError ->
                Log.w(TAG, "consent info update failed: ${requestError.message}")
            }
        )
    }

    private fun initialiseSdk(activity: Activity) {
        if (initialised) return
        initialised = true
        MobileAds.initialize(activity) {
            loadInterstitial(activity)
            loadRewarded(activity)
        }
    }

    private fun loadInterstitial(activity: Activity) {
        if (!AdGate.adsAllowedNow() || !initialised) return
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    Log.w(TAG, "interstitial load failed: ${error.message}")
                }
            }
        )
    }

    private fun loadRewarded(activity: Activity) {
        if (!AdGate.adsAllowedNow() || !initialised) return
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    Log.w(TAG, "rewarded load failed: ${error.message}")
                }
            }
        )
    }

    /** True if a rewarded ad is actually ready, so the UI can hide the offer rather than lie. */
    fun rewardedReady(): Boolean = AdGate.adsAllowedNow() && rewarded != null

    /** Show the interstitial if one is loaded. Never called mid-battle — only on the defeat screen. */
    fun showInterstitial(activity: Activity) {
        val ad = interstitial ?: return
        if (!AdGate.adsAllowedNow()) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial(activity) // preload the next one
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
            }
        }
        ad.show(activity)
    }

    /**
     * Show the opt-in rewarded ad. [onEarned] fires only on the SDK's *earned* callback — never on
     * dismissal, or the reward could be taken by closing the ad immediately.
     */
    fun showRewarded(activity: Activity, onEarned: () -> Unit) {
        val ad = rewarded ?: return
        if (!AdGate.adsAllowedNow()) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                loadRewarded(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
            }
        }
        ad.show(activity) { onEarned() }
    }
}
