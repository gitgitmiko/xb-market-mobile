package com.xbmarket.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.Regions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel, onBack: () -> Unit, onOpen: (GameSummary) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val home by Prefs.homeMarket.collectAsStateWithLifecycle()
    val homeRegion = Regions.byMarket(home)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Region termurah") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Diskon ${state.sourceRegion?.flag.orEmpty()} ${state.sourceRegion?.name.orEmpty()} dibandingkan di ${state.total} region · " +
                    "harga dalam ${state.base}, selisih terhadap ${homeRegion?.flag.orEmpty()} ${homeRegion?.name ?: home}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.running -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Memeriksa region ${state.done} dari ${state.total}…")
                }
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                state.results.isEmpty() -> Text("Tidak ada hasil.", modifier = Modifier.padding(16.dp))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.results, key = { it.game.id }) { result ->
                        ScanRow(result, state.base, onClick = { onOpen(result.game) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRow(result: ScanResult, base: String, onClick: () -> Unit) {
    val cheapest = result.cheapest
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.game.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${cheapest.region.flag} ${cheapest.region.name}", fontWeight = FontWeight.SemiBold)
                if (cheapest.discountPercent > 0) DiscountBadge(cheapest.discountPercent)
            }
            Text(
                "${formatMoney(cheapest.price, cheapest.currency)} · diskon di ${result.saleRegions} region",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                cheapest.converted?.let { formatMoney(it, base) } ?: "—",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            result.home?.converted?.let { h ->
                Text(
                    formatMoney(h, base),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.savingsPercent?.takeIf { it > 0 }?.let {
                Text("hemat $it%", style = MaterialTheme.typography.labelSmall, color = SaleColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
