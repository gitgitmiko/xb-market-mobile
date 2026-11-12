#!/usr/bin/env python3
"""Cek harga game pantauan semua akun dan kirim push FCM saat turun harga di region utama.

    pip install firebase-admin
    GOOGLE_APPLICATION_CREDENTIALS=service-account.json python3 tools/price_alerts.py [--dry-run]

Dijalankan tiap 6 jam oleh .github/workflows/price-alerts.yml (template: tools/price-alerts.workflow.yml). Data di Firestore:
users/{uid} (homeMarket, priceAlerts), users/{uid}/watchlist/{productId} (lastPrice, lastMarket
ditulis skrip ini), users/{uid}/tokens/{fcmToken}. Game yang baru dipantau hanya dicatat harganya.
"""
import argparse
import json
import urllib.parse
import urllib.request

import firebase_admin
from firebase_admin import firestore, messaging

CATALOG = "https://displaycatalog.mp.microsoft.com/v7.0/products"
# Ambang 0,5% menghindari push karena pembulatan harga (sama dengan PriceWatchWorker).
DROP_RATIO = 0.995
# Token yang tidak valid lagi; dihapus dari Firestore.
DEAD_TOKEN_ERRORS = (messaging.UnregisteredError, messaging.SenderIdMismatchError)


def purchase_price(product):
    """Harga edisi dasar (SKU pertama) yang bisa dibeli, seperti XboxCatalog.purchasePrice."""
    skus = product.get("DisplaySkuAvailabilities") or []
    if not skus:
        return None
    for availability in skus[0].get("Availabilities", []):
        price = (availability.get("OrderManagementData") or {}).get("Price")
        if "Purchase" in availability.get("Actions", []) and price and price.get("CurrencyCode"):
            on_sale = price["ListPrice"] < price["MSRP"]
            return {
                "currency": price["CurrencyCode"],
                "price": price["ListPrice"],
                "msrp": price["MSRP"],
                "saleEnds": ((availability.get("Conditions") or {}).get("EndDate") or "") if on_sale else "",
            }
    return None


def prices_in_market(ids, market):
    result = {}
    ids = sorted(ids)
    for start in range(0, len(ids), 20):
        query = urllib.parse.urlencode({"bigIds": ",".join(ids[start:start + 20]), "market": market, "languages": "en-us"})
        request = urllib.request.Request(f"{CATALOG}?{query}", headers={"MS-CV": "xbmarketsrv0000.1"})
        with urllib.request.urlopen(request, timeout=30) as response:
            for product in json.load(response).get("Products", []):
                price = purchase_price(product)
                if price:
                    result[product["ProductId"]] = price
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="tidak mengirim push dan tidak menulis Firestore")
    args = parser.parse_args()

    firebase_admin.initialize_app()
    db = firestore.client()

    # Kumpulkan pantauan semua akun yang notifikasinya aktif dan punya perangkat.
    users = []
    for user in db.collection("users").stream():
        data = user.to_dict()
        if data.get("priceAlerts") is False:
            continue
        tokens = [t.id for t in user.reference.collection("tokens").stream()]
        if not tokens:
            continue
        watch = list(user.reference.collection("watchlist").stream())
        if watch:
            users.append((user, data.get("homeMarket") or "ID", tokens, watch))

    wanted = {}
    for _, market, _, watch in users:
        wanted.setdefault(market, set()).update(doc.id for doc in watch)
    prices = {}
    for market, ids in wanted.items():
        try:
            prices[market] = prices_in_market(ids, market)
        except Exception as error:  # satu market gagal tidak menghentikan yang lain
            print(f"market {market} gagal: {error}")

    sent = 0
    for user, market, tokens, watch in users:
        for doc in watch:
            now = prices.get(market, {}).get(doc.id)
            if not now:
                continue
            data = doc.to_dict()
            before = data.get("lastPrice") if data.get("lastMarket") == market else None
            if before is not None and now["price"] < before * DROP_RATIO:
                payload = {
                    "type": "price_drop",
                    "gameId": doc.id,
                    "gameTitle": data.get("title") or "",
                    "gameImage": data.get("image") or "",
                    "market": market,
                    "currency": now["currency"],
                    "price": str(now["price"]),
                    "msrp": str(now["msrp"]),
                    "before": str(before),
                    "saleEnds": now["saleEnds"],
                }
                print(f"{user.id} {market} {data.get('title')}: {before} -> {now['price']}")
                if not args.dry_run:
                    sent += send(user, tokens, payload)
            if not args.dry_run and before != now["price"]:
                doc.reference.update({"lastPrice": now["price"], "lastMarket": market})
    print(f"{len(users)} akun, {sent} push terkirim")


def send(user, tokens, payload):
    messages = [
        messaging.Message(token=token, data=payload, android=messaging.AndroidConfig(priority="high"))
        for token in tokens
    ]
    response = messaging.send_each(messages)
    for token, result in zip(tokens, response.responses):
        if not result.success and isinstance(result.exception, DEAD_TOKEN_ERRORS):
            user.reference.collection("tokens").document(token).delete()
    return response.success_count


if __name__ == "__main__":
    main()
