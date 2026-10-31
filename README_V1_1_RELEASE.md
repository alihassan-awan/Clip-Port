# ClipPort v1.1 Beta — Complete Android project

This package contains a full Android project, not a patch kit. Upload the **contents of this folder** to the repository root (including `app/`, `.github/`, Gradle configuration), then run GitHub Actions.

## Added features
- Extracts the first approved HTTPS URL from TikTok Lite share captions, ignoring advertising URLs.
- Automatically analyzes links received via Android `ACTION_SEND`; does **not** automatically download.
- Default quality is 720p.
- Adds public Instagram posts/reels support via yt-dlp, with best-effort single-image metadata fallback.
- Adds an explicit-permission WhatsApp Status folder/file importer, saving to `Downloads/ClipPort/Statuses`.

## Retained download components
`ClipPortApp.kt`, `DownloadService.kt`, and `DownloadQueue.kt` remain unchanged from the packaged working multi-platform base. The UI and downloader are amended for v1.1.

## Before installing
- This complete package derives from the locally packaged ClipPort multi-platform UI base and **may not exactly match the current GitHub v1 UI revision**. Compare `MainActivity.kt` before replacing a later UI.
- Compilation and device testing have not been performed here; do not declare this a stable or security-certified release.
- WhatsApp's `.Statuses` folder may not be exposed on Android 11+, depending on OS and WhatsApp version. Use user-selected files when inaccessible.
- Public Instagram single-photo detection is best effort; carousels, login walls, and restricted media are not guaranteed.
- Keep a copy of the last tested APK and source. Release APKs need consistent signing.
