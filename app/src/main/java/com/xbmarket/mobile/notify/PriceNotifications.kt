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
    private const val CHANNEL_NEWS = "news"
    const val EXTRA_GAME_ID = "gameId"
    const val EXTRA_GAME_TITLE = "gameTitle"
    const val EXTRA_GAME_IMAGE = "gameImage"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Satu notifikasi per game; mengetuknya membuka halaman game itu. */
    fun notifyDrop(context: Context, game: GameSummary, now: RegionPrice, before: Double) {
        val title = if (now.discountPercent > 0) "${game.title} diskon ${now.discountPercent}%" else "${game.title} turun harga"
        val text = "${now.region.flag} ${formatMoney(now.price, now.currency)} (sebelumnya ${formatMoney(before, now.currency)})" +
            (now.saleEnds?.let { " · s.d. ${com.xbmarket.mobile.ui.formatDate(it)}" } ?: "")
        show(context, CHANNEL, game.id.hashCode(), title, text, game)
    }

    /** Pengumuman dari developer lewat push; [game] opsional untuk membuka halaman game. */
    fun notifyAnnouncement(context: Context, title: String, text: String, game: GameSummary?) {
        show(context, CHANNEL_NEWS, (game?.id ?: title).hashCode(), title, text, game)
    }

    private fun show(context: Context, channel: String, id: Int, title: String, text: String, game: GameSummary?) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        game?.let {
            intent.putExtra(EXTRA_GAME_ID, it.id).putExtra(EXTRA_GAME_TITLE, it.title).putExtra(EXTRA_GAME_IMAGE, it.image)
        }
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // dicek lewat canNotify()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_NEWS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Turun harga", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pemberitahuan saat game di daftar pantauan turun harga di region utama"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NEWS, "Info XB Market", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pengumuman dan promo dari developer"
            },
        )
    }
}
