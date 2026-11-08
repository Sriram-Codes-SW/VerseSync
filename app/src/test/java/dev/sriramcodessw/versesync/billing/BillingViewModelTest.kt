package dev.sriramcodessw.versesync.billing

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class BillingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var applicationMock: Application
    private lateinit var billingClientWrapperMock: BillingClientWrapper
    private lateinit var viewModel: BillingViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        applicationMock = mock()
        billingClientWrapperMock = mock()
        viewModel = BillingViewModel(applicationMock, billingClientWrapperMock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun launchPurchaseFlow_shouldCallBillingClientWrapper() {
        val activity: Activity = mock()
        val productDetails: ProductDetails = mock()
        whenever(billingClientWrapperMock.launchPurchaseFlow(activity, productDetails)).thenReturn(true)

        val launched = viewModel.launchPurchaseFlow(activity, productDetails)

        verify(billingClientWrapperMock).launchPurchaseFlow(activity, productDetails)
        assertTrue(launched)
    }

    @Test
    fun launchPurchaseFlow_reportsWhenThePurchaseScreenCouldNotOpen() {
        val activity: Activity = mock()
        val productDetails: ProductDetails = mock()
        whenever(billingClientWrapperMock.launchPurchaseFlow(activity, productDetails)).thenReturn(false)

        assertFalse(viewModel.launchPurchaseFlow(activity, productDetails))
    }

    @Test
    fun refreshPurchases_asksTheWrapperToRecheckSubscriptions() {
        viewModel.refreshPurchases()

        verify(billingClientWrapperMock).refreshPurchases()
    }
}
