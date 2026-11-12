package com.xbmarket.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Pengaturan dan daftar pantauan, disimpan lokal di SharedPreferences. */
object Prefs {
    private const val FILE = "xbmarket"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var context: Context
    private val _baseCurrency = MutableStateFlow("IDR")
    val baseCurrency: StateFlow<String> = _baseCurrency.asStateFlow()
    private val _homeMarket = MutableStateFlow("ID")
    val homeMarket: StateFlow<String> = _homeMarket.asStateFlow()
    private val _priceAlerts = MutableStateFlow(true)
    val priceAlerts: StateFlow<Boolean> = _priceAlerts.asStateFlow()
    private val _watchlist = MutableStateFlow<List<GameSummary>>(emptyList())
    val watchlist: StateFlow<List<GameSummary>> = _watchlist.asStateFlow()

    fun init(context: Context) {
        this.context = context.applicationContext
        val prefs = prefs()
        _baseCurrency.value = prefs.getString("baseCurrency", "IDR")!!
        _homeMarket.value = prefs.getString("homeMarket", "ID")!!
        _priceAlerts.value = prefs.getBoolean("priceAlerts", true)
        _watchlist.value = prefs.getString("watchlist", null)
            ?.let { runCatching { json.decodeFromString<List<GameSummary>>(it) }.getOrNull() }
            .orEmpty()
    }

    fun setBaseCurrency(code: String) {
        _baseCurrency.value = code
        prefs().edit().putString("baseCurrency", code).apply()
    }

    fun setHomeMarket(market: String) {
        _homeMarket.value = market
        prefs().edit().putString("homeMarket", market).apply()
    }

    fun dealFilters(): DealFilters = prefs().getString("dealFilters", null)
        ?.let { runCatching { json.decodeFromString<DealFilters>(it) }.getOrNull() }
        ?: DealFilters()

    fun setDealFilters(filters: DealFilters) {
        prefs().edit().putString("dealFilters", json.encodeToString(filters)).apply()
    }

    fun setPriceAlerts(enabled: Boolean) {
        _priceAlerts.value = enabled
        prefs().edit().putBoolean("priceAlerts", enabled).apply()
    }

    /** Harga terakhir yang dicek worker untuk game di satu market (dalam mata uang market itu). */
    fun lastPrice(id: String, market: String): Double? {
        val key = "price:$market:$id"
        return if (prefs().contains(key)) Double.fromBits(prefs().getLong(key, 0)) else null
    }

    // Disimpan sebagai bit Double: Float tidak presisi untuk harga besar seperti VND.
    fun setLastPrice(id: String, market: String, price: Double) {
        prefs().edit().putLong("price:$market:$id", price.toRawBits()).apply()
    }

    fun isWatched(id: String) = _watchlist.value.any { it.id == id }

    fun toggleWatch(game: GameSummary) {
        val list = _watchlist.value
        val added = list.none { it.id == game.id }
        _watchlist.value = if (added) listOf(game) + list else list.filterNot { it.id == game.id }
        prefs().edit().putString("watchlist", json.encodeToString(_watchlist.value)).apply()
        CloudSync.onWatchToggled(game, added)
    }

    /** Ganti seluruh pantauan dengan daftar dari akun (lihat [CloudSync]). */
    fun replaceWatchlist(list: List<GameSummary>) {
        if (list == _watchlist.value) return
        _watchlist.value = list
        prefs().edit().putString("watchlist", json.encodeToString(list)).apply()
    }

    /** Akun yang pantauan lokalnya sudah digabung ke Firestore. */
    fun syncedUid(): String? = prefs().getString("syncedUid", null)

    fun setSyncedUid(uid: String?) {
        prefs().edit().putString("syncedUid", uid).apply()
    }

    /** Lengkapi gambar/judul item pantauan yang kosong tanpa mengubah urutannya. */
    fun fillWatchDetails(details: List<GameSummary>) {
        val byId = details.associateBy { it.id }
        val updated = _watchlist.value.map { game ->
            val d = byId[game.id] ?: return@map game
            game.copy(title = game.title.ifBlank { d.title }, image = game.image ?: d.image)
        }
        val changed = updated.filter { it !in _watchlist.value }
        if (changed.isEmpty()) return
        _watchlist.value = updated
        prefs().edit().putString("watchlist", json.encodeToString(updated)).apply()
        // Perbarui juga di akun (merge, tanpa menghapus harga terakhir dari server).
        changed.forEach(CloudSync::updateDetails)
    }

    private fun prefs() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
