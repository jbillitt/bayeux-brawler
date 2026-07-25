package com.example.game

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One non-consumable purchase: [PRODUCT_REMOVE_ADS]. There is deliberately no abstraction layer —
 * one store, one product.
 *
 * The purchase is restored by querying owned purchases on every launch, which is also what recovers
 * it after a reinstall, so there is no separate "restore purchases" button to write.
 *
 * Nothing here is unit-testable: BillingClient needs a real Play Store on a real device signed in
 * with a licence-tester account. Treat [GameProfile.adFreePurchased] as the tested surface.
 */
object Billing {

    const val PRODUCT_REMOVE_ADS = "remove_ads"
    private const val TAG = "Billing"

    private var client: BillingClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cached details for the store row, so the UI can show the real localised price. */
    @Volatile
    var removeAdsPrice: String? = null
        private set

    fun init(context: Context) {
        if (client != null) return
        val c = BillingClient.newBuilder(context.applicationContext)
            .enablePendingPurchases()
            .setListener { result, purchases ->
                // Fires for purchases completed in this session.
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    scope.launch { purchases.forEach { grantIfRemoveAds(it) } }
                }
            }
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "billing setup failed: ${result.debugMessage}")
                    return
                }
                scope.launch {
                    refreshOwnedPurchases()
                    loadPrice()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "billing disconnected")
            }
        })
    }

    /**
     * The restore path. Anything the account already owns is granted here, on every launch.
     */
    private suspend fun refreshOwnedPurchases() {
        val c = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = c.queryPurchasesAsync(params)
        result.purchasesList.forEach { grantIfRemoveAds(it) }
    }

    private suspend fun loadPrice() {
        val c = client ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_REMOVE_ADS)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val details: List<ProductDetails> = c.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ).productDetailsList.orEmpty()
        removeAdsPrice = details.firstOrNull()?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    /**
     * Grant, then acknowledge. Play auto-refunds an unacknowledged purchase after three days, so the
     * acknowledgement is not optional bookkeeping.
     */
    private suspend fun grantIfRemoveAds(purchase: Purchase) {
        if (PRODUCT_REMOVE_ADS !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        GameProfile.setAdFree(true)

        if (!purchase.isAcknowledged) {
            val c = client ?: return
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = c.acknowledgePurchase(ack)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    /** Launch the store's own purchase sheet. No-op if billing never connected. */
    fun purchaseRemoveAds(activity: Activity) {
        val c = client ?: return
        scope.launch {
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val details = c.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
            ).productDetailsList.orEmpty().firstOrNull()
            if (details == null) {
                Log.w(TAG, "$PRODUCT_REMOVE_ADS not found — is it created and active in Play Console?")
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            // launchBillingFlow must run on the main thread.
            activity.runOnUiThread { c.launchBillingFlow(activity, params) }
        }
    }
}
