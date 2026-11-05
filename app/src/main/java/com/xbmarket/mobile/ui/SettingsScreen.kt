package com.xbmarket.mobile.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.notify.PriceNotifications
import com.xbmarket.mobile.notify.PriceWatchWorker

/** Mata uang dasar yang bisa dipilih untuk konversi. */
private val Currencies = listOf(
    "IDR", "USD", "EUR", "GBP", "JPY", "SGD", "MYR", "THB", "PHP", "VND", "INR", "AUD", "TRY", "BRL", "ARS",
)

@Composable
fun SettingsScreen() {
    val base by Prefs.baseCurrency.collectAsStateWithLifecycle()
    val home by Prefs.homeMarket.collectAsStateWithLifecycle()
    var pickCurrency by remember { mutableStateOf(false) }
    var pickMarket by remember { mutableStateOf(false) }
    val alerts by Prefs.priceAlerts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column {
        ListItem(
            headlineContent = { Text("Mata uang konversi") },
            supportingContent = { Text("Semua harga region dikonversi ke mata uang ini") },
            trailingContent = { Text(base, color = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { pickCurrency = true },
        )
        ListItem(
            headlineContent = { Text("Region utama") },
            supportingContent = { Text("Dipakai untuk harga di daftar pantauan") },
            trailingContent = {
                val r = Regions.byMarket(home)
                Text("${r?.flag.orEmpty()} ${r?.name ?: home}", color = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable { pickMarket = true },
        )
        ListItem(
            headlineContent = { Text("Notifikasi turun harga") },
            supportingContent = { Text("Cek game pantauan di region utama setiap 6 jam") },
            trailingContent = {
                Switch(checked = alerts, onCheckedChange = { on ->
                    Prefs.setPriceAlerts(on)
                    if (on) {
                        PriceWatchWorker.schedule(context)
                        if (!PriceNotifications.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        PriceWatchWorker.cancel(context)
                    }
                })
            },
        )
        if (alerts) {
            TextButton(
                onClick = {
                    PriceWatchWorker.runNow(context)
                    Toast.makeText(context, "Mengecek harga pantauan…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Cek harga sekarang") }
        }
        Text(
            "Harga dari katalog publik Microsoft Store. Kurs dari open.er-api.com (diperbarui harian), " +
                "jadi hasil konversi adalah perkiraan. Pembelian lintas region dapat melanggar ketentuan Microsoft.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (pickCurrency) {
        Picker("Mata uang", Currencies, label = { it }, onDismiss = { pickCurrency = false }) {
            Prefs.setBaseCurrency(it)
            pickCurrency = false
        }
    }
    if (pickMarket) {
        Picker("Region utama", Regions.all, label = { "${it.flag} ${it.name}" }, onDismiss = { pickMarket = false }) {
            Prefs.setHomeMarket(it.market)
            pickMarket = false
        }
    }
}

@Composable
fun <T> Picker(title: String, options: List<T>, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    Text(
                        label(option),
                        modifier = Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
