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

/** One way to pay, as shown on the unlock screen. */
data class PlanOption(
    val id: String,
    val title: String,
    /** Price from Google Play, e.g. "₹49". */
    val price: String,
    /** "/ month", "/ year", "once" etc. */
    val per: String,
    val note: String,
    val renews: Boolean,
    val highlight: Boolean = false
)

/** What the screens need to know about payment. */
data class ProState(
    /** Lifetime bought, or a subscription / pass is active. */
    val purchased: Boolean,
    val trialDaysLeft: Int,
    val lifetime: Boolean = false,
    val subscriptionActive: Boolean = false,
    val autoRenewing: Boolean = false,
    /** Plan last bought on this phone, for display. */
    val plan: String? = null,
    /** Ways to pay, in display order; empty until Google Play answers. */
    val plans: List<PlanOption> = emptyList(),
    /** False on builds not installed from Play, or when Play is not reachable. */
    val storeReady: Boolean = false,
    val pending: Boolean = false,
    val message: String? = null
) {
    val isPro: Boolean get() = purchased || trialDaysLeft > 0
    val showCountdown: Boolean get() = Trial.showCountdown(purchased, trialDaysLeft)
    /** Cheapest price shown in short texts ("from ₹49"). */
    val price: String? get() = plans.firstOrNull()?.price
}

/**
 * Payments through Google Play: a lifetime unlock (one-time product), auto-renewing monthly and
 * yearly subscriptions, and 1-month / 1-year passes (Play "prepaid" plans that do not renew).
 * Google Play takes the payment and tracks renewals and expiry; the app asks Play what is active and
 * keeps the answer so it also works offline for a while.
 */
class ProStore(context: Context, private val settings: Settings) : PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()

    private var lifetimeDetails: ProductDetails? = null
    private var subDetails: ProductDetails? = null
    @Volatile private var connecting = false
    /** Plan the person is buying right now, remembered when Play confirms. */
    @Volatile private var buyingPlan: String? = null
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<ProState> = _state.asStateFlow()

    init {
        if (settings.firstUseMillis == 0L) settings.firstUseMillis = System.currentTimeMillis()
    }

    private fun snapshot(): ProState {
        val now = settings.clockNow()
        val subOk = Trial.subscriptionStillValid(settings.subActive, settings.subConfirmedMillis, now)
        return ProState(
            purchased = settings.hasPaid,
            trialDaysLeft = Trial.daysLeft(settings.trialFromMillis, now),
            lifetime = settings.proPurchased,
            subscriptionActive = subOk,
            autoRenewing = subOk && settings.subAutoRenewing,
            plan = if (settings.proPurchased) LIFETIME else settings.subPlan.takeIf { subOk }
        )
    }

    private fun update(change: (ProState) -> ProState) {
        val f = snapshot()
        _state.value = change(
            _state.value.copy(
                purchased = f.purchased, trialDaysLeft = f.trialDaysLeft, lifetime = f.lifetime,
                subscriptionActive = f.subscriptionActive, autoRenewing = f.autoRenewing, plan = f.plan
            )
        )
    }

    /** Call when the app comes to the front: checks purchases and prices with Google Play. */
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

    private fun product(id: String, type: String) =
        QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()

    private fun queryAll() {
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(LIFETIME_ID, BillingClient.ProductType.INAPP))).build()
        ) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                lifetimeDetails = list.productDetailsList.firstOrNull()
                publishPlans()
            }
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(SUB_ID, BillingClient.ProductType.SUBS))).build()
        ) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                subDetails = list.productDetailsList.firstOrNull()
                publishPlans()
            }
        }
        restore(quiet = true)
    }

    /** The base plan offer (no extra Play trial: the app has its own 30-day trial). */
    private fun baseOffer(planId: String): ProductDetails.SubscriptionOfferDetails? =
        subDetails?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId && it.offerId == null }
            ?: subDetails?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }

    private fun offerPrice(planId: String): Pair<String, Long>? =
        baseOffer(planId)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.let { it.formattedPrice to it.priceAmountMicros }

    private fun publishPlans() {
        val plans = mutableListOf<PlanOption>()
        val monthly = offerPrice(MONTHLY)
        val yearly = offerPrice(YEARLY)
        monthly?.let { plans += PlanOption(MONTHLY, "Monthly", it.first, "/ month", "Renews every month. Cancel any time in Google Play.", renews = true) }
        yearly?.let {
            val saving = monthly?.let { m -> if (m.second > 0) 100 - (it.second * 100 / (m.second * 12)) else null }
            plans += PlanOption(
                YEARLY, "Yearly", it.first, "/ year",
                "Renews every year." + (if (saving != null && saving > 0) " Save $saving% vs monthly." else "") + " Cancel any time.",
                renews = true, highlight = true
            )
        }
        offerPrice(MONTH_PASS)?.let { plans += PlanOption(MONTH_PASS, "1-month pass", it.first, "once", "Pay once for 1 month. Does not renew.", renews = false) }
        offerPrice(YEAR_PASS)?.let { plans += PlanOption(YEAR_PASS, "1-year pass", it.first, "once", "Pay once for 1 year. Does not renew.", renews = false) }
        lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice?.let {
            plans += PlanOption(LIFETIME, "Lifetime", it, "once", "Pay once, yours forever on this Google account.", renews = false)
        }
        update { it.copy(plans = plans, storeReady = plans.isNotEmpty()) }
    }

    /** Ask Google Play what this Google account owns (also used by "Restore purchase"). */
    fun restore(quiet: Boolean = false) {
        if (!client.isReady) {
            if (!quiet) update { it.copy(message = "Google Play is not reachable right now. Try again when online.") }
            refresh()
            return
        }
        var answers = 0
        var found = false
        fun done() {
            answers++
            if (answers == 2 && !quiet) update {
                it.copy(message = if (it.purchased) "Your purchase is active. Thank you!" else "No purchase found for this Google account.")
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, purchases ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter { LIFETIME_ID in it.products }
                owned.forEach { handle(it) }
                // Only switch off when Play positively says nothing is owned (e.g. refunded).
                if (owned.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) settings.proPurchased = false
                else found = true
                update { it.copy(pending = owned.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
            }
            done()
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, purchases ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                // Play lists only subscriptions and passes that are still active (including grace period).
                val active = purchases.filter { SUB_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                active.forEach { handle(it) }
                if (active.isEmpty()) {
                    settings.subActive = false
                    settings.subAutoRenewing = false
                }
                update { it }
            }
            done()
        }
    }

    /** Opens Google Play's payment sheet for one plan. Returns false when Play is not available. */
    fun buy(activity: Activity, planId: String): Boolean {
        val params = if (planId == LIFETIME) {
            val d = lifetimeDetails ?: run { refresh(); return false }
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        } else {
            val d = subDetails ?: run { refresh(); return false }
            val offer = baseOffer(planId) ?: return false
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build()
        }
        buyingPlan = planId
        return client.launchBillingFlow(
            activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build()
        ).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun clearMessage() = update { it.copy(message = null) }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it, justBought = true) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(quiet = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> update { it.copy(message = "The payment did not go through. Nothing was charged.") }
        }
    }

    private fun handle(p: Purchase, justBought: Boolean = false) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (LIFETIME_ID in p.products) settings.proPurchased = true
                if (SUB_ID in p.products) {
                    settings.subActive = true
                    settings.subAutoRenewing = p.isAutoRenewing
                    settings.subConfirmedMillis = System.currentTimeMillis()
                    if (justBought) buyingPlan?.takeIf { it != LIFETIME }?.let { settings.subPlan = it }
                }
                update { it.copy(pending = false, message = if (justBought) "Thank you! Everything is unlocked." else it.message) }
                if (!p.isAcknowledged) {
                    // Unacknowledged purchases are refunded by Google after 3 days.
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
                }
            }
            Purchase.PurchaseState.PENDING -> update {
                it.copy(pending = true, message = "Payment is pending. The app unlocks as soon as Google Play confirms it.")
            }
            else -> {}
        }
    }

    companion object {
        /** One-time product in Play Console. */
        const val LIFETIME_ID = "wealth_manager_pro"
        /** Subscription product in Play Console, with the four base plans below. */
        const val SUB_ID = "wealth_manager_premium"
        const val MONTHLY = "monthly"
        const val YEARLY = "yearly"
        const val MONTH_PASS = "month-pass"
        const val YEAR_PASS = "year-pass"
        const val LIFETIME = "lifetime"

        fun planName(id: String?): String = when (id) {
            MONTHLY -> "Monthly subscription"
            YEARLY -> "Yearly subscription"
            MONTH_PASS -> "1-month pass"
            YEAR_PASS -> "1-year pass"
            LIFETIME -> "Lifetime"
            else -> "Subscription"
        }

        const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=$SUB_ID&package=in.wealthmanager.app"
    }
}
