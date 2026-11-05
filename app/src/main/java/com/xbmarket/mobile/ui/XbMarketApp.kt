package com.xbmarket.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.RegionPrice

private enum class Tab(val label: String) { Deals("Diskon"), Search("Cari"), Watchlist("Pantauan"), Settings("Pengaturan") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XbMarketApp(pendingGame: GameSummary? = null, onPendingGameOpened: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.Deals) }
    var openGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var openGameTitle by rememberSaveable { mutableStateOf("") }
    var openGameImage by rememberSaveable { mutableStateOf<String?>(null) }
    // Di luar percabangan agar posisi scroll Diskon tetap saat kembali dari detail game.
    val dealsListState = rememberLazyListState()
    var scanOpen by rememberSaveable { mutableStateOf(false) }
    val scanVm: ScanViewModel = viewModel()

    LaunchedEffect(pendingGame) {
        pendingGame?.let {
            openGameTitle = it.title
            openGameImage = it.image
            openGameId = it.id
            onPendingGameOpened()
        }
    }

    openGameId?.let { id ->
        BackHandler { openGameId = null }
        GameScreen(GameSummary(id, openGameTitle, openGameImage), onBack = { openGameId = null })
        return
    }
    val open: (GameSummary) -> Unit = {
        openGameTitle = it.title
        openGameImage = it.image
        openGameId = it.id
    }
    // Hasil pemindaian hanya ada di memori; setelah proses dibuat ulang, kembali ke tab.
    if (scanOpen && scanVm.state.value.sourceRegion != null) {
        BackHandler { scanOpen = false }
        ScanScreen(scanVm, onBack = { scanOpen = false }, onOpen = open)
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == Tab.Deals) "XB Market" else tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Deals -> Icons.Filled.Star
                                    Tab.Search -> Icons.Filled.Search
                                    Tab.Watchlist -> Icons.Filled.Favorite
                                    Tab.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Deals -> DealsScreen(
                    onOpen = open,
                    listState = dealsListState,
                    onScan = { games, region ->
                        scanVm.start(games, region)
                        scanOpen = true
                    },
                )
                Tab.Search -> SearchScreen(onOpen = open)
                Tab.Watchlist -> WatchlistScreen(onOpen = open)
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun SearchScreen(onOpen: (GameSummary) -> Unit, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Cari game Xbox…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.loading -> Centered { CircularProgressIndicator() }
            state.error != null -> Centered { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
            state.query.isBlank() -> Centered {
                Text(
                    "Cari game untuk membandingkan harganya di ${com.xbmarket.mobile.data.Regions.all.size} region Microsoft Store.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            state.results.isEmpty() -> Centered { Text("Tidak ada hasil") }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.results, key = { it.id }) { game -> GameRow(game, onClick = { onOpen(game) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchlistScreen(onOpen: (GameSummary) -> Unit, vm: WatchlistViewModel = viewModel()) {
    val watchlist by Prefs.watchlist.collectAsStateWithLifecycle()
    val homeMarket by Prefs.homeMarket.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    LaunchedEffect(watchlist.size, homeMarket) { vm.refresh() }

    if (watchlist.isEmpty()) {
        Centered {
            Text(
                "Belum ada game dipantau. Buka game lalu ketuk ♥ untuk menambahkannya.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(watchlist, key = { it.id }) { game ->
                GameRow(game, price = prices[game.id], onClick = { onOpen(game) })
            }
        }
    }
}

@Composable
fun GameRow(game: GameSummary, price: RegionPrice? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = game.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (price != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${price.region.flag} ${formatMoney(price.price, price.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (price.discountPercent > 0) DiscountBadge(price.discountPercent)
                }
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
