# 📡 WireFM

<p align="center">
  <img src="Assets/benner.png" alt="WireFM Banner" width="100%" />
</p>

> **Ultra-lightweight, zero-config wireless file manager connecting Linux PC & Android phones via QR code and WebDAV.**

---

## ⚡ Highlights

- 🎯 **1-Command Run**: Just run `wirefm` in your terminal.
- 📱 **QR Code Auto-Pair**: Scan the terminal QR code from your phone to connect instantly.
- 📂 **Bidirectional File Management**: Browse, copy, move, delete, upload, and download files from PC to phone and phone to PC.
- 🐧 **Native Linux File Manager Integration**: Auto-detects and mounts inside **Dolphin**, **Thunar**, or **Nautilus**.
- 🪶 **Zero Bloat & Extremely Fast**:
  - PC server is a single **~11MB Go binary** with zero runtime dependencies.
  - Web UI is 100% embedded directly inside the binary.
  - Native Kotlin Android app with clean lavender/purple UI.

---

## 🚀 Quick Start (PC)

### 1-Line Install & Run:
```bash
curl -sSL https://raw.githubusercontent.com/jasmin1727/wirefm/main/install.sh | bash
```

### Or Run Locally:
```bash
cd ~/Projects/wirefm
./wirefm
```

When you start `wirefm`:
1. It prints a **QR Code** directly in your terminal.
2. It spins up the **REST API** (`:8080`) and **WebDAV** (`:8081`).
3. Open on your phone's browser or scan with the **WireFM Android App**.

---

## 📱 Mobile App (Android)

- Built in **Kotlin + Jetpack Components**.
- Includes built-in **ZXing QR scanner** for 1-tap connection.
- Modern lavender design with instant device tabs:
  - 💻 **PC Storage** (`/home/jasmin/`)
  - 📱 **Internal Storage** (DCIM, Downloads, Documents, etc.)

### Build APK:
```bash
cd android
./gradlew assembleDebug
```
The output APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 📂 Native File Manager Auto-Mount

To access your phone directly from Linux Explorer (**Dolphin** or **Thunar**):
```bash
./mount_phone.sh <PHONE_IP>
```
This automatically mounts using `gio` / `kio-fuse` so your phone appears under Network drives in Dolphin & Thunar!

---

## 🛠️ Project Structure

```
wirefm/
├── wirefm              # Standalone compiled 11MB binary (Go + embedded Web UI)
├── wirefm.sh           # Auto-detection launcher script
├── mount_phone.sh      # Native Dolphin/Thunar network mounter
├── install.sh          # 1-command installer script
├── server/             # Go WebDAV & REST server source
│   ├── main.go
│   └── static/
│       └── index.html  # Embedded purple theme web interface
└── android/            # Native Kotlin Android Application
    ├── app/src/main/
    │   ├── kotlin/com/wirefm/
    │   └── res/        # Custom purple themes & layouts
    └── build.gradle.kts
```

---

## 🌟 License

MIT License © 2026 Jasmin Thakor
