package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.FxRates
import com.xbmarket.mobile.data.GameDetail
import com.xbmarket.mobile.data.PriceComparison
import com.xbmarket.mobile.data.XboxCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameState(
    val detail: GameDetail? = null,
    val comparison: PriceComparison? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Diisi bila kurs gagal dimuat; harga tetap tampil dalam mata uang lokal. */
    val fxError: String? = null,
)

class GameViewModel(
    private val catalog: XboxCatalog = XboxCatalog(),
    private val fx: FxRates = FxRates(),
) : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    private var loaded: Pair<String, String>? = null

    fun load(productId: String, baseCurrency: String, force: Boolean = false) {
        if (!force && loaded == productId to baseCurrency) return
        loaded = productId to baseCurrency
        _state.value = GameState(detail = _state.value.detail?.takeIf { it.id == productId })
        viewModelScope.launch {
            // coroutineScope agar kegagalan async dilempar ke catch, bukan membatalkan viewModelScope.
            try { coroutineScope {
                val detail = async { catalog.detail(productId) }
                val prices = async { catalog.pricesByRegion(productId) }
                val converter = runCatching { fx.converter(baseCurrency) }
                val (found, missing) = prices.await()
                val convert = converter.getOrNull()
                val converted = found.map { p -> p.copy(converted = convert?.invoke(p.price, p.currency)) }
                    .sortedWith(compareBy(nullsLast()) { it.converted })
                _state.value = GameState(
                    detail = detail.await(),
                    comparison = PriceComparison(converted, missing),
                    loading = false,
                    fxError = converter.exceptionOrNull()?.let { "Kurs tidak tersedia: ${it.message}" },
                )
            } } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal memuat")
            }
        }
    }
}
