package com.xbmarket.mobile.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

private val ID = Locale("in", "ID")

/** Mata uang yang dalam praktiknya tidak memakai pecahan, walau Java mencatat 2 desimal untuk sebagian. */
private val NoDecimals = setOf("IDR", "JPY", "KRW", "VND", "CLP", "COP", "ISK", "HUF", "KZT", "TWD", "NGN")

/** Format harga dengan simbol mata uang; nilai ribuan ke atas dan mata uang tanpa pecahan tidak diberi desimal. */
fun formatMoney(amount: Double, currency: String): String {
    val format = NumberFormat.getCurrencyInstance(ID)
    runCatching {
        val c = Currency.getInstance(currency)
        format.currency = c
        val digits = if (amount >= 1000 || currency in NoDecimals) 0 else c.defaultFractionDigits.coerceAtLeast(0)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
    }.onFailure { return "$currency ${"%,.2f".format(amount)}" }
    return format.format(amount)
}

/** Tanggal dari displaycatalog ("2026-11-12T23:59:59.0000000Z") atau browse ("11/12/2026 09:59:59") → "12 Nov 2026". */
fun formatDate(raw: String): String {
    val pattern = if (raw.contains('T')) "yyyy-MM-dd'T'HH:mm:ss" else "MM/dd/yyyy HH:mm:ss"
    return runCatching {
        val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        SimpleDateFormat("d MMM yyyy", ID).format(parser.parse(raw.take(19))!!)
    }.getOrDefault(raw.take(10))
}
