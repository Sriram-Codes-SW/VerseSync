package dev.sriramcodessw.versesync.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.billingclient.api.ProductDetails
import dev.sriramcodessw.versesync.billing.BillingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipJarScreen(
    onBack: () -> Unit,
    viewModel: BillingViewModel,
) {
    val productDetails by viewModel.productDetails.collectAsState()
    val activity = LocalContext.current as Activity

    fun buy(product: ProductDetails) {
        if (!viewModel.launchPurchaseFlow(activity, product)) {
            Toast
                .makeText(
                    activity,
                    "This purchase is not available right now. Check your connection and try again.",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tip Jar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(productDetails) { product ->
                ListItem(
                    headlineContent = { Text(product.name) },
                    supportingContent = { Text(product.description) },
                    trailingContent = { Text(displayPrice(product)) },
                    modifier =
                        Modifier
                            .clickable { buy(product) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${product.name} for ${displayPrice(product)}"
                            },
                )
            }
        }
    }
}

/**
 * The price to show for a product. A one-time product has one price. A subscription has its price
 * in the first pricing phase of its first offer, which is the phase the purchase starts with.
 * Reading only the one-time price left the subscription row without a price.
 */
internal fun displayPrice(product: ProductDetails): String =
    product.oneTimePurchaseOfferDetails?.formattedPrice
        ?: product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
        ?: ""
