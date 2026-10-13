# ClipPort Android — Phase 1 (source preview)

**Built by Ali Hassan Awan**

Native Android prototype using Kotlin + Jetpack Compose and the published `youtubedl-android` engine. No Vercel, Blogger or server required.

## Phase 1 included
- Responsive Material 3 phone UI
- Android share-menu text URL handling
- HTTPS YouTube URL validation
- Fetch title using yt-dlp wrapper
- Quality ceiling selection: Best, 1080p, 720p, 480p, 360p; audio M4A
- Download progress and copy completed file into **Downloads/ClipPort** via MediaStore
- Clear status and error strings

## Build APK without paid hosting
1. Create a **new GitHub repository** and upload the *contents* of this project folder (including `.github`).
2. In the GitHub repository select **Actions > Build ClipPort Debug APK > Run workflow**.
3. On successful run, open the workflow and download **ClipPort-Phase1-debug-apk** artifact.
4. Extract artifact ZIP and install `app-debug.apk` on Android (enable install from this source).
5. Alternatively, open root folder in Android Studio with JDK 17 and compile `assembleDebug`.

## Important caveats
- **This source has not been compiled or installed/tested in this environment.** GitHub Actions is provided to verify Gradle dependency compatibility. If compilation fails, share the workflow log for adjustment.
- yt-dlp may fail on specific videos, geographic/YouTube restrictions, or when its extractor needs updates.
- Quality labels are ceilings, not a verified per-video format list yet. This is a Phase 1 limitation.
- Long downloads are **not background-safe yet**: keep the app in foreground. Background service, cancellation, playlists, exact formats, and queue will come in later phases.
- Requires Android 10 or later (API 29+), to use scoped-storage MediaStore saving without legacy storage permissions.
- Google Play policies, licensing and YouTube terms must be considered before publishing.

## Roadmap
- Phase 2: playlist metadata, multi-item selection, foreground service/notifications, queue, cancel/retry.
- Phase 3: thumbnails, true available-format picker, download history, theme customization and visual polish.
- Phase 4: release signing, app icon, QA matrix, GitHub Releases.

## Libraries and licenses
This source is original app code using `io.github.junkfood02.youtubedl-android` (GPL-3.0) and yt-dlp and FFmpeg. If distributing this combined work, review applicable GPL obligations, provide corresponding source and required notices. Do not use Seal branding.
