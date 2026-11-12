package com.xbmarket.mobile.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xbmarket.mobile.data.CloudSync
import com.xbmarket.mobile.data.GameSummary
import com.xbmarket.mobile.data.Prefs
import com.xbmarket.mobile.data.RegionPrice
import com.xbmarket.mobile.data.Regions

/**
 * Push FCM dari server (tools/price_alerts.py dan tools/send_push.py). Server mengirim pesan data saja
 * agar notifikasi selalu dibuat di sini dengan format harga dan tautan ke halaman game yang sama.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CloudSync.saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val game = data["gameId"]?.let { GameSummary(it, data["gameTitle"].orEmpty(), data["gameImage"]) }
        when (data["type"]) {
            "price_drop" -> {
                Prefs.init(applicationContext)
                if (!Prefs.priceAlerts.value || game == null) return
                val region = Regions.byMarket(data["market"].orEmpty()) ?: return
                val price = data["price"]?.toDoubleOrNull() ?: return
                val before = data["before"]?.toDoubleOrNull() ?: return
                val now = RegionPrice(
                    region = region,
                    currency = data["currency"].orEmpty(),
                    price = price,
                    msrp = data["msrp"]?.toDoubleOrNull() ?: price,
                    saleEnds = data["saleEnds"]?.takeIf { it.isNotBlank() },
                )
                PriceNotifications.notifyDrop(applicationContext, game, now, before)
            }
            else -> {
                val title = data["title"] ?: message.notification?.title ?: return
                val body = data["body"] ?: message.notification?.body.orEmpty()
                PriceNotifications.notifyAnnouncement(applicationContext, title, body, game)
            }
        }
    }
}
