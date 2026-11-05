package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.Deal
import com.xbmarket.mobile.data.DealFilters
import com.xbmarket.mobile.data.FxRates
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.data.XboxDeals
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DealsState(
    val market: String,
    val deals: List<Deal> = emptyList(),
    val filters: DealFilters = DealFilters(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val continuation: String? = null,
    val endReached: Boolean = false,
    val base: String = "IDR",
    val convert: ((Double, String) -> Double?)? = null,
) {
    /** Hasil setelah filter sisi aplikasi dan urutan. Platform/genre/diskon minimal sudah disaring server. */
    val visible: List<Deal>
        get() {
            val f = filters
            val list = deals.filter { d ->
                when (f.kind) {
                    DealFilters.Kind.All -> true
                    DealFilters.Kind.Game -> d.isGame
                    DealFilters.Kind.AddOn -> !d.isGame
                } &&
                    !(f.hideGamePass && d.inGamePass) &&
                    (f.maxPrice == null || (convert?.invoke(d.price, d.currency) ?: 0.0) <= f.maxPrice)
            }
            return when (f.sort) {
                DealFilters.Sort.Discount -> list
                DealFilters.Sort.PriceAsc -> list.sortedBy { convert?.invoke(it.price, it.currency) ?: it.price }
                DealFilters.Sort.Newest -> list.sortedByDescending { it.releaseDate.orEmpty() }
            }
        }
}

class DealsViewModel(
    private val source: XboxDeals = XboxDeals(),
    private val fx: FxRates = FxRates(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        DealsState(market = Prefs.homeMarket.value, filters = Prefs.dealFilters(), base = Prefs.baseCurrency.value),
    )
    val state: StateFlow<DealsState> = _state.asStateFlow()
    private var job: Job? = null
    /** Naik setiap reload; hasil dari muatan lama diabaikan. */
    private var generation = 0

    init {
        reload()
        viewModelScope.launch {
            Prefs.baseCurrency.collect { base ->
                val convert = runCatching { fx.converter(base) }.getOrNull()
                _state.update { s ->
                    // Batas harga tersimpan dalam mata uang dasar lama; tidak berlaku lagi setelah diganti.
                    val filters = if (s.base != base && s.filters.maxPrice != null) {
                        s.filters.copy(maxPrice = null).also(Prefs::setDealFilters)
                    } else {
                        s.filters
                    }
                    s.copy(base = base, convert = convert, filters = filters)
                }
            }
        }
    }

    fun selectMarket(market: String) {
        if (market == _state.value.market) return
        reload(market)
    }

    fun setFilters(filters: DealFilters) {
        val old = _state.value.filters
        Prefs.setDealFilters(filters)
        if (filters.serverKey() != old.serverKey()) {
            _state.update { it.copy(filters = filters) }
            reload()
        } else {
            _state.update { it.copy(filters = filters) }
            // Saringan di aplikasi bisa membuat daftar pendek; isi ulang bila perlu.
            if (_state.value.visible.size < MIN_VISIBLE) loadMore()
        }
    }

    fun reload(market: String = _state.value.market) {
        job?.cancel()
        generation++
        val s = _state.value
        _state.value = DealsState(market = market, filters = s.filters, base = s.base, convert = s.convert, loading = true)
        job = viewModelScope.launch { fetch(first = true) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.continuation == null) return
        job = viewModelScope.launch { fetch(first = false) }
    }

    /** Ambil halaman sampai ada cukup item baru yang tampil (setelah filter), maksimal beberapa halaman. */
    private suspend fun fetch(first: Boolean) {
        val region = Regions.byMarket(_state.value.market) ?: return
        val gen = generation
        if (!first) _state.value = _state.value.copy(loadingMore = true)
        val startVisible = _state.value.visible.size
        try {
            var pages = 0
            do {
                val page = source.page(region, _state.value.filters, _state.value.continuation)
                if (gen != generation) return
                // update{} membaca state terbaru agar kurs yang datang di tengah jalan tidak tertimpa.
                _state.update { s ->
                    val known = s.deals.mapTo(HashSet()) { it.id }
                    s.copy(
                        deals = s.deals + page.deals.filter { it.id !in known },
                        continuation = page.continuation,
                        endReached = page.continuation == null,
                        error = null,
                    )
                }
                pages++
            } while (!_state.value.endReached && _state.value.visible.size - startVisible < MIN_VISIBLE && pages < MAX_PAGES)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (gen == generation) _state.update { it.copy(error = e.message ?: "Gagal memuat diskon") }
        } finally {
            if (gen == generation) _state.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    private companion object {
        const val MIN_VISIBLE = 20
        const val MAX_PAGES = 5
    }
}
