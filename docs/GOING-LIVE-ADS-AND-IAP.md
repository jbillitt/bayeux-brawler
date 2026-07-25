# Going live with ads and IAP — what needs changing when the real keys arrive

Written 2026-07-25, when C3 shipped against **Google's public test ad ids** and an
**uncreated Play product**. Everything below is what is deliberately *not* real yet.

The app is already published: `applicationId com.headspace.bayeuxbrawlers`. Ads are a
change to a live listing, not a greenfield feature.

---

## 1. The three ad ids — configuration, not code

`app/build.gradle.kts` reads all three from Gradle properties and falls back to Google's
test ids. **Do not edit the source to go live.** Put the real values in `gradle.properties`
(which is gitignored — check before committing) or pass them on the command line:

```properties
ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
ADMOB_REWARDED_ID=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
```

Current test values (these earn nothing, and are safe to click):

| Property | Test value in use |
|---|---|
| `ADMOB_APP_ID` | `ca-app-pub-3940256099942544~3347511713` |
| `ADMOB_INTERSTITIAL_ID` | `ca-app-pub-3940256099942544/1033173712` |
| `ADMOB_REWARDED_ID` | `ca-app-pub-3940256099942544/5224354917` |

`BuildConfig.USING_TEST_AD_IDS` is true while the fallbacks are in use, and `Ads.init`
logs a loud warning under the `Ads` tag. When you go live that warning should disappear —
if it does not, the properties are not being picked up.

**Verify after switching:** `grep -r "3940256099942544" app/build/outputs/` on a release
build should return nothing.

## 2. The Play product does not exist yet

`Billing.PRODUCT_REMOVE_ADS` is `"remove_ads"`. Nothing has been created in Play Console,
so today `queryProductDetails` returns empty and the purchase flow logs
`remove_ads not found` and does nothing.

To make it real:
1. Play Console → Monetise → In-app products → create a **one-time** (non-consumable)
   product with product id exactly `remove_ads`.
2. Set a price, and **activate** it. An inactive product behaves identically to a missing one.
3. Add licence testers (Play Console → Setup → Licence testing) so purchases are free.
4. Upload the build to at least **internal testing**. Billing does not work for a build that
   Play has never seen, even with the right product id.

If you rename the product, change `PRODUCT_REMOVE_ADS` — it is referenced nowhere else.

## 3. What is genuinely untested

Be clear-eyed about this:

- **Billing has zero automated coverage.** `BillingClient` needs a real Play Store, a real
  signed-in account and a real product. Nothing in the unit suite exercises `Billing.kt`.
- **The ad SDK has zero automated coverage.** Ad SDKs are not unit-testable.
- **What *is* tested** is the part that can hurt you: `AdGate` (the kill switches and the
  every-third-death cadence) and `GameViewModel.rewardChoices` (the reward payout), both
  pure functions. See `AdGateTest`, `AdRewardTest`.
- Nothing in C1/C2/C3 has been run on a physical device by me at all.

## 4. Before the first release with real ids

These are your calls, not mine. All four are policy, not code:

- [ ] **Play "contains ads" declaration** — Play Console → App content → Ads. Must be
      updated before a build with real ads goes to production.
- [ ] **Data safety form** — the ads SDK collects an advertising ID and device data. The
      manifest now declares `com.google.android.gms.permission.AD_ID`. The form must match.
- [ ] **Age rating / families policy** — a cartoon medieval brawler may be judged
      child-appealing. If the app lands in a families programme, personalised ads and the ad
      SDK configuration both have extra constraints. Settle this *before* release.
- [ ] **UMP consent form** — must be created and published in the AdMob console
      (Privacy & messaging → European regulations). The code calls
      `loadAndShowConsentFormIfRequired`, but with no form published there is nothing to show
      and EEA/UK users get no consent gate. **This is the one most easily missed: the code
      being correct is not the same as the form existing.**

## 5. The debug kill switch — do not relax it

`AdGate.adsAllowed(isDebug, purchased)` returns false for *any* debug build. Clicking your
own live ads is the standard route to an AdMob account suspension, and a debug build is
exactly where you click things.

`AdGateTest.theDebugBuildThisTestRunsInAllowsNoAds` asserts this, and the unit suite runs the
debug variant — so that test failing means the protection is gone.

If it is ever relaxed to see ads in development, it must use the test ids above and nothing
else. Never a live id in a debug build.

## 6. Tuning

- `AdGate.ADS_PER_DEATHS = 3` — deaths between interstitials. One constant, in one place.
- The interstitial fires on the **defeat screen only**, never mid-battle
  (`GameViewModel.pendingInterstitial`, shown by `MainActivity`).
- The rewarded ad is **opt-in**, offered once per level-up screen, and only when an ad is
  genuinely loaded (`Ads.rewardedReady()`) — the offer hides rather than promising a reward
  that cannot be delivered. It pays out on the SDK's *earned* callback only, never dismissal.
