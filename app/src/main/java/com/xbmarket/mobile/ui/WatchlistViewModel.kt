package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.data.XboxCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Harga game pantauan di market utama pengguna. */
class WatchlistViewModel(private val catalog: XboxCatalog = XboxCatalog()) : ViewModel() {
    private val _prices = MutableStateFlow<Map<String, RegionPrice>>(emptyMap())
    val prices: StateFlow<Map<String, RegionPrice>> = _prices.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        val ids = Prefs.watchlist.value.map { it.id }
        val region = Regions.byMarket(Prefs.homeMarket.value) ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { catalog.pricesInMarket(ids, region) }.onSuccess { _prices.value = it }
            _refreshing.value = false
        }
    }
}
