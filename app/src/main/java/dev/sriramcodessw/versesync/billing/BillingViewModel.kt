package dev.sriramcodessw.versesync.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.StateFlow

class BillingViewModel(
    application: Application,
    private val billingClientWrapper: BillingClientWrapper = BillingClientWrapper(application),
) : AndroidViewModel(application) {
    val productDetails: StateFlow<List<ProductDetails>> = billingClientWrapper.productDetails
    val isPro: StateFlow<Boolean> = billingClientWrapper.isPro

    init {
        billingClientWrapper.startConnection()
    }

    override fun onCleared() {
        billingClientWrapper.endConnection()
        super.onCleared()
    }

    /** Returns false when the purchase screen could not be opened. */
    fun launchPurchaseFlow(
        activity: Activity,
        productDetails: ProductDetails,
    ): Boolean = billingClientWrapper.launchPurchaseFlow(activity, productDetails)

    /** Re-checks subscriptions, so an expired one stops unlocking Pro without a restart. */
    fun refreshPurchases() {
        billingClientWrapper.refreshPurchases()
    }
}

class BillingViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BillingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BillingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
