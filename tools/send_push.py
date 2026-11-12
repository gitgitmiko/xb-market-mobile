#!/usr/bin/env python3
"""Kirim push pengumuman ke semua perangkat (topik "all") atau ke satu akun.

    pip install firebase-admin
    export GOOGLE_APPLICATION_CREDENTIALS=service-account.json
    python3 tools/send_push.py --title "Judul" --body "Isi"
    python3 tools/send_push.py --email kamu@gmail.com --title "Tes" --body "Halo"
    python3 tools/send_push.py --title "Diskon!" --body "..." --game 9P3J32CTXLRZ --game-title "ELDEN RING"

--game membuat notifikasi membuka halaman game itu saat diketuk.
"""
import argparse

import firebase_admin
from firebase_admin import auth, firestore, messaging


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--email", help="kirim hanya ke perangkat akun ini")
    parser.add_argument("--game", help="productId game yang dibuka saat notifikasi diketuk")
    parser.add_argument("--game-title", default="")
    args = parser.parse_args()

    firebase_admin.initialize_app()
    data = {"type": "announcement", "title": args.title, "body": args.body}
    if args.game:
        data.update(gameId=args.game, gameTitle=args.game_title)
    android = messaging.AndroidConfig(priority="high")

    if not args.email:
        message_id = messaging.send(messaging.Message(topic="all", data=data, android=android))
        print("SENT topic all", message_id)
        return
    uid = auth.get_user_by_email(args.email).uid
    tokens = [t.id for t in firestore.client().collection("users").document(uid).collection("tokens").stream()]
    if not tokens:
        raise SystemExit(f"{args.email} belum punya perangkat terdaftar")
    response = messaging.send_each([messaging.Message(token=t, data=data, android=android) for t in tokens])
    print(f"SENT {response.success_count}/{len(tokens)} perangkat")


if __name__ == "__main__":
    main()
