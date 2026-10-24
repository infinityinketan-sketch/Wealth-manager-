package com.example.ledger.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.ledger.core.Trial
import com.example.ledger.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the screens need to know about Pro. */
data class ProState(
    val purchased: Boolean,
    val trialDaysLeft: Int,
    /** Price from Google Play (e.g. "₹149"), or null until Play answers. */
    val price: String? = null,
    /** False on builds not installed from Play, or when Play is not reachable. */
    val storeReady: Boolean = false,
    val pending: Boolean = false,
    val message: String? = null
) {
    val isPro: Boolean get() = purchased || trialDaysLeft > 0
    val showCountdown: Boolean get() = Trial.showCountdown(purchased, trialDaysLeft)
}

/**
 * One-time "Pro" unlock through Google Play. Google Play handles the payment; the app only learns
 * "bought / not bought". The answer is cached so Pro keeps working with no connection.
 */
class ProStore(context: Context, private val settings: Settings) : PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var details: ProductDetails? = null
    @Volatile private var connecting = false
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<ProState> = _state.asStateFlow()

    init {
        if (settings.firstUseMillis == 0L) settings.firstUseMillis = System.currentTimeMillis()
    }

    private fun snapshot(): ProState = ProState(
        purchased = settings.proPurchased,
        trialDaysLeft = Trial.daysLeft(settings.firstUseMillis, System.currentTimeMillis())
    )

    private fun update(change: (ProState) -> ProState) {
        val fresh = snapshot()
        _state.value = change(_state.value.copy(purchased = fresh.purchased, trialDaysLeft = fresh.trialDaysLeft))
    }

    /** Call when the app comes to the front: checks the purchase and the price with Google Play. */
    fun refresh() {
        update { it }
        if (client.isReady) { queryAll(); return }
        if (connecting) return
        connecting = true
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) queryAll()
                    else update { it.copy(storeReady = false) }
                }
                override fun onBillingServiceDisconnected() {
                    connecting = false
                    update { it.copy(storeReady = false) }
                }
            })
        } catch (e: Exception) {
            connecting = false
            update { it.copy(storeReady = false) }
        }
    }

    private fun queryAll() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { result, list ->
            val d = list.productDetailsList.firstOrNull()
            if (result.responseCode == BillingClient.BillingResponseCode.OK && d != null) {
                details = d
                update { it.copy(storeReady = true, price = d.oneTimePurchaseOfferDetails?.formattedPrice) }
            }
        }
        restore(quiet = true)
    }

    /** Ask Google Play which purchases this Google account owns (also used by "Restore purchase"). */
    fun restore(quiet: Boolean = false) {
        if (!client.isReady) {
            if (!quiet) update { it.copy(message = "Google Play is not reachable right now. Try again when online.") }
            refresh()
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.filter { PRODUCT_ID in it.products }
            owned.forEach { handle(it) }
            // Only switch Pro off when Play positively says nothing is owned (e.g. refunded).
            if (owned.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                if (settings.proPurchased) settings.proPurchased = false
                update { it.copy(pending = owned.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
            }
            if (!quiet) update {
                it.copy(message = if (it.purchased) "Pro is active on this phone. Thank you!" else "No Pro purchase found for this Google account.")
            }
        }
    }

    /** Opens Google Play's own payment sheet. Returns false when Play is not available. */
    fun buy(activity: Activity): Boolean {
        val d = details ?: run { refresh(); return false }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun clearMessage() = update { it.copy(message = null) }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.filter { PRODUCT_ID in it.products }?.forEach { handle(it) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(quiet = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> update { it.copy(message = "The payment did not go through. Nothing was charged.") }
        }
    }

    private fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                settings.proPurchased = true
                update { it.copy(pending = false) }
                if (!p.isAcknowledged) {
                    // Unacknowledged purchases are refunded by Google after 3 days.
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                    ) { }
                }
            }
            Purchase.PurchaseState.PENDING -> update {
                it.copy(pending = true, message = "Payment is pending. Pro unlocks as soon as Google Play confirms it.")
            }
            else -> {}
        }
    }

    companion object {
        const val PRODUCT_ID = "wealth_manager_pro"
    }
}
