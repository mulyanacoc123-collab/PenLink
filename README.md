# PenLink — Wireless Pen Display (Android, Kotlin)

Satu APK, dua peran: **Host** (HP Snapdragon, menerima pena + membagikan layar) dan **Client** (tablet).

## Build
1. Buka folder `PenLink` di Android Studio (Koala/2024.1+, JDK 17). Sync Gradle.
2. Run `app` di KEDUA perangkat.

## Persiapan Host
1. Pasang **Shizuku**, jalankan lewat *Wireless debugging* (ulangi setelah reboot).
2. PenLink → Host → "Beri izin Shizuku" → "Mulai Host" → izinkan perekaman layar.
3. Catat kode 4 digit & alamat IP di layar.

## Mode koneksi (Client)
| Mode | Cara |
|---|---|
| Wi-Fi | Satu jaringan, isi IP host. |
| USB / ADB | Dengan PC: `adb -s HOST forward tcp:8080 tcp:8080` dan `adb -s TABLET reverse tcp:8080 tcp:8080`; tablet konek ke 127.0.0.1. |
| USB Tethering | Aktifkan tethering di host, kabel ke tablet, isi IP gateway (mis. 192.168.42.129). |

## Catatan
- Tekanan 0..1, tilt, hover, tombol stylus, dan eraser disuntikkan sebagai MotionEvent stylus asli.
- Pairing: 5 kali salah → kunci 30 detik. Lalu lintas tidak terenkripsi (ws://) — cukup untuk USB/LAN rumah, bukan jaringan publik.
- Batasan: 1 pointer, orientasi host sebaiknya dikunci, hidden API bisa berbeda antar versi Android.
- Frame yang tidak berubah tidak dikirim ulang (hemat bandwidth); client baru langsung menerima frame terakhir.
