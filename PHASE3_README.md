# ClipPort Phase 3 (0.3.0-beta)

## Changed
- Material 3 premium dark UI, developer credit in top header.
- Thumbnails for YouTube videos, playlist items and download queue; internet-based and may be blocked.
- Non-disruptive update check on opening app and by tapping the update icon. Uses GitHub latest published release; no published release means no update.
- A release link opens browser for user-reviewed download/install. Android disallows silent arbitrary APK updates. To publish updates, tag a release (e.g., v0.3.1) and attach a compatible signed APK (same signing key!). Current Actions workflow builds **debug APKs**: upgrading an install requires matching signatures. For production distribution configure stable release signing before distributing.
- Existing Downloader, DownloadService, DownloadQueue, ClipPortApp, FFmpeg & yt-dlp integrations left unchanged.

## Test checklist
1. GitHub Actions debug build succeeds, install over prior debug APK.
2. Analyze single YouTube URL, see thumbnail; test playlist thumbnails.
3. Check quality 360p/480p/720p audio and multi-video download.
4. Run background download, notification, cancel/retry, force-close queue recovery.
5. Tap update icon: if no Releases, says no newer Release. Publish a newer Release only with correctly signed APK.
