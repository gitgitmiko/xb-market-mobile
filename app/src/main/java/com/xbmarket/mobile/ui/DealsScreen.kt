package com.xbmarket.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.xbmarket.mobile.data.Deal
import com.xbmarket.mobile.data.DealFilters
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Region
import com.xbmarket.mobile.data.Regions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(
    onOpen: (GameSummary) -> Unit,
    listState: LazyListState,
    onScan: (List<GameSummary>, Region) -> Unit,
    vm: DealsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pickRegion by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val region = Regions.byMarket(state.market)
    val deals = state.visible

    // Muat halaman berikutnya saat mendekati akhir daftar.
    LaunchedEffect(listState, deals.size, state.deals.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 6) vm.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { pickRegion = true },
                label = { Text("${region?.flag.orEmpty()} ${region?.name ?: state.market}") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            val activeFilters = state.filters.activeCount
            FilterChip(
                selected = activeFilters > 0,
                onClick = { showFilters = true },
                label = { Text(if (activeFilters > 0) "Filter ($activeFilters)" else "Filter") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            Box {
                AssistChip(
                    onClick = { showSort = true },
                    label = { Text(state.filters.sort.label) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    DealFilters.Sort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = {
                                vm.setFilters(state.filters.copy(sort = sort))
                                showSort = false
                                scope.launch { listState.scrollToItem(0) }
                            },
                        )
                    }
                }
            }
            // Bandingkan diskon yang sudah dimuat (maks. ScanViewModel.MAX_GAMES teratas) di semua region.
            AssistChip(
                onClick = { region?.let { r -> onScan(deals.map { GameSummary(it.id, it.title, it.image) }, r) } },
                enabled = deals.isNotEmpty() && region != null,
                label = { Text("Termurah") },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }

        PullToRefreshBox(isRefreshing = state.loading && deals.isNotEmpty(), onRefresh = vm::reload, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading && deals.isEmpty() -> Centered { CircularProgressIndicator() }
                state.error != null && deals.isEmpty() -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = vm::reload) { Text("Coba lagi") }
                    }
                }
                deals.isEmpty() && state.endReached -> Centered {
                    Text(if (state.filters.activeCount > 0) "Tidak ada diskon yang cocok dengan filter" else "Tidak ada diskon di region ini")
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(deals, key = { it.id }) { deal ->
                        DealRow(deal, state.base, state.convert, onClick = { onOpen(GameSummary(deal.id, deal.title, deal.image)) })
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                                state.error != null -> TextButton(onClick = vm::loadMore) { Text("Gagal memuat. Coba lagi") }
                                !state.endReached && !state.loading -> TextButton(onClick = vm::loadMore) { Text("Muat lebih banyak") }
                                state.endReached -> Text(
                                    "${deals.size} diskon",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        DealFilterSheet(
            initial = state.filters,
            base = state.base,
            convert = state.convert,
            onApply = {
                vm.setFilters(it)
                showFilters = false
                scope.launch { listState.scrollToItem(0) }
            },
            onDismiss = { showFilters = false },
        )
    }
    if (pickRegion) {
        Picker("Region", Regions.all, label = { "${it.flag} ${it.name}" }, onDismiss = { pickRegion = false }) {
            vm.selectMarket(it.market)
            scope.launch { listState.scrollToItem(0) }
            pickRegion = false
        }
    }
}

@Composable
private fun DealRow(deal: Deal, base: String, convert: ((Double, String) -> Double?)?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = deal.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(deal.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DiscountBadge(deal.discountPercent)
                Text(
                    formatMoney(deal.price, deal.currency),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    formatMoney(deal.msrp, deal.currency),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tampilkan konversi hanya jika mata uang region berbeda dari mata uang dasar.
                if (deal.currency != base) {
                    convert?.invoke(deal.price, deal.currency)?.let {
                        Text("≈ ${formatMoney(it, base)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                deal.endDateUtc?.let {
                    Text("s.d. ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = SaleColor)
                }
            }
        }
    }
}
