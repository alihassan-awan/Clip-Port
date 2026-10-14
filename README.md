<div align="center">

# ClipPort

**Save your favorite media, directly to your Android device.**

A modern, native Android video downloader with support for public **YouTube, Facebook, and TikTok** videos, YouTube playlists, background downloads, and a persistent download queue.

**Built by [Ali Hassan Awan](https://github.com/alihassan-awan)**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-1.0.0-9D8CFF)](https://github.com/alihassan-awan/Clip-Port/releases)
[![GitHub Actions](https://github.com/alihassan-awan/Clip-Port/actions/workflows/build-apk.yml/badge.svg)](https://github.com/alihassan-awan/Clip-Port/actions/workflows/build-apk.yml)

[**Releases**](https://github.com/alihassan-awan/Clip-Port/releases) · [**Builds**](https://github.com/alihassan-awan/Clip-Port/actions) · [**Report an issue**](https://github.com/alihassan-awan/Clip-Port/issues)

</div>

---

## ✨ Overview

ClipPort brings media discovery, downloads, and queue management into one Android app. Paste a supported link, inspect the video thumbnail, select a quality, and download it directly to your phone. No ClipPort account or remote conversion server is required.

The interface uses a premium dark theme with a dedicated **Discover** workspace and **Downloads** center, so playlist selection and active download progress stay organized.

> **Supported platforms:** YouTube, Facebook and TikTok. Platform support is subject to public video availability and upstream extractor compatibility. ClipPort does not bypass DRM, private access restrictions or authentication requirements.

## 🚀 Features

| Category | What ClipPort offers |
| --- | --- |
| **Video sources** | Public YouTube, Facebook and TikTok video URLs |
| **YouTube playlists** | Preview and select videos from a playlist (up to the first **50** entries) |
| **Quality choices** | Best, 1080p, 720p, 480p, 360p and audio-only M4A (subject to source availability) |
| **Thumbnails** | Previews for videos, playlist items and download history where available |
| **Audio + video** | FFmpeg-based stream merging for supported media |
| **Background downloads** | Android foreground service with progress notifications |
| **Download manager** | Persistent queue metadata, progress, cancel and retry |
| **Recovery** | Jobs interrupted by process termination can be retried manually |
| **Network failures** | Clearer HTTP 403 errors; one bounded retry for applicable failures |
| **App updates** | GitHub Release update check and an external release link for user-approved installation |
| **Local storage** | Completed files saved under **Downloads/ClipPort/** |
| **Design** | Kotlin/Jetpack Compose UI, dark surfaces, accented controls and source badges |

### 📱 Interface

- **Discover** — paste a link, analyze media, view thumbnails, select a quality and choose playlist entries.
- **Downloads** — view active, completed and failed tasks; inspect progress, cancel pending jobs and retry failed items.
- **Background notifications** — monitor the current download without keeping the app screen open.

<!-- Add actual screenshots after capturing them from your tested APK.
Example:
## Screenshots
| Discover | Playlist | Downloads |
| --- | --- | --- |
| ![Discover](screenshots/discover.png) | ![Playlist](screenshots/playlist.png) | ![Downloads](screenshots/downloads.png) |
-->

## 📥 Installation

### Option A — APK from GitHub Releases

1. Open the [Releases page](https://github.com/alihassan-awan/Clip-Port/releases).
2. If a release APK is published, download the APK appropriate for your device.
3. Open the APK on your Android device and approve installation when Android prompts you.
4. Open **ClipPort**, paste a supported public video URL, and tap **Analyze link**.
5. Select the quality, then tap **Start download** (or select playlist entries first).

**Note:** A GitHub Actions debug artifact is a test build, not a production-signed release. If the Releases page has no APK yet, follow the build instructions below.

### Option B — Build with GitHub Actions

1. Fork this repository or use its existing workflow in your own checkout.
2. Open **Actions → Build ClipPort Debug APK**.
3. Choose **Run workflow**, or push a commit to `main` to trigger a build.
4. After a successful run, download the APK artifact and extract it.
5. Install the debug APK on a compatible Android device.

### Build locally

**Requirements:** Android SDK (API 35), JDK 17, Gradle 8.9 and internet access for dependencies.

```bash
gradle assembleDebug --no-daemon
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

**Compatibility:** Android 10 (API 29) or later. The project includes Android ABI filters for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

## 🧭 How to use

1. **Paste a link:** Enter a public YouTube, Facebook or TikTok video URL in **Discover**.
2. **Analyze:** ClipPort reads available video metadata and displays a preview where available.
3. **Choose a format:** Select your preferred resolution or M4A audio-only mode; available formats vary by source.
4. **For YouTube playlists:** Choose **All**, **None** or individual videos before downloading.
5. **Download:** Tasks move into **Downloads** where you can track progress and see results.
6. **Find your file:** Open your phone's **Downloads/ClipPort/** folder.

## 🏗️ Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Video extraction:** [yt-dlp](https://github.com/yt-dlp/yt-dlp), via [youtubedl-android](https://github.com/yausername/youtubedl-android)
- **Media processing:** [FFmpeg](https://ffmpeg.org/)
- **Image loading:** [Coil](https://coil-kt.github.io/coil/)
- **Background jobs:** Android foreground service + Kotlin coroutines
- **Queue metadata:** Android SharedPreferences
- **File saving:** Android MediaStore
- **CI:** GitHub Actions

## 📂 Project structure

```text
app/
  src/main/
    java/com/alihassanawan/clipport/
      MainActivity.kt      # Discover and Downloads UI
      Downloader.kt        # Extraction, formats and file export
      DownloadService.kt   # Background downloads and notifications
      DownloadQueue.kt     # Queue state and persistence
      ClipPortApp.kt       # yt-dlp/FFmpeg initialization
      UpdateChecker.kt     # GitHub Release update check
    res/                   # App launcher and notification icons
    AndroidManifest.xml
  build.gradle.kts
.github/workflows/build-apk.yml
```

## 🔒 Privacy and security

- Downloads run **on your device**; ClipPort does not require an account or upload media to a ClipPort backend.
- The app checks supported **HTTPS** URL hosts before passing URLs to the extractor.
- Android's app-private temporary directory and MediaStore are used for download processing and final file saving.
- The Android foreground service is not exported to other apps.
- Remote app update information comes from this repository's GitHub Releases; installation is handled by Android with user approval.
- You should download APKs only from a trusted release and verify their source. **A debug build is not a security-certified production release.**
- Extractors and downloaded media originate from external services. Keep dependencies updated and use caution with unfamiliar links.

**No app can guarantee every link will work.** Certain videos may fail with HTTP 403 or require authentication, or may not provide every requested quality. A retry does not guarantee access.

## 🔄 Updates and release builds

ClipPort can **check** the latest published GitHub Release and open its release page. It does **not** silently install APKs; Android requires user-approved installation for this distribution method.

For an official production release:

1. Create and securely back up a permanent Android signing keystore (**do not commit it**).
2. Build and sign a release APK with that same signing identity for all subsequent updates.
3. Test the signed APK, including upgrades, on real devices.
4. Publish the APK and its SHA-256 checksum in a GitHub Release, using a matching version tag such as `v1.0.0`.
5. Keep dependencies, yt-dlp compatibility and Android API behavior under review.

> The source project currently declares **v1.0.0**. That version label alone does not mean a production-signed APK has been published or independently security-audited.

## ⚠️ Current limitations

- Facebook and TikTok support targets **public single-video URLs**; the current playlist selector is for YouTube.
- YouTube playlist previews are limited to the first **50** entries.
- Not every video provides every listed resolution or M4A audio format.
- Pending queue metadata survives app restarts, but interrupted transfers **restart from the beginning** when retried; partial-file resume is not implemented.
- Downloads do not automatically restart after a device reboot.
- Some HTTP 403/network restrictions are outside ClipPort's control.
- Automatic, silent APK installation is **not supported**.

## 🛠️ Troubleshooting

| Symptom | What to check |
| --- | --- |
| **HTTP 403 Forbidden** | Video access and upstream yt-dlp compatibility; try again later or choose another public video. |
| **No audio** | Confirm FFmpeg initialization and inspect the diagnostic log; video/audio may require merging. |
| **Download stops in background** | Android battery/background restrictions and foreground-service notification availability. |
| **No thumbnail** | The source may not expose a thumbnail, or the image request may fail. |
| **Update not shown** | Verify that a newer, correctly versioned **GitHub Release** exists; a CI artifact alone is not a release. |
| **Install fails over old APK** | Debug and production-signed APKs can have different signing keys; uninstalling may remove app data, so proceed carefully. |

When reporting a bug, open a [GitHub issue](https://github.com/alihassan-awan/Clip-Port/issues) with the Android version, selected source/format, steps to reproduce, and relevant **redacted** logs. Remove private URLs, cookies, tokens and other sensitive data before sharing.

## 🤝 Contributing

Feedback, bug reports and feature suggestions are welcome through [GitHub Issues](https://github.com/alihassan-awan/Clip-Port/issues). Please discuss large changes before implementing them to avoid regressions in the tested downloader core.

## 📜 Legal notice

ClipPort is a general-purpose download utility. Use it only to save content you own, have permission to download, or may otherwise lawfully access and download. Respect copyright, the source platform's applicable terms and the rights of content owners. ClipPort is not affiliated with YouTube, Facebook, TikTok, Google, Meta or ByteDance.

## 👨‍💻 Developer

**Ali Hassan Awan**  
GitHub: [@alihassan-awan](https://github.com/alihassan-awan)

---

<div align="center">
  <strong>ClipPort</strong><br />
  Made for a simple, local and organized Android download experience.
</div>
