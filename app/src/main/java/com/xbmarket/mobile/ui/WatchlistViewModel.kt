package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.FxRates
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.data.XboxCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Harga satu game pantauan: di region utama (null bila tidak dijual di sana) dan region termurah. */
data class WatchPrice(val home: RegionPrice?, val cheapest: RegionPrice?)

/** Harga game pantauan di semua region, dikonversi ke mata uang dasar. Satu permintaan per region untuk semua game. */
class WatchlistViewModel(
    private val catalog: XboxCatalog = XboxCatalog(),
    private val fx: FxRates = FxRates(),
) : ViewModel() {
    private val _prices = MutableStateFlow<Map<String, WatchPrice>>(emptyMap())
    val prices: StateFlow<Map<String, WatchPrice>> = _prices.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        val watchlist = Prefs.watchlist.value
        if (watchlist.isEmpty() || _refreshing.value) return
        val ids = watchlist.map { it.id }
        val home = Prefs.homeMarket.value
        val base = Prefs.baseCurrency.value
        viewModelScope.launch {
            _refreshing.value = true
            runCatching {
                coroutineScope {
                    val convert = async { runCatching { fx.converter(base) }.getOrNull() }
                    // Item pantauan lama bisa tersimpan tanpa gambar; lengkapi dari katalog.
                    val missing = watchlist.filter { it.image == null || it.title.isBlank() }.map { it.id }
                    if (missing.isNotEmpty()) launch { runCatching { Prefs.fillWatchDetails(catalog.summaries(missing)) } }
                    val gate = Semaphore(8)
                    val byRegion = Regions.all.map { region ->
                        async(Dispatchers.IO) {
                            runCatching { gate.withPermit { catalog.pricesInMarket(ids, region) } }.getOrDefault(emptyMap())
                        }
                    }.awaitAll()
                    val conv = convert.await()
                    ids.associateWith { id ->
                        val all = byRegion.mapNotNull { it[id] }.map { it.copy(converted = conv?.invoke(it.price, it.currency)) }
                        WatchPrice(
                            home = all.firstOrNull { it.region.market == home },
                            cheapest = all.filter { it.converted != null }.minByOrNull { it.converted!! },
                        )
                    }
                }
            }.onSuccess { _prices.value = it }
            _refreshing.value = false
        }
    }
}
