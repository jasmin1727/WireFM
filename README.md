<p align="center">
  <img src="Assets/benner.png" alt="WireFM Banner" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/jasmin1727/WireFM/releases/latest/download/WireFM.apk">
    <img src="https://img.shields.io/badge/_Download_Android_APK-WireFM.apk-8B5CF6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="42" />
  </a>
  <a href="#-quick-start-pc">
    <img src="https://img.shields.io/badge/💻_Install_on_Linux-1--Line_Curl-4ECCA3?style=for-the-badge&logo=linux&logoColor=white" alt="Linux Install" height="42" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
  <img src="https://img.shields.io/badge/Platform-Linux%20%7C%20Android-purple.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Server%20Size-11%20MB%20(Go)-orange.svg" alt="Size" />
  <img src="https://img.shields.io/badge/Android-Kotlin-green.svg" alt="Kotlin" />
</p>

---

## 📥 Direct Downloads

> ### 📱 **[👉 CLICK HERE TO DOWNLOAD ANDROID APK (WireFM.apk)](https://github.com/jasmin1727/WireFM/releases/latest/download/WireFM.apk)**
> *Install directly on your Android phone without needing Android Studio!*

---

## ⚡ Highlights

- 🎯 **Zero Setup**: 1 command to run on PC, 1 tap to scan on Android.
- 📱 **Instant QR Code Pairing**: Terminal generates a QR code — scan with phone camera to connect in 1 second.
- 📂 **Bidirectional File Management**:
  - Browse, copy, move, rename, delete files on PC from phone.
  - Browse, download, and upload files to phone from PC.
- 🐧 **Native Linux Explorer Integration**:
  - Auto-detects **Dolphin**, **Thunar**, or **Nautilus**.
  - Mounts phone as a native network drive using WebDAV.
- 🪶 **Ultra Lightweight**:
  - PC server is a single **~11MB Go binary** with zero dependencies.
  - Web UI is 100% embedded inside the binary.
  - Clean Lavender/Purple theme matching modern desktop and phone aesthetics.

---

## 🚀 Quick Start (PC)

### 1-Line Install (Arch / Ubuntu / Debian / Fedora):
```bash
curl -sSL https://raw.githubusercontent.com/jasmin1727/WireFM/main/install.sh | bash
```

### Run WireFM:
```bash
wirefm
```
*(Or specify a custom directory to share: `wirefm /path/to/folder`)*

Terminal will show:
1. Local IP & WebDAV endpoints.
2. An ASCII QR Code.
3. Open the link or scan with the **WireFM Android App**.

---

## 📱 Mobile App (Android)


1. Download [`WireFM.apk`](https://github.com/jasmin1727/WireFM/releases/latest/download/WireFM.apk) onto your phone and install.
2. Open the app and tap **"Connect Your Pc (Scan QR)"**.
3. Point your camera at the PC terminal's QR code.
4. 🎉 **Done!** You now have complete access to manage all files and folders.

---

## 🐧 Native Dolphin / Thunar Auto-Mount

Want to open your phone inside your favorite Linux File Manager?
```bash
./mount_phone.sh <PHONE_IP>
```
This mounts via `gio` / `kio-fuse` directly into Dolphin or Thunar sidebar!

---

## 🛠️ Architecture & Tech Stack

```
           ┌──────────────────────┐
           │      WireFM (Go)     │
           │  • Port 8080 (REST)  │
           │  • Port 8081 (WebDAV)│
           │  • Terminal QR Code  │
           └──────────┬───────────┘
                      │ Local Wi-Fi (HTTP / WebDAV)
                      ▼
           ┌──────────────────────┐
           │   WireFM (Kotlin)    │
           │  • ZXing QR Scanner  │
           │  • Bi-directional FM │
           │  • Material Purple UI│
           └──────────────────────┘
```

---

## 📦 Build from Source

### PC Binary:
```bash
cd server
go build -o ../wirefm .
```

### Android APK:
```bash
cd android
gradle assembleDebug
```
Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🌟 Support & Contributions

If you find WireFM helpful, please consider giving it a ⭐ **Star** on [GitHub](https://github.com/jasmin1727/WireFM)!

## 📄 License

MIT License © 2026 [Jasmin Thakor](https://github.com/jasmin1727)
