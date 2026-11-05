package com.xbmarket.mobile.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
    val inGamePass: Boolean = false,
    /** ISO 8601, untuk urutan Terbaru. */
    val releaseDate: String? = null,
) {
    val isGame: Boolean get() = kind == null || kind == "Game"
}

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
        val releaseDate: String? = null,
        val includedWithPassesProductIds: List<String> = emptyList(),
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

    /**
     * Satu halaman (±25 produk). [continuation] null = halaman pertama; hasil `continuation` null = habis.
     * Platform dan genre dari [filters] disaring server; [DealFilters.minDiscount] menghentikan paging lebih awal.
     */
    suspend fun page(region: Region, filters: DealFilters = DealFilters(), continuation: String? = null): DealsPage =
        withContext(Dispatchers.IO) {
        val encoded = encodeFilters(filters)
        val minDiscount = filters.minDiscount.coerceAtLeast(1)
        val body = json.encodeToString(
            BrowseRequest(Filters = encoded, EncodedFilters = encoded, EncodedCT = continuation),
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
            if (p == null || p.currency.isEmpty() || p.listPrice >= p.msrp || p.discountPercentage < minDiscount) return@mapNotNull null
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
                inGamePass = s.includedWithPassesProductIds.isNotEmpty(),
                releaseDate = s.releaseDate,
            )
        }
        // Urutan diskon menurun: satu produk di bawah batas berarti sisa halaman juga di bawah batas.
        val reachedEnd = all.isEmpty() || all.any { (_, p) -> p != null && p.discountPercentage < minDiscount }
        DealsPage(deals, response.channels[CHANNEL]?.encodedCT?.takeUnless { reachedEnd })
    }

    private fun encodeFilters(filters: DealFilters): String {
        fun choices(id: String, values: Collection<String>) = buildJsonObject {
            put("id", id)
            putJsonArray("choices") { values.forEach { v -> add(buildJsonObject { put("id", v) }) } }
        }
        val obj = buildJsonObject {
            put("orderby", choices("orderby", listOf("DiscountPercentage desc")))
            put("Price", choices("Price", listOf("OnSale")))
            if (filters.platforms.isNotEmpty()) put("PlayWith", choices("PlayWith", filters.platforms))
            if (filters.genres.isNotEmpty()) put("Genre", choices("Genre", filters.genres))
        }
        return Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val CHANNEL = "DEALS"
    }
}
