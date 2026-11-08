package dev.sriramcodessw.versesync.ui.screens

import com.android.billingclient.api.ProductDetails
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class TipJarPriceTest {
    @Test
    fun oneTimeProduct_showsItsPrice() {
        val oneTime: ProductDetails.OneTimePurchaseOfferDetails = mock()
        whenever(oneTime.formattedPrice).thenReturn("$1.99")
        val product: ProductDetails = mock()
        whenever(product.oneTimePurchaseOfferDetails).thenReturn(oneTime)

        assertEquals("$1.99", displayPrice(product))
    }

    @Test
    fun subscription_showsThePriceOfItsFirstPhase() {
        // The subscription row used to be blank because only the one-time price was read.
        val phase: ProductDetails.PricingPhase = mock()
        whenever(phase.formattedPrice).thenReturn("$4.99")
        val phases: ProductDetails.PricingPhases = mock()
        whenever(phases.pricingPhaseList).thenReturn(listOf(phase))
        val offer: ProductDetails.SubscriptionOfferDetails = mock()
        whenever(offer.pricingPhases).thenReturn(phases)
        val product: ProductDetails = mock()
        whenever(product.subscriptionOfferDetails).thenReturn(listOf(offer))

        assertEquals("$4.99", displayPrice(product))
    }

    @Test
    fun productWithNoPriceInformation_showsNothing() {
        val product: ProductDetails = mock()

        assertEquals("", displayPrice(product))
    }
}
