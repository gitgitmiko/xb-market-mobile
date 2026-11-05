package com.xbmarket.mobile.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger

data class Deal(
    val id: String,
    val title: String,
    val image: String?,
    val kind: String?,
    val currency: String,
    val price: Double,
    val msrp: Double,
    val discountPercent: Int,
    /** Format "MM/dd/yyyy HH:mm:ss" UTC, seperti dari server. */
    val endDateUtc: String?,
)

data class DealsPage(val deals: List<Deal>, val continuation: String?)

/**
 * Daftar game diskon per region dari API browse xbox.com (emerald.xboxservices.com),
 * diurutkan dari diskon terbesar. Region ditentukan oleh locale `en-{market}`.
 *
 * Filter `Price=OnSale` hanya dihormati server di sebagian market; di market lain hasil
 * tetap terurut diskon, jadi halaman dihentikan begitu diskon mencapai 0.
 */
class XboxDeals(private val client: OkHttpClient = Http.client) {

    @Serializable
    private data class BrowseRequest(
        val Filters: String,
        val ReturnFilters: Boolean = false,
        val ChannelKeyToBeUsedInResponse: String = CHANNEL,
        val EncodedFilters: String,
        val ChannelId: String = "",
        val EncodedCT: String? = null,
    )

    @Serializable
    private data class BrowseResponse(
        val channels: Map<String, Channel> = emptyMap(),
        val productSummaries: List<Summary> = emptyList(),
    )

    @Serializable
    private data class Channel(val encodedCT: String? = null)

    @Serializable
    private data class Summary(
        val productId: String,
        val title: String = "",
        val productKind: String? = null,
        val images: Images? = null,
        val specificPrices: SpecificPrices? = null,
    )

    @Serializable
    private data class Images(val boxArt: ImageRef? = null, val poster: ImageRef? = null)

    @Serializable
    private data class ImageRef(val url: String? = null)

    @Serializable
    private data class SpecificPrices(val purchaseable: List<PriceInfo> = emptyList())

    @Serializable
    private data class PriceInfo(
        val listPrice: Double = 0.0,
        val msrp: Double = 0.0,
        val discountPercentage: Double = 0.0,
        val currency: String = "",
        val endDateUtc: String? = null,
    )

    // encodeDefaults: server menolak (400) bila ReturnFilters/ChannelId tidak dikirim; explicitNulls=false membuang EncodedCT kosong.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val cv = AtomicInteger(0)

    /** Satu halaman (±25 produk). [continuation] null = halaman pertama; hasil `continuation` null = habis. */
    suspend fun page(region: Region, continuation: String? = null): DealsPage = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            BrowseRequest(Filters = FILTERS, EncodedFilters = FILTERS, EncodedCT = continuation),
        )
        val request = Request.Builder()
            .url("https://emerald.xboxservices.com/xboxcomfd/browse?locale=en-${region.market}")
            .header("x-ms-api-version", "1.1")
            .header("MS-CV", "xbmarket0000000.${cv.incrementAndGet() % 999 + 1}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error("Xbox HTTP ${r.code}")
            json.decodeFromString<BrowseResponse>(r.body!!.string())
        }
        val all = response.productSummaries.map { s ->
            val p = s.specificPrices?.purchaseable?.firstOrNull()
            s to p
        }
        val deals = all.mapNotNull { (s, p) ->
            if (p == null || p.currency.isEmpty() || p.listPrice >= p.msrp || p.discountPercentage < 1) return@mapNotNull null
            Deal(
                id = s.productId,
                title = s.title,
                image = s.images?.boxArt?.url ?: s.images?.poster?.url,
                kind = s.productKind,
                currency = p.currency,
                price = p.listPrice,
                msrp = p.msrp,
                discountPercent = p.discountPercentage.toInt(),
                endDateUtc = p.endDateUtc,
            )
        }
        // Urutan diskon menurun: satu produk tanpa diskon berarti sisa halaman juga tanpa diskon.
        val reachedEnd = all.isEmpty() || all.any { (_, p) -> p != null && p.discountPercentage < 1 }
        DealsPage(deals, response.channels[CHANNEL]?.encodedCT?.takeUnless { reachedEnd })
    }

    private companion object {
        const val CHANNEL = "DEALS"
        val FILTERS: String = Base64.encodeToString(
            """{"orderby":{"id":"orderby","choices":[{"id":"DiscountPercentage desc"}]},"Price":{"id":"Price","choices":[{"id":"OnSale"}]}}"""
                .toByteArray(),
            Base64.NO_WRAP,
        )
    }
}
