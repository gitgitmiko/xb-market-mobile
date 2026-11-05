package com.xbmarket.mobile.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xbmarket.mobile.MainActivity
import com.xbmarket.mobile.R
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.ui.formatMoney

object PriceNotifications {
    private const val CHANNEL = "price_drops"
    const val EXTRA_GAME_ID = "gameId"
    const val EXTRA_GAME_TITLE = "gameTitle"
    const val EXTRA_GAME_IMAGE = "gameImage"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Satu notifikasi per game; mengetuknya membuka halaman game itu. */
    fun notifyDrop(context: Context, game: GameSummary, now: RegionPrice, before: Double) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            game.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_GAME_ID, game.id)
                .putExtra(EXTRA_GAME_TITLE, game.title)
                .putExtra(EXTRA_GAME_IMAGE, game.image),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (now.discountPercent > 0) "${game.title} diskon ${now.discountPercent}%" else "${game.title} turun harga"
        val text = "${now.region.flag} ${formatMoney(now.price, now.currency)} (sebelumnya ${formatMoney(before, now.currency)})" +
            (now.saleEnds?.let { " · s.d. ${com.xbmarket.mobile.ui.formatDate(it)}" } ?: "")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // dicek lewat canNotify()
        NotificationManagerCompat.from(context).notify(game.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Turun harga", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pemberitahuan saat game di daftar pantauan turun harga di region utama"
            },
        )
    }
}
