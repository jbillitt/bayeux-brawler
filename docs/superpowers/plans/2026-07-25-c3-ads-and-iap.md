# C3 — Ads and IAP Implementation Plan

**Goal:** Free game, interstitial every few deaths, opt-in rewarded ad for a level-up bonus, one purchase that removes ads. Off entirely in debug builds.

**Depends on:** C1 (`GameProfile` — `adFreePurchased`, `totalDeaths` already exist).

## Before writing any code — read this

The app is live: `applicationId com.headspace.bayeuxbrawlers`, `versionCode 24`. These are real consequences, not hypotheticals.

1. **Real ad unit ids must never run in a debug build.** Clicking your own live ads is the standard route to an AdMob account suspension. The design below turns ads off entirely in debug. If that is ever relaxed, it must be Google's published test ad unit ids and nothing else.
2. **UMP consent is mandatory**, not optional polish. Serving personalised ads to EEA/UK users without a Google-certified consent form is a policy violation. The UMP SDK ships inside `play-services-ads`.
3. **The Play listing changes** — the "contains ads" declaration and the Data safety form both need updating before release.
4. **Ads affect the age rating and the families-policy position.** A cartoon medieval brawler may be judged child-appealing. Confirm before release, not after.

Items 2-4 are your calls to make, not mine. Task 5 stops and asks.

---

### Task 1: AdGate — the single kill switch

Everything that could show an ad asks this one object. One chokepoint, so a kill switch cannot be forgotten at a call site.

**Files:** create `app/src/main/java/com/example/game/AdGate.kt`; test `app/src/test/java/com/example/game/AdGateTest.kt`

- [ ] **Write the test first**

```kotlin
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
}
```

- [ ] **Run it, watch it fail, then implement**

```kotlin
package com.example.game

/**
 * The only thing that decides whether an ad may show. Every call site asks here, so the debug
 * and purchase kill switches cannot be missed at one of them.
 */
object AdGate {

    /** Deaths between interstitials. */
    const val ADS_PER_DEATHS = 3

    fun adsAllowed(isDebug: Boolean, purchased: Boolean): Boolean = !isDebug && !purchased

    fun shouldShowInterstitial(totalDeaths: Int): Boolean =
        totalDeaths > 0 && totalDeaths % ADS_PER_DEATHS == 0

    /** Convenience for callers: reads BuildConfig and the cached profile. */
    fun adsAllowedNow(): Boolean =
        adsAllowed(isDebug = BuildConfig.DEBUG, purchased = GameProfile.cached.adFreePurchased)
}
```

Add `import com.example.BuildConfig` — check the actual package of the generated `BuildConfig` first; it follows the module namespace, not `applicationId`.

- [ ] `gradle :app:testDebugUnitTest --tests "com.example.game.AdGateTest" --console=plain` → PASS
- [ ] Commit: `feat: AdGate — single chokepoint for every ad decision`

Note this task is fully testable with **no SDK dependency at all**. Do that on purpose: the gating logic is the part that can hurt you, and it should be green before AdMob is anywhere near the build.

---

### Task 2: Play Billing — the purchase

Do this before the ads. If billing lands first, the "remove ads" state is already trustworthy when ads arrive.

**Files:** `app/build.gradle.kts`, create `app/src/main/java/com/example/game/Billing.kt`

- [ ] Add `com.android.billingclient:billing-ktx` to `gradle/libs.versions.toml` and the app module.
- [ ] One non-consumable product, id `remove_ads`. Create it in Play Console first — the code cannot be tested against a product that does not exist.
- [ ] `Billing.kt`: connect, query `queryPurchasesAsync` on every launch, and on an owned or newly-acknowledged `remove_ads`, call `GameProfile.setAdFree(true)`. Acknowledge within three days or Play auto-refunds the purchase.
- [ ] Query-on-launch is what restores the purchase after a reinstall. There is no separate restore button to write.
- [ ] "Remove Ads" entry on the start screen, hidden once `GameProfile.cached.adFreePurchased` is true.
- [ ] Test against a licence-tester account in Play Console's internal testing track. There is no way to verify billing from a unit test; say so in the completion report rather than implying coverage.
- [ ] Commit: `feat: play billing remove_ads purchase`

---

### Task 3: AdMob + UMP consent

**Files:** `app/build.gradle.kts`, `AndroidManifest.xml`, create `app/src/main/java/com/example/game/Ads.kt`

- [ ] Add `play-services-ads`; put the AdMob app id in the manifest as required.
- [ ] **UMP consent runs first, before any ad request.** Gather consent at launch, then initialise the ads SDK. Do not reorder these.
- [ ] `Ads.kt` loads one interstitial and one rewarded ad, both behind `AdGate.adsAllowedNow()`. If it returns false, never construct a request at all — do not load-then-suppress.
- [ ] Ad unit ids: read from `BuildConfig` fields so debug and release cannot share them. Debug gets Google's published test ids; release gets the real ones. Never hardcode a live id in source.
- [ ] Commit: `feat: admob with UMP consent, gated behind AdGate`

---

### Task 4: Wire the two placements

**Files:** `GameViewModel.kt` (death path, and `pendingChoices` at `:1702`), `MainActivity.kt` (level-up screen at `:1032`)

- [ ] **Interstitial on death.** Where C1 calls `GameProfile.recordDeath()`, afterwards check `AdGate.adsAllowedNow() && AdGate.shouldShowInterstitial(GameProfile.cached.totalDeaths)`. Show it on the defeat screen, never mid-battle.
- [ ] **Rewarded ad on level-up.** An opt-in button on the level-up screen. On the SDK's *earned* callback — never on dismissal — grant one of: an extra attachment card, an extra card in the choice pool, or a wider armour selection for that choice. All three are additions to `pendingChoices`, so the reward is data, not a new mechanic.
- [ ] Test the grant path without the SDK: extract `fun rewardChoices(state: UiState): List<LevelUpChoice>` and unit-test that it returns extra cards. The SDK callback just calls it.
- [ ] Commit: `feat: interstitial on death, opt-in rewarded ad on level-up`

---

### Task 5: Release gate — stop and ask

- [ ] `gradle :app:testDebugUnitTest --console=plain` → PASS
- [ ] Build a **debug** APK, play it, confirm **zero** ad requests. Check logcat for `Ads` tags. This is the check that protects the AdMob account.
- [ ] Build a release APK against **test** ad ids and confirm both placements appear.
- [ ] **Then stop.** Before any live ad id ships, get the user's explicit go-ahead on: the Play "contains ads" declaration, the Data safety form update, and the age-rating/families-policy answer. Do not switch to live ids unprompted.

---

## Notes

- Tasks 1 and 2 have real test coverage. Task 3 has essentially none — ad SDKs are not unit-testable — so the gating logic in Task 1 is deliberately pure functions that can be tested without it.
- `ADS_PER_DEATHS = 3` is one constant, tunable on device.
- No abstraction layer over AdMob. One network, one object; if a second network ever matters, extract then.
