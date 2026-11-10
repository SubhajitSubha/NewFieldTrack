package com.example.fieldtrack.data.billing.store

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long one payment covers. Farm income comes in seasons, so a season is offered next to a year. */
enum class StorePeriod { Season, Year }

/** One way of paying, as the store words and prices it for this person's country. */
data class StoreOffer(
    val id: String,
    val period: StorePeriod,
    /** The price as the store writes it, in the person's own currency. Never worked out here. */
    val priceText: String,
    /** The founding price: kept for as long as it is renewed. */
    val founding: Boolean,
)

/** A purchase the store confirms, to be handed to the server to check. */
data class StorePurchase(val productId: String, val token: String, val acknowledged: Boolean)

/**
 * The store, behind one small door.
 *
 * Everything the app knows about payment goes through here, so it can run, be tested and be
 * sideloaded with no store at all: with none, [offers] stays empty and the free plan is simply
 * the plan. The store is never trusted for the answer to "has this business paid": a purchase is
 * handed to our server, which asks the store itself and writes the result for every phone to read.
 */
interface StoreBilling {
    val offers: StateFlow<List<StoreOffer>>

    /** Purchases that went through while the app was open. */
    val completed: SharedFlow<StorePurchase>

    /** Connects and reads the prices. False when there is no store to talk to. */
    suspend fun connect(): Boolean

    /** Opens the store's own payment sheet for [offer], tying the purchase to [orgId]. */
    fun buy(activity: Activity, offer: StoreOffer, orgId: String)

    /** Everything still active for this account, for restoring a purchase and for renewals. */
    suspend fun active(): List<StorePurchase>

    /** Tells the store the purchase has been taken, as it requires within three days. */
    suspend fun acknowledge(purchase: StorePurchase)
}

/** Google Play's billing, as the platform provides it. */
class PlayBilling(private val context: Context) : StoreBilling {

    private val _offers = MutableStateFlow<List<StoreOffer>>(emptyList())
    override val offers: StateFlow<List<StoreOffer>> = _offers.asStateFlow()

    private val _completed = MutableSharedFlow<StorePurchase>(extraBufferCapacity = 4)
    override val completed: SharedFlow<StorePurchase> = _completed.asSharedFlow()

    private var details: ProductDetails? = null
    private val tokens = mutableMapOf<String, String>()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                _completed.tryEmit(StorePurchase(p.products.firstOrNull().orEmpty(), p.purchaseToken, p.isAcknowledged))
            }
        }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    override suspend fun connect(): Boolean = runCatching {
        if (!client.isReady) {
            val ready = CompletableDeferred<Boolean>()
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    ready.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() { ready.complete(false) }
            })
            if (!ready.await()) return@runCatching false
        }
        val query = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build())
        ).build()
        val found = client.queryProductDetails(query).productDetailsList?.firstOrNull() ?: return@runCatching false
        details = found
        tokens.clear()
        _offers.value = found.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val period = when (offer.basePlanId) {
                BASE_SEASON -> StorePeriod.Season
                BASE_YEAR -> StorePeriod.Year
                else -> return@mapNotNull null
            }
            val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
            tokens[offer.offerToken] = offer.offerToken
            StoreOffer(offer.offerToken, period, price, founding = FOUNDING_TAG in offer.offerTags)
        }.let { all ->
            // Where a founding offer exists for a period it is the one shown; the ordinary one stays behind it.
            all.groupBy { it.period }.flatMap { (_, same) -> listOfNotNull(same.firstOrNull { it.founding } ?: same.firstOrNull()) }
        }
        _offers.value.isNotEmpty()
    }.getOrDefault(false)

    override fun buy(activity: Activity, offer: StoreOffer, orgId: String) {
        val product = details ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.id).build())
            )
            // The business this pays for, so the server can check the purchase belongs to it.
            .setObfuscatedAccountId(orgId)
            .build()
        runCatching { client.launchBillingFlow(activity, params) }
    }

    override suspend fun active(): List<StorePurchase> = runCatching {
        if (!client.isReady && !connect()) return@runCatching emptyList()
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
            .purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .map { StorePurchase(it.products.firstOrNull().orEmpty(), it.purchaseToken, it.isAcknowledged) }
    }.getOrDefault(emptyList())

    override suspend fun acknowledge(purchase: StorePurchase) {
        if (purchase.acknowledged) return
        runCatching { client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()) }
    }

    companion object {
        /** The subscription in the store, with a base plan for a season and one for a year. Named here, created in the store's console. */
        const val PRODUCT = "fieldtrack_business"
        const val BASE_SEASON = "season"
        const val BASE_YEAR = "year"
        const val FOUNDING_TAG = "founding"
    }
}

/** For builds and tests with no store: nothing to buy, nothing connected. */
class NoStore : StoreBilling {
    override val offers: StateFlow<List<StoreOffer>> = MutableStateFlow(emptyList())
    override val completed: SharedFlow<StorePurchase> = MutableSharedFlow()
    override suspend fun connect() = false
    override fun buy(activity: Activity, offer: StoreOffer, orgId: String) = Unit
    override suspend fun active(): List<StorePurchase> = emptyList()
    override suspend fun acknowledge(purchase: StorePurchase) = Unit
}
