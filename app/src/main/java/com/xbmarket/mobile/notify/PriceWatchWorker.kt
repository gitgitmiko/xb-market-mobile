package com.xbmarket.mobile.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.Regions
import com.xbmarket.mobile.data.XboxCatalog
import java.util.concurrent.TimeUnit

/**
 * Mengecek harga game pantauan di region utama dan memberi notifikasi bila lebih murah dari
 * harga terakhir yang tercatat. Game yang baru dipantau hanya dicatat, tidak diberi notifikasi.
 */
class PriceWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        if (!Prefs.priceAlerts.value) return Result.success()
        val watchlist = Prefs.watchlist.value
        val region = Regions.byMarket(Prefs.homeMarket.value) ?: return Result.success()
        if (watchlist.isEmpty()) return Result.success()

        val prices = runCatching { XboxCatalog().pricesInMarket(watchlist.map { it.id }, region) }
            .getOrElse { return Result.retry() }
        watchlist.forEach { game ->
            val now = prices[game.id] ?: return@forEach
            val before = Prefs.lastPrice(game.id, region.market)
            // Ambang 0,5% menghindari notifikasi karena pembulatan harga.
            if (before != null && now.price < before * 0.995) {
                PriceNotifications.notifyDrop(applicationContext, game, now, before)
            }
            Prefs.setLastPrice(game.id, region.market, now.price)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "price-watch"
        private const val WORK_NOW = "price-watch-now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceWatchWorker>(6, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PriceWatchWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
