package com.xbmarket.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger

/**
 * Katalog publik Microsoft Store (displaycatalog.mp.microsoft.com), endpoint yang sama
 * dengan yang dipakai Xbox Bargains untuk harga per region. Harga ditentukan oleh parameter `market`.
 */
class XboxCatalog(private val client: OkHttpClient = Http.client) {

    @Serializable
    private data class ProductsResponse(@SerialName("Products") val products: List<Product> = emptyList())

    @Serializable
    private data class Product(
        @SerialName("ProductId") val productId: String,
        @SerialName("LocalizedProperties") val localized: List<Localized> = emptyList(),
        @SerialName("DisplaySkuAvailabilities") val skus: List<SkuAvailability> = emptyList(),
    )

    @Serializable
    private data class Localized(
        @SerialName("ProductTitle") val title: String = "",
        @SerialName("PublisherName") val publisher: String? = null,
        @SerialName("ShortDescription") val shortDescription: String? = null,
        @SerialName("ProductDescription") val description: String? = null,
        @SerialName("Images") val images: List<Image> = emptyList(),
    )

    @Serializable
    private data class Image(
        @SerialName("ImagePurpose") val purpose: String = "",
        @SerialName("Uri") val uri: String = "",
    )

    @Serializable
    private data class SkuAvailability(@SerialName("Availabilities") val availabilities: List<Availability> = emptyList())

    @Serializable
    private data class Availability(
        @SerialName("Actions") val actions: List<String> = emptyList(),
        @SerialName("Conditions") val conditions: Conditions? = null,
        @SerialName("OrderManagementData") val orderData: OrderData? = null,
    )

    @Serializable
    private data class Conditions(@SerialName("EndDate") val endDate: String? = null)

    @Serializable
    private data class OrderData(@SerialName("Price") val price: Price? = null)

    @Serializable
    private data class Price(
        @SerialName("CurrencyCode") val currency: String = "",
        @SerialName("ListPrice") val listPrice: Double = 0.0,
        @SerialName("MSRP") val msrp: Double = 0.0,
    )

    @Serializable
    private data class SuggestResponse(@SerialName("Results") val results: List<SuggestGroup> = emptyList())

    @Serializable
    private data class SuggestGroup(@SerialName("Products") val products: List<SuggestProduct> = emptyList())

    @Serializable
    private data class SuggestProduct(
        @SerialName("ProductId") val productId: String,
        @SerialName("Title") val title: String = "",
        @SerialName("Icon") val icon: String? = null,
        @SerialName("Type") val type: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cv = AtomicInteger(0)

    suspend fun search(query: String, market: String = "US"): List<GameSummary> = withContext(Dispatchers.IO) {
        val url = "$BASE/v7.0/productFamilies/autoSuggest".toHttpUrl().newBuilder()
            .addQueryParameter("productFamilyNames", "games")
            .addQueryParameter("platformDependencyName", "windows.xbox")
            .addQueryParameter("query", query)
            .addQueryParameter("market", market)
            .addQueryParameter("languages", LANGUAGE)
            .build()
        json.decodeFromString<SuggestResponse>(get(url.toString())).results
            .flatMap { it.products }
            .filter { it.type == null || it.type == "Game" || it.type == "Durable" }
            .distinctBy { it.productId }
            .map { GameSummary(it.productId, it.title, it.icon?.let(::absoluteUrl)) }
    }

    suspend fun detail(productId: String, market: String = "US"): GameDetail = withContext(Dispatchers.IO) {
        val product = products(listOf(productId), market).firstOrNull() ?: error("Game tidak ditemukan")
        val loc = product.localized.firstOrNull()
        fun image(vararg purposes: String) = purposes.firstNotNullOfOrNull { p ->
            loc?.images?.firstOrNull { it.purpose == p }?.uri?.let(::absoluteUrl)
        }
        GameDetail(
            id = product.productId,
            title = loc?.title.orEmpty(),
            publisher = loc?.publisher,
            description = loc?.shortDescription?.takeIf { it.isNotBlank() } ?: loc?.description,
            heroImage = image("SuperHeroArt", "TitledHeroArt", "BrandedKeyArt"),
            boxArt = image("BoxArt", "Poster", "FeaturePromotionalSquareArt"),
        )
    }

    /** Harga [productId] di semua [regions], paralel dengan batas koneksi agar tidak membanjiri server. */
    suspend fun pricesByRegion(productId: String, regions: List<Region> = Regions.all): Pair<List<RegionPrice>, List<Region>> =
        coroutineScope {
            val gate = Semaphore(8)
            val results = regions.map { region ->
                async(Dispatchers.IO) {
                    region to runCatching { gate.withPermit { priceIn(productId, region) } }.getOrNull()
                }
            }.awaitAll()
            results.mapNotNull { it.second } to results.filter { it.second == null }.map { it.first }
        }

    /** Batch beberapa game di satu market (dipakai daftar pantauan). */
    suspend fun pricesInMarket(productIds: List<String>, region: Region): Map<String, RegionPrice> =
        withContext(Dispatchers.IO) {
            productIds.chunked(20).flatMap { chunk ->
                products(chunk, region.market).mapNotNull { p -> purchasePrice(p, region)?.let { p.productId to it } }
            }.toMap()
        }

    private fun priceIn(productId: String, region: Region): RegionPrice? =
        products(listOf(productId), region.market).firstOrNull()?.let { purchasePrice(it, region) }

    /** SKU pertama adalah edisi dasar; ambil availability yang bisa dibeli. */
    private fun purchasePrice(product: Product, region: Region): RegionPrice? {
        val availability = product.skus.firstOrNull()?.availabilities
            ?.firstOrNull { "Purchase" in it.actions && it.orderData?.price != null } ?: return null
        val price = availability.orderData!!.price!!
        if (price.currency.isEmpty()) return null
        val onSale = price.listPrice < price.msrp
        return RegionPrice(
            region = region,
            currency = price.currency,
            price = price.listPrice,
            msrp = price.msrp,
            saleEnds = availability.conditions?.endDate?.takeIf { onSale },
        )
    }

    private fun products(ids: List<String>, market: String): List<Product> {
        val url = "$BASE/v7.0/products".toHttpUrl().newBuilder()
            .addQueryParameter("bigIds", ids.joinToString(","))
            .addQueryParameter("market", market)
            .addQueryParameter("languages", LANGUAGE)
            .build()
        return json.decodeFromString<ProductsResponse>(get(url.toString())).products
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            // Correlation vector Microsoft; server menerima nilai apa pun yang berformat <basis>.<n>.
            .header("MS-CV", "xbmarket0000000.${cv.incrementAndGet() % 999 + 1}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Microsoft Store HTTP ${response.code}")
            return response.body!!.string()
        }
    }

    private fun absoluteUrl(uri: String) = if (uri.startsWith("//")) "https:$uri" else uri

    private companion object {
        const val BASE = "https://displaycatalog.mp.microsoft.com"
        const val LANGUAGE = "en-us"
    }
}
