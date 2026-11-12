package com.xbmarket.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.notify.PriceNotifications
import com.xbmarket.mobile.notify.PriceWatchWorker
import com.xbmarket.mobile.ui.XbMarketApp
import com.xbmarket.mobile.ui.XbMarketTheme
import com.xbmarket.mobile.update.UpdateDialog

class MainActivity : ComponentActivity() {
    /** Game yang diminta dibuka dari notifikasi; dikosongkan setelah dibuka. */
    private var pendingGame by mutableStateOf<GameSummary?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (Prefs.priceAlerts.value) PriceWatchWorker.schedule(this)
        if (savedInstanceState == null) pendingGame = gameFrom(intent)
        setContent {
            XbMarketTheme {
                XbMarketApp(pendingGame = pendingGame, onPendingGameOpened = { pendingGame = null })
                UpdateDialog(viewModel())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gameFrom(intent)?.let { pendingGame = it }
    }

    private fun gameFrom(intent: Intent?): GameSummary? {
        val id = intent?.getStringExtra(PriceNotifications.EXTRA_GAME_ID) ?: return null
        return GameSummary(
            id,
            intent.getStringExtra(PriceNotifications.EXTRA_GAME_TITLE).orEmpty(),
            intent.getStringExtra(PriceNotifications.EXTRA_GAME_IMAGE),
        )
    }
}
