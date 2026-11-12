# XB Market

Aplikasi Android (Kotlin + Jetpack Compose) untuk membandingkan harga game Xbox di 44 region Microsoft Store.

Fitur: daftar diskon per region (scroll tanpa batas; filter platform/genre di server, diskon minimal, harga maks, jenis, sembunyikan Game Pass; urut diskon/harga termurah/termahal/terbaru), cari game, harga per region dikonversi ke satu mata uang dan diurutkan dari termurah, badge diskon dan tanggal berakhirnya, daftar pantauan dengan harga region utama, diskon, dan region termurah; notifikasi turun harga (tanpa login: WorkManager lokal tiap 6 jam; dengan login Google: push FCM dari server), pemindai region termurah untuk 60 diskon teratas, pengaturan mata uang dan region utama.

## Sumber data

- Harga: `https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds={id}&market={XX}&languages=en-us`
  (endpoint publik yang sama dengan Xbox Bargains). Harga = `DisplaySkuAvailabilities[0]` → availability dengan action `Purchase` → `OrderManagementData.Price` (`ListPrice` harga sekarang, `MSRP` harga normal).
- Pencarian: `.../v7.0/productFamilies/autoSuggest?productFamilyNames=games&platformDependencyName=windows.xbox&query=...`
- Daftar diskon: `POST https://emerald.xboxservices.com/xboxcomfd/browse?locale=en-{XX}` (API browse xbox.com) dengan header `x-ms-api-version: 1.1` dan `MS-CV`, body berisi filter base64 `orderby=DiscountPercentage desc` + `Price=OnSale`, halaman berikutnya lewat `EncodedCT`. Filter OnSale hanya dihormati di sebagian market; di market lain hasil tetap terurut diskon sehingga paging berhenti saat diskon 0.
- Kurs: `https://open.er-api.com/v6/latest/USD` (gratis, tanpa key, cache 6 jam).

Xbox Bargains juga memakai backend privat `api.xbbargains.app` (`{region}/deals`, `{region}/{pid}/price-history`, `/currency/{c}/rates`). Backend itu tidak kita pakai.

## Firebase (login Google, sinkron pantauan, push)

Opsional: tanpa `app/google-services.json` aplikasi tetap bisa dibangun dan menu akun disembunyikan.

1. Buat project Firebase, tambahkan app Android `com.xbmarket.mobile`, tambahkan SHA-1 debug/release, unduh `google-services.json` ke `app/`.
2. Aktifkan Authentication → Google, Cloud Firestore, dan Cloud Messaging (FCM API v1).
3. Deploy rules: `firebase deploy --only firestore:rules` (pakai `firestore.rules`).
4. Push turun harga: buat service account (Project settings → Service accounts → Generate key), simpan isinya sebagai secret
   GitHub `FIREBASE_SERVICE_ACCOUNT`, lalu set variable repo `PRICE_ALERTS_ENABLED=true`. Workflow
   `.github/workflows/price-alerts.yml` menjalankan `tools/price_alerts.py` tiap 6 jam (salin dari
   `tools/price-alerts.workflow.yml`; token push butuh scope `workflow`).
5. Pengumuman manual: `GOOGLE_APPLICATION_CREDENTIALS=service-account.json python3 tools/send_push.py --title ... --body ...`
   (semua perangkat lewat topik `all`, atau `--email` untuk satu akun).

Data Firestore: `users/{uid}` (homeMarket, baseCurrency, priceAlerts), `users/{uid}/watchlist/{productId}`
(title, image, addedAt; lastPrice/lastMarket ditulis server), `users/{uid}/tokens/{fcmToken}`.

## Update OTA (GitHub Releases)

Aplikasi mengecek `releases/latest` repo ini saat dibuka (paling sering tiap 6 jam) dan lewat Pengaturan → Cek pembaruan.
Bila tag `v<versionName>` lebih baru dari versi terpasang, APK di rilis itu diunduh lalu dipasang lewat Package Installer.

Merilis versi baru:
1. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`, commit, push.
2. `GITHUB_TOKEN=... tools/release.sh "Catatan rilis"` (token dengan izin Contents read/write repo ini).

APK rilis ditandatangani `xb-market-release.jks` lewat `keystore.properties` (keduanya tidak di-commit; simpan cadangannya).
Update hanya bisa dipasang di atas APK yang ditandatangani kunci yang sama. Untuk login Google, tambahkan SHA-1 kunci ini ke app Firebase.

## Build

`./gradlew :app:assembleDebug` (JDK 17, Android SDK 36).
