package com.xbmarket.mobile.data

/** Market Microsoft Store yang dibandingkan. Kode market = kode negara ISO 3166 (dipakai displaycatalog). */
data class Region(val market: String, val name: String, val flag: String)

object Regions {
    /** Terurut menurut nama agar mudah dicari di pemilih region. */
    val all: List<Region> = listOf(
        Region("ID", "Indonesia", "🇮🇩"),
        Region("US", "Amerika Serikat", "🇺🇸"),
        Region("GB", "Inggris", "🇬🇧"),
        Region("CA", "Kanada", "🇨🇦"),
        Region("AU", "Australia", "🇦🇺"),
        Region("NZ", "Selandia Baru", "🇳🇿"),
        Region("IN", "India", "🇮🇳"),
        Region("SG", "Singapura", "🇸🇬"),
        Region("MY", "Malaysia", "🇲🇾"),
        Region("PH", "Filipina", "🇵🇭"),
        Region("TH", "Thailand", "🇹🇭"),
        Region("VN", "Vietnam", "🇻🇳"),
        Region("JP", "Jepang", "🇯🇵"),
        Region("KR", "Korea Selatan", "🇰🇷"),
        Region("HK", "Hong Kong", "🇭🇰"),
        Region("TW", "Taiwan", "🇹🇼"),
        Region("BR", "Brasil", "🇧🇷"),
        Region("AR", "Argentina", "🇦🇷"),
        Region("MX", "Meksiko", "🇲🇽"),
        Region("CL", "Chili", "🇨🇱"),
        Region("CO", "Kolombia", "🇨🇴"),
        Region("PE", "Peru", "🇵🇪"),
        Region("TR", "Turki", "🇹🇷"),
        Region("ZA", "Afrika Selatan", "🇿🇦"),
        Region("NG", "Nigeria", "🇳🇬"),
        Region("EG", "Mesir", "🇪🇬"),
        Region("SA", "Arab Saudi", "🇸🇦"),
        Region("AE", "Uni Emirat Arab", "🇦🇪"),
        Region("IL", "Israel", "🇮🇱"),
        Region("KZ", "Kazakhstan", "🇰🇿"),
        Region("UA", "Ukraina", "🇺🇦"),
        Region("PL", "Polandia", "🇵🇱"),
        Region("CZ", "Ceko", "🇨🇿"),
        Region("HU", "Hungaria", "🇭🇺"),
        Region("NO", "Norwegia", "🇳🇴"),
        Region("SE", "Swedia", "🇸🇪"),
        Region("DK", "Denmark", "🇩🇰"),
        Region("IS", "Islandia", "🇮🇸"),
        Region("CH", "Swiss", "🇨🇭"),
        Region("DE", "Jerman", "🇩🇪"),
        Region("FR", "Prancis", "🇫🇷"),
        Region("IT", "Italia", "🇮🇹"),
        Region("ES", "Spanyol", "🇪🇸"),
        Region("NL", "Belanda", "🇳🇱"),
    ).sortedBy { it.name }

    fun byMarket(market: String): Region? = all.firstOrNull { it.market == market }
}
