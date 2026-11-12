package com.xbmarket.mobile.data

import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sinkron akun ke Firestore agar server bisa mengirim push turun harga:
 * - `users/{uid}`: region utama, mata uang, dan status notifikasi.
 * - `users/{uid}/watchlist/{productId}`: game pantauan (server menambahkan `lastPrice`/`lastMarket`).
 * - `users/{uid}/tokens/{fcmToken}`: perangkat yang menerima push.
 *
 * Saat login, pantauan lokal digabung ke akun; setelah itu daftar di akun menjadi sumbernya.
 */
object CloudSync {
    private const val TAG = "CloudSync"
    /** Semua perangkat berlangganan topik ini untuk pengumuman dari developer (tools/send_push.py). */
    const val TOPIC_ALL = "all"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: ListenerRegistration? = null
    private var uid: String? = null

    private val db get() = FirebaseFirestore.getInstance()
    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun start() {
        if (!Account.available) return
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
        scope.launch {
            Account.user.collect { user ->
                listener?.remove()
                listener = null
                uid = user?.uid
                if (user != null) onSignedIn(user.uid, user.email)
            }
        }
        scope.launch {
            combine(Account.user, Prefs.homeMarket, Prefs.baseCurrency, Prefs.priceAlerts) { user, market, base, alerts ->
                user?.uid?.let { Triple(it, market, base to alerts) }
            }.distinctUntilChanged().collect { value ->
                val (id, market, rest) = value ?: return@collect
                userDoc(id).set(
                    mapOf("homeMarket" to market, "baseCurrency" to rest.first, "priceAlerts" to rest.second, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                ).addOnFailureListener { Log.w(TAG, "profil gagal disimpan", it) }
            }
        }
    }

    private suspend fun onSignedIn(uid: String, email: String?) {
        runCatching {
            userDoc(uid).set(mapOf("email" to email), SetOptions.merge())
            // Gabungkan pantauan lokal sekali per akun, lalu ikuti daftar di akun.
            if (Prefs.syncedUid() != uid) {
                val remote = userDoc(uid).collection("watchlist").get().await().documents.map { it.id }.toSet()
                Prefs.watchlist.value.filter { it.id !in remote }.forEach { upload(uid, it) }
                Prefs.setSyncedUid(uid)
            }
            saveToken(FirebaseMessaging.getInstance().token.await())
        }.onFailure { Log.w(TAG, "sinkron awal gagal", it) }
        if (this.uid != uid) return
        listener = userDoc(uid).collection("watchlist").orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                Prefs.replaceWatchlist(
                    snapshot.documents.map { d ->
                        GameSummary(d.id, d.getString("title").orEmpty(), d.getString("image"))
                    },
                )
            }
    }

    fun onWatchToggled(game: GameSummary, added: Boolean) {
        val id = uid ?: return
        if (added) upload(id, game) else userDoc(id).collection("watchlist").document(game.id).delete()
    }

    /** Perbarui judul/gambar tanpa mengubah urutan (`addedAt`) dan harga terakhir dari server. */
    fun updateDetails(game: GameSummary) {
        val id = uid ?: return
        userDoc(id).collection("watchlist").document(game.id)
            .update(mapOf("title" to game.title, "image" to game.image))
    }

    private fun upload(uid: String, game: GameSummary) {
        userDoc(uid).collection("watchlist").document(game.id).set(
            mapOf("title" to game.title, "image" to game.image, "addedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    /** Dipanggil dari [com.xbmarket.mobile.notify.PushService] saat token FCM berganti. */
    fun saveToken(token: String) {
        val id = uid ?: return
        userDoc(id).collection("tokens").document(token).set(
            mapOf("platform" to "android", "model" to Build.MODEL, "updatedAt" to FieldValue.serverTimestamp()),
        )
    }

    /** Hapus token perangkat ini dari akun agar push tidak dikirim lagi setelah keluar. */
    suspend fun beforeSignOut() {
        val id = uid ?: return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            userDoc(id).collection("tokens").document(token).delete().await()
        }
        Prefs.setSyncedUid(null)
    }

    /** True bila notifikasi turun harga dikirim server lewat push, jadi pengecekan lokal tidak perlu. */
    val serverAlerts: Boolean get() = Account.available && Account.user.value != null
}
