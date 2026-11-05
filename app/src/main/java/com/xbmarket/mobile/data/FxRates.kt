package com.xbmarket.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kurs dari open.er-api.com (gratis, tanpa API key, diperbarui harian).
 * Xbox Bargains memakai backend sendiri (`/currency/{currency}/rates`); kita pakai sumber publik.
 */
class FxRates(private val client: OkHttpClient = Http.client) {

    @Serializable
    private data class RatesResponse(val result: String, val rates: Map<String, Double> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    /** Kurs relatif terhadap USD, disimpan di memori selama 6 jam. */
    private suspend fun usdRates(): Map<String, Double> = mutex.withLock {
        cached?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Kurs HTTP ${response.code}")
                val body = json.decodeFromString<RatesResponse>(response.body!!.string())
                if (body.result != "success") error("Kurs tidak tersedia")
                body.rates
            }
        }.also { cached = System.currentTimeMillis() to it }
    }

    /** Fungsi konversi ke [base]; mengembalikan null untuk mata uang yang tidak dikenal. */
    suspend fun converter(base: String): (Double, String) -> Double? {
        val rates = usdRates()
        val baseRate = rates[base] ?: error("Mata uang $base tidak dikenal")
        return { amount, currency -> rates[currency]?.let { amount / it * baseRate } }
    }

    // Cache dibagi semua instance agar layar Diskon dan detail game tidak mengunduh ulang.
    private companion object {
        const val CACHE_MS = 6 * 60 * 60 * 1000L
        val mutex = Mutex()
        var cached: Pair<Long, Map<String, Double>>? = null
    }
}
