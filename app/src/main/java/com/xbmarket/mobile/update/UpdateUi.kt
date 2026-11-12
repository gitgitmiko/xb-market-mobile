package com.xbmarket.mobile.update

import android.app.Application
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xbmarket.mobile.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Ready(val update: AppUpdate, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences("update", 0)

    /** Cek otomatis saat aplikasi dibuka, paling sering tiap 6 jam. */
    fun autoCheck() {
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            runCatching { AppUpdater.check() }
                .onSuccess { update ->
                    prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                    _state.value = if (update != null) UpdateState.Available(update) else UpdateState.Idle
                    if (update == null && manual) toast("XB Market sudah versi terbaru (${BuildConfig.VERSION_NAME})")
                }
                .onFailure {
                    _state.value = if (manual) UpdateState.Failed(it.message ?: "Gagal mengecek pembaruan") else UpdateState.Idle
                }
        }
    }

    fun download(update: AppUpdate) {
        _state.value = UpdateState.Downloading(update, 0f)
        viewModelScope.launch {
            runCatching {
                AppUpdater.download(getApplication(), update) { p -> _state.value = UpdateState.Downloading(update, p) }
            }.onSuccess { apk ->
                _state.value = UpdateState.Ready(update, apk)
                install()
            }.onFailure { _state.value = UpdateState.Failed(it.message ?: "Gagal mengunduh pembaruan") }
        }
    }

    /** Buka installer; bila izin belum diberikan, buka pengaturan izin dan biarkan dialog tetap terbuka. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        val context = getApplication<Application>()
        if (!AppUpdater.canInstall(context)) {
            AppUpdater.openInstallPermission(context)
            return
        }
        runCatching { AppUpdater.install(context, ready.apk) }
            .onFailure { _state.value = UpdateState.Failed(it.message ?: "Gagal membuka installer") }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private fun toast(text: String) = Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

/** Dialog pembaruan; dipasang sekali di akar aplikasi. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.autoCheck() }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Versi ${s.update.versionName} tersedia") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Terpasang ${BuildConfig.VERSION_NAME}" +
                            (if (s.update.size > 0) " · unduhan ${Formatter.formatShortFileSize(context, s.update.size)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.update.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(s.update.notes)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.download(s.update) }) { Text("Perbarui") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Nanti") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengunduh ${s.update.versionName}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Siap dipasang") },
            text = {
                Text(
                    if (AppUpdater.canInstall(context)) "Ketuk Pasang untuk memperbarui ke ${s.update.versionName}."
                    else "Izinkan XB Market memasang aplikasi di pengaturan, lalu kembali dan ketuk Pasang.",
                )
            },
            confirmButton = { TextButton(onClick = vm::install) { Text("Pasang") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Nanti") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Pembaruan gagal") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("Tutup") } },
        )
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}
