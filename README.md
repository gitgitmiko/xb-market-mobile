# XB Market

Aplikasi Android (Kotlin + Jetpack Compose) untuk membandingkan harga game Xbox di 44 region Microsoft Store.

Fitur: daftar diskon per region (scroll tanpa batas; filter platform/genre di server, diskon minimal, harga maks, jenis, sembunyikan Game Pass; urut diskon/harga/terbaru), cari game, harga per region dikonversi ke satu mata uang dan diurutkan dari termurah, badge diskon dan tanggal berakhirnya, daftar pantauan (lokal) dengan notifikasi turun harga (WorkManager, tiap 6 jam, region utama), pemindai region termurah untuk 60 diskon teratas, pengaturan mata uang dan region utama.

## Sumber data

- Harga: `https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds={id}&market={XX}&languages=en-us`
  (endpoint publik yang sama dengan Xbox Bargains). Harga = `DisplaySkuAvailabilities[0]` → availability dengan action `Purchase` → `OrderManagementData.Price` (`ListPrice` harga sekarang, `MSRP` harga normal).
- Pencarian: `.../v7.0/productFamilies/autoSuggest?productFamilyNames=games&platformDependencyName=windows.xbox&query=...`
- Daftar diskon: `POST https://emerald.xboxservices.com/xboxcomfd/browse?locale=en-{XX}` (API browse xbox.com) dengan header `x-ms-api-version: 1.1` dan `MS-CV`, body berisi filter base64 `orderby=DiscountPercentage desc` + `Price=OnSale`, halaman berikutnya lewat `EncodedCT`. Filter OnSale hanya dihormati di sebagian market; di market lain hasil tetap terurut diskon sehingga paging berhenti saat diskon 0.
- Kurs: `https://open.er-api.com/v6/latest/USD` (gratis, tanpa key, cache 6 jam).

Xbox Bargains juga memakai backend privat `api.xbbargains.app` (`{region}/deals`, `{region}/{pid}/price-history`, `/currency/{c}/rates`). Backend itu tidak kita pakai.

## Build

`./gradlew :app:assembleDebug` (JDK 17, Android SDK 36).
