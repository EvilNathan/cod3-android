# Project 1944 Android — Panduan Tester (APK debug)

Port Android Call of Duty 3 (rekompilasi Xbox 360 → ReXGlue → ARM64 Android).
Status: **M3/M4 — boot, render, input sentuh + gamepad, audio, save/load.**

> Data game **tidak** dibundel ke APK. Kamu butuh salinan game sendiri
> (ISO Xbox 360 milikmu) — sekarang **tanpa ekstraksi**: ISO langsung
> di-mount. APK bersifat lokal; jangan didistribusikan bersama file game.

## 1. Persiapan sekali saja

1. **Data game** — dua opsi:
   - **ISO langsung (disarankan)**: taruh `Call of Duty 3.iso` di memori
     internal, mis. `/sdcard/Call of Duty 3.iso`, lalu di app tekan
     **PICK ISO** dan pilih file itu. **Tidak perlu ekstrak** — game boot
     langsung dari disc image (di-mount read-only via GDFX/XISO di native).
   - **Folder terekstrak**: `/sdcard/cod3/default.xex` + `sp/…` + `config/…`
     (hasil ekstrak ISO sebelumnya juga tetap didukung).
   (Saat HP terhubung via USB/ADB, script `D:\stage\deploy-device.ps1` bisa
   menyalin file dari `D:\cod3-game` secara otomatis.)

2. **Izinkan akses Semua File** (MANAGE_EXTERNAL_STORAGE) untuk aplikasi
   "Project 1944":
   *Settings → Apps → Project 1944 → Permissions → Files and media → Allow
   management of all files*, atau buka *Settings → Special app access →
   All files access*. Tanpa ini game tidak bisa membaca `/sdcard/cod3`.

3. **Pasang APK**: `app-debug.apk` (debug-signed). ADB:
   ```
   adb install -r app-debug.apk
   ```

## 2. Menjalankan

1. Buka aplikasi **Project 1944**.
2. Di layar utama pilih **PICK ISO** (disc image) atau **PICK FOLDER**
   (folder hasil ekstrak); tombol **RECHECK** memvalidasi pilihan.
3. Tekan **LAUNCH GAME** — game boot: loading → menu utama. Boot pertama
   bisa 1–3 menit (kompilasi pipeline Vulkan sekali jalan).

### Kontrol sentuh
Overlay di layar (kiri = gerak, kanan = kamera):
- **Stik kiri** — gerak; **stik kanan** — pandangan (sentuh area, seret).
- Tombol muka **A/B/X/Y** (kanan bawah), **LB/RB** + **LT/RT** (pilar atas).
- **BACK / START** di tengah atas.
- Gamepad fisik (Xbox/DS4/DualSense via Bluetooth) otomatis dipakai untuk
  semua port XInput — overlay tetap bisa dipakai bersamaan.

## 3. Di mana log-nya

Jika layar hitam / crash / keluar sendiri:

1. **In-app log** (jika bisa masuk):
   `/sdcard/Android/data/com.project1944.cod3/files/`
   - `logfile.txt` — log game (ReXGlue + game)
   - `native-log.txt` — log native
2. **logcat**:
   ```
   adb logcat -s project1944 SDL/APP AndroidRuntime DEBUG
   ```
   Tag `project1944` = log port; `SDL/APP` = SDL3 Android backend.

Kirim ke pengembang: dua file di atas + potongan `logcat` saat crash.

## 4. Checklist validasi (lapor per item)

| # | Item | Cara cek | Hasil yang diharapkan |
|---|---|---|---|
| M3-1 | Boot | Launch app → PICK ISO/folder | Loading → menu utama, ada frame pertama |
| M3-2 | Render menu | Main menu | Tampil normal (bukan hitam/putih) |
| M3-3 | Level 1 load | Mulai campaign Saint-Lô | Load selesai → briefing/level |
| M3-4 | Render in-game | Bergerak 30 dtk | Adegan tampil, HUD terlihat |
| M4-1 | Stik kiri | Gerakkan karakter | Maju/mundur/strafe sesuai arah sentuh |
| M4-2 | Stik kanan | Pandangan | Kamera mengikuti seret |
| M4-3 | A/B/X/Y | Melewati briefing, lompat/tembak | Aksi sesuai tombol |
| M4-4 | LT/RT + LB/RB | Aim, tembak, granat | Aksi sesuai tombol |
| M4-5 | BACK/START | Pause menu | Menu pause muncul |
| M4-6 | Gamepad fisik | Pasang via BT, coba semua | Kontrol jalan tanpa overlay |
| M5-1 | Audio | Menu + in-game | Musik/SFX terdengar |
| M6-1 | Save | Capai checkpoint, keluar, lanjut | Progres tersimpan & ter-load |

## 5. Masalah yang diketahui

- **Pertama kali setelah install**: build Vulkan pipeline butuh waktu; jangan
  dipaksa-close saat "Compiling shaders" (jika ada indikator).
- **Performa** menengah-rendah: Snapdragon 8 Gen 1+ disarankan untuk 30+ fps
  pada resolusi internal default. Di HP kencang pun bisa turun di area berat.
- **Gyro & layout editor** belum ada (deferred setelah "core playable" lolos).
- Kalau game keluar saat pilih folder: izin Semua File belum aktif (poin 1.2).

## 6. Alur laporan

Kirim:
1. Perangkat (merk/model, SoC, RAM, Android versi).
2. Checklist di atas (per item: OK / FAIL + keterangan).
3. `logfile.txt`, `native-log.txt`, potongan `logcat` saat kejadian.
4. Screenshot/rekaman jika memungkinkan.
