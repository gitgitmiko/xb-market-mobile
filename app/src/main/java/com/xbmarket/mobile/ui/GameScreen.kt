package com.xbmarket.mobile.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.notify.PriceNotifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(game: GameSummary, onBack: () -> Unit, vm: GameViewModel = viewModel(key = game.id)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base by Prefs.baseCurrency.collectAsStateWithLifecycle()
    val home by Prefs.homeMarket.collectAsStateWithLifecycle()
    val watchlist by Prefs.watchlist.collectAsStateWithLifecycle()
    val watched = watchlist.any { it.id == game.id }
    val context = LocalContext.current
    var onlySale by rememberSaveable { mutableStateOf(false) }
    val alerts by Prefs.priceAlerts.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(game.id, base) { vm.load(game.id, base) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.title ?: game.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = { vm.load(game.id, base, force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                    }
                    IconButton(onClick = {
                        Prefs.toggleWatch(GameSummary(game.id, state.detail?.title ?: game.title, state.detail?.boxArt ?: game.image))
                        if (!watched) {
                            // Catat harga sekarang sebagai acuan agar penurunan berikutnya terdeteksi.
                            state.comparison?.prices?.firstOrNull { it.region.market == home }
                                ?.let { Prefs.setLastPrice(game.id, home, it.price) }
                            if (alerts && !PriceNotifications.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }) {
                        Icon(
                            if (watched) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (watched) "Hapus dari pantauan" else "Pantau",
                            tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val comparison = state.comparison
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Header(state, fallbackImage = game.image)
            }
            when {
                state.error != null -> item {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                comparison == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Mengambil harga dari semua region…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                comparison.prices.isEmpty() -> item {
                    Text("Game ini tidak dijual di region mana pun.", modifier = Modifier.padding(16.dp))
                }
                else -> {
                    state.fxError?.let { msg ->
                        item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    val homePrice = comparison.prices.firstOrNull { it.region.market == home }?.converted
                    val onSale = comparison.prices.count { it.discountPercent > 0 }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Harga per region", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Dikonversi ke $base · diskon di $onSale dari ${comparison.prices.size} region",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilterChip(selected = onlySale, onClick = { onlySale = !onlySale }, label = { Text("Hanya diskon") })
                        }
                    }
                    val shown = if (onlySale) comparison.prices.filter { it.discountPercent > 0 } else comparison.prices
                    if (shown.isEmpty()) item {
                        Text("Tidak ada region yang sedang diskon.", modifier = Modifier.padding(16.dp))
                    }
                    itemsIndexed(shown, key = { _, p -> p.region.market }) { index, price ->
                        PriceRow(
                            rank = index + 1,
                            price = price,
                            base = base,
                            isHome = price.region.market == home,
                            homePrice = homePrice,
                            onOpenStore = {
                                val url = "https://www.xbox.com/en-US/games/store/-/${game.id}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
                    }
                    if (comparison.unavailable.isNotEmpty()) item {
                        Text(
                            "Tidak tersedia di: " + comparison.unavailable.joinToString { "${it.flag} ${it.name}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: GameState, fallbackImage: String?) {
    val detail = state.detail
    Column {
        AsyncImage(
            model = detail?.heroImage ?: detail?.boxArt ?: fallbackImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (detail != null) {
            Column(Modifier.padding(16.dp)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                detail.publisher?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                detail.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PriceRow(
    rank: Int,
    price: RegionPrice,
    base: String,
    isHome: Boolean,
    homePrice: Double?,
    onOpenStore: () -> Unit,
) {
    val cheapest = rank == 1
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                cheapest -> MaterialTheme.colorScheme.primaryContainer
                isHome -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
            Text(price.region.flag, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(price.region.name, fontWeight = FontWeight.SemiBold)
                    if (isHome) Text("utama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    if (price.discountPercent > 0) DiscountBadge(price.discountPercent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatMoney(price.price, price.currency), style = MaterialTheme.typography.bodySmall)
                    if (price.discountPercent > 0) {
                        Text(
                            formatMoney(price.msrp, price.currency),
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                price.saleEnds?.let {
                    Text("Diskon s.d. ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = SaleColor)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price.converted?.let { formatMoney(it, base) } ?: "—",
                    fontWeight = FontWeight.Bold,
                    color = if (cheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                // Selisih terhadap region utama, misalnya "-42%" berarti 42% lebih murah.
                if (!isHome && homePrice != null && homePrice > 0 && price.converted != null) {
                    val diff = ((price.converted / homePrice - 1) * 100).toInt()
                    if (diff != 0) {
                        Text(
                            (if (diff > 0) "+" else "") + "$diff%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diff < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onOpenStore, contentPadding = PaddingValues(0.dp)) {
                    Text("Store", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun DiscountBadge(percent: Int) {
    Text(
        "-$percent%",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = Modifier.background(SaleColor, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
