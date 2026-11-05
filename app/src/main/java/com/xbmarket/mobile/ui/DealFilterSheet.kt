package com.xbmarket.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xbmarket.mobile.data.DealFilters
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Batas harga dalam USD yang dikonversi ke mata uang dasar untuk pilihan "Harga maks". */
private val MaxPriceUsd = listOf(2.0, 5.0, 10.0, 20.0, 40.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DealFilterSheet(
    initial: DealFilters,
    base: String,
    convert: ((Double, String) -> Double?)?,
    onApply: (DealFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    val maxPrices = remember(base, convert) {
        convert?.let { c -> MaxPriceUsd.mapNotNull { usd -> c(usd, "USD")?.let(::niceRound) }.distinct() }.orEmpty()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            Text("Filter diskon", style = MaterialTheme.typography.titleLarge)

            Section("Jenis") {
                DealFilters.Kind.entries.forEach { k ->
                    FilterChip(selected = f.kind == k, onClick = { f = f.copy(kind = k) }, label = { Text(k.label) })
                }
            }
            Section("Diskon minimal") {
                DealFilters.Discounts.forEach { d ->
                    FilterChip(
                        selected = f.minDiscount == d,
                        onClick = { f = f.copy(minDiscount = d) },
                        label = { Text(if (d == 0) "Semua" else "$d%+") },
                    )
                }
            }
            if (maxPrices.isNotEmpty()) {
                Section("Harga maks ($base)") {
                    FilterChip(selected = f.maxPrice == null, onClick = { f = f.copy(maxPrice = null) }, label = { Text("Semua") })
                    maxPrices.forEach { p ->
                        FilterChip(
                            selected = f.maxPrice == p,
                            onClick = { f = f.copy(maxPrice = p) },
                            label = { Text("≤ ${formatMoney(p, base)}") },
                        )
                    }
                }
            }
            Section("Platform") {
                DealFilters.Platforms.forEach { (id, label) ->
                    FilterChip(
                        selected = id in f.platforms,
                        onClick = { f = f.copy(platforms = f.platforms.toggle(id)) },
                        label = { Text(label) },
                    )
                }
            }
            Section("Genre") {
                DealFilters.Genres.forEach { g ->
                    FilterChip(selected = g in f.genres, onClick = { f = f.copy(genres = f.genres.toggle(g)) }, label = { Text(g) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sembunyikan yang ada di Game Pass")
                    Text(
                        "Game yang bisa dimainkan lewat langganan Game Pass",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = f.hideGamePass, onCheckedChange = { f = f.copy(hideGamePass = it) })
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { f = DealFilters(sort = f.sort) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(onClick = { onApply(f) }, modifier = Modifier.weight(1f)) { Text("Terapkan") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item

/** Bulatkan ke 2 angka penting agar batas harga mudah dibaca (mis. 81.234 → 81.000). */
private fun niceRound(value: Double): Double {
    if (value <= 0) return value
    val magnitude = 10.0.pow(floor(log10(value)) - 1)
    return kotlin.math.round(value / magnitude) * magnitude
}
