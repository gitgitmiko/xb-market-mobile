package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.FxRates
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.Region
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.data.XboxCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Region termurah satu game. [savingsPercent] dibanding region utama; null bila tidak dijual di region utama. */
data class ScanResult(
    val game: GameSummary,
    val cheapest: RegionPrice,
    val home: RegionPrice?,
    val saleRegions: Int,
) {
    val savingsPercent: Int?
        get() {
            val h = home?.converted ?: return null
            val c = cheapest.converted ?: return null
            return if (h > 0) ((1 - c / h) * 100).toInt() else null
        }
}

data class ScanState(
    val sourceRegion: Region? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = Regions.all.size,
    val results: List<ScanResult> = emptyList(),
    val base: String = "IDR",
    val error: String? = null,
)

/**
 * Membandingkan harga sekumpulan game (dari daftar Diskon) di semua region sekaligus.
 * displaycatalog menerima 20 ID per permintaan, jadi 60 game = 3 permintaan per region.
 */
class ScanViewModel(
    private val catalog: XboxCatalog = XboxCatalog(),
    private val fx: FxRates = FxRates(),
) : ViewModel() {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(games: List<GameSummary>, sourceRegion: Region) {
        job?.cancel()
        val targets = games.distinctBy { it.id }.take(MAX_GAMES)
        val base = Prefs.baseCurrency.value
        val homeMarket = Prefs.homeMarket.value
        _state.value = ScanState(sourceRegion = sourceRegion, running = true, base = base)
        job = viewModelScope.launch {
            try {
                val convert = fx.converter(base)
                val ids = targets.map { it.id }
                val gate = Semaphore(6)
                val byRegion: List<Map<String, RegionPrice>> = coroutineScope {
                    Regions.all.map { region ->
                        async(Dispatchers.IO) {
                            val prices = runCatching { gate.withPermit { catalog.pricesInMarket(ids, region) } }.getOrDefault(emptyMap())
                            _state.update { it.copy(done = it.done + 1) }
                            prices
                        }
                    }.awaitAll()
                }
                val results = targets.mapNotNull { game ->
                    val prices = byRegion.mapNotNull { it[game.id] }
                        .map { it.copy(converted = convert(it.price, it.currency)) }
                    val cheapest = prices.filter { it.converted != null }.minByOrNull { it.converted!! } ?: return@mapNotNull null
                    ScanResult(
                        game = game,
                        cheapest = cheapest,
                        home = prices.firstOrNull { it.region.market == homeMarket },
                        saleRegions = prices.count { it.discountPercent > 0 },
                    )
                }.sortedWith(compareByDescending<ScanResult> { it.savingsPercent ?: -1 }.thenBy { it.cheapest.converted })
                _state.update { it.copy(running = false, results = results) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(running = false, error = e.message ?: "Gagal memindai") }
            }
        }
    }

    companion object {
        const val MAX_GAMES = 60
    }
}
