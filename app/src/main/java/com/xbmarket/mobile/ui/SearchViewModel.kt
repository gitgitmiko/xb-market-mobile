package com.xbmarket.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.XboxCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<GameSummary> = emptyList(),
    val error: String? = null,
)

class SearchViewModel(private val catalog: XboxCatalog = XboxCatalog()) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        job?.cancel()
        if (query.isBlank()) {
            _state.value = SearchState()
            return
        }
        job = viewModelScope.launch {
            delay(350)
            _state.value = _state.value.copy(loading = true, error = null)
            _state.value = runCatching { catalog.search(query.trim()) }.fold(
                onSuccess = { _state.value.copy(loading = false, results = it) },
                onFailure = { _state.value.copy(loading = false, error = it.message ?: "Gagal mencari") },
            )
        }
    }
}
