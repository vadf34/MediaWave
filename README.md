# MediaWave

<div align="center">

**A Modern Media Downloader for Android**

Download videos and audio from YouTube, TikTok, Instagram, and 1,800+ other sites

![Android](https://img.shields.io/badge/Android-10%2B-brightgreen?logo=android)
![Version](https://img.shields.io/badge/Версия-1.0.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?logo=kotlin)
[![Download APK](https://img.shields.io/badge/Скачать-APK-green?style=flat&logo=android)](https://github.com/vadf34/MediaWave/releases/latest)


</div>

---

## 📱 Screenshots

<div align="center">

| Home | History | Profile Parser | Platform Selector | Settings |
|:-------:|:-------:|:--------------:|:---------------:|:---------:|
| <img src="docs/screenshots/screen_home.png?v=2" width="140"/> | <img src="docs/screenshots/screen_history.png?v=2" width="140"/> | <img src="docs/screenshots/screen_parser.png?v=2" width="140"/> | <img src="docs/screenshots/screen_parser_select.png?v=2" width="140"/> | <img src="docs/screenshots/screen_settings.png?v=2" width="140"/> |

</div>

---

## ✨ Features

### 🔗 URL-based Download
Simply copy a link to any video or post — the app will automatically paste it from the clipboard on launch. Supports over 1,800 sites, including YouTube, TikTok, Instagram, Twitter/X, Facebook, VKontakte, Reddit, Pinterest, Twitch, Rumble, and many more. The download engine is powered by the latest version of yt-dlp, which can be updated directly from within the app.

### 👤 Profile Parser
Enter a username or account URL to browse all videos and photos from that profile. Supported platforms include Instagram, TikTok, YouTube, Twitter/X, Facebook, VKontakte, Reddit, Pinterest, Twitch, and Rumble. Select the desired files and download them with a single tap.

### 📋 Download History
All downloads are saved to a history log with status indicators. You can track completed, active, and failed downloads at a glance.

### 🎬 Quality Selection
Before each download, a dialog displays the available formats and resolutions (where the source supports HD). Choose the appropriate balance between quality and file size.

### 🍪 Cookie Profiles
To download content from private or authenticated accounts, you can import cookies from your browser. Multiple cookie profiles are supported for different sites.

### ⚡ Background Downloads
Downloads run in the background via a Foreground Service — you can minimize the app without interrupting any active transfers. A persistent notification displays real-time progress.

### 🌍 Multi-language Support
The interface is available in 8 languages: **Ukrainian, Russian, English, German, Spanish, French, Portuguese, and Chinese.**

### 🎨 Theme
Both light and dark themes are supported. Dark theme is enabled by default.

---

## 📂 Storage Locations

| Type | Directory on Device |
|-----|---------------------|
| 🎬 Video | `Movies/MediaWave` |
| 🎵 Audio | `Music/MediaWave` |

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Database | Room |
| Download Engine | youtubedl-android (yt-dlp) |
| Background Tasks | WorkManager + Foreground Service |
| Preferences | DataStore Preferences |
| Networking | OkHttp |
| Image Loading | Coil |
| Animations | Lottie |

---

## 📋 Requirements

- Android **10+** (API 29)
- Internet connection
- ~50 MB of free storage for the yt-dlp binary (downloaded automatically on first launch)

---

## 🚀 Build Instructions

1. Clone the repository
2. Open the project in **Android Studio Hedgehog** or later
3. Sync Gradle
4. Run on a physical device or emulator (API 29+)

```bash
git clone https://github.com/vadf34/MediaWave.git
cd MediaWave
gradle assembleDebug
```

---

## 📁 Project Structure

```
app/src/main/java/com/mediawave/downloader/
├── MainActivity.kt                  — app entry point
├── MediaWaveApp.kt                  — Application класс (Hilt)
├── data/
│   ├── db/                          — Room: database and DAOs
│   ├── model/                       — data models
│   └── repository/                  — repositories, DataStore
├── di/                              — Hilt modules
├── download/
│   ├── DownloadManager.kt           — download logic via yt-dlp
│   └── DownloadService.kt           — Foreground Service
├── ui/
│   ├── screens/
│   │   ├── home/                    — main download screen
│   │   ├── history/                 — download history
│   │   ├── profile/                 — profile parser
│   │   ├── settings/                — app settings
│   │   └── cookies/                 — cookie management
│   └── theme/                       — Compose theme 
└── util/
    └── LocaleHelper.kt              — locale/language switching
```

---

## ☕ Support the Project
If you enjoy using MediaWave and would like to support its development, you can do so here:


[![Donate](https://img.shields.io/badge/Donate-Donatello-5865F2?style=for-the-badge&logo=ko-fi&logoColor=white)](https://donatello.to/nexid)



[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/nexid)



![USDT](https://img.shields.io/badge/USDT%20(TRC20)-26A17B?style=for-the-badge&logo=tether&logoColor=white) `TQfyjiAByrvJDke6dGgvzC8NN8TKifkCMY`

Any support motivates faster bug fixes! ✨



