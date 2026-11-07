package dev.sriramcodessw.versesync.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import dev.sriramcodessw.versesync.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingClientWrapper(private val context: Context) {

    companion object {
        const val PRO_PRODUCT_ID = "pro_subscription"
        val TIP_PRODUCT_IDS = setOf("small_tip", "big_tip")
        private const val RECONNECT_DELAY_MS = 5_000L
    }

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails = _productDetails.asStateFlow()

    // Debug builds unlock Pro so features can be exercised without a Play purchase.
    // Release builds derive Pro only from a verified, purchased subscription.
    private val _isPro = MutableStateFlow(BuildConfig.DEBUG)
    val isPro = _isPro.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            if (processPurchases(purchases)) _isPro.value = true
        }
    }

    private val billingClient: BillingClient

    init {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry with a delay instead of reconnecting in a tight loop.
                handler.postDelayed({ startConnection() }, RECONNECT_DELAY_MS)
            }
        })
    }

    fun endConnection() {
        handler.removeCallbacksAndMessages(null)
        billingClient.endConnection()
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("small_tip")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("big_tip")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { _, queryProductDetailsResult ->
            _productDetails.value = queryProductDetailsResult.productDetailsList ?: emptyList()
        }
    }

    private fun queryPurchases() {
        // Subscriptions decide Pro status; this also acknowledges any not yet acknowledged.
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPro.value = BuildConfig.DEBUG || processPurchases(purchases)
            }
        }
        // Consume tips left over from an interrupted purchase so they are not refunded.
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases)
        }
    }

    /**
     * Acknowledges subscriptions and consumes tips. Google refunds purchases that are not
     * acknowledged within three days. Returns true when an active Pro subscription is present.
     */
    private fun processPurchases(purchases: List<Purchase>): Boolean {
        var hasPro = false
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.products.any { it in TIP_PRODUCT_IDS }) {
                billingClient.consumeAsync(
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { _, _ -> }
            } else if (purchase.products.contains(PRO_PRODUCT_ID)) {
                hasPro = true
                if (!purchase.isAcknowledged) {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    ) { }
                }
            }
        }
        return hasPro
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            // Subscriptions require an offer token or the flow fails with a developer error.
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
            productParams.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }
}
