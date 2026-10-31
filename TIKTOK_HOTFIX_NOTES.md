# ClipPort v1.1.1 — TikTok Share Text Hotfix

This package starts from the v1.1 complete Android source package (not from an older phase). It changes only `MainActivity.kt`, Gradle version metadata, and this document. `Downloader.kt`, `DownloadService.kt`, `ClipPortApp.kt`, queue persistence, Instagram and WhatsApp Status components are unchanged.

## What changed
- TikTok Lite promotional `https://www.tiktok.com/tiktoklite` links are rejected as non-media links.
- Real video URLs (`vm.tiktok.com/...`, `vt.tiktok.com/...`, and `www.tiktok.com/@user/video/...`) are extracted from share captions.
- Multi-URL pasted text is normalized to the actual video URL.
- Shared valid links still auto-analyze **without auto-downloading**.
- Prominent messages distinguish invalid promotional links, 403 restrictions, browser-impersonation requirements, and TikTok IP blocking.
- Default 720p retained.

## Important limitation
`ERROR: [TikTok] ... Your IP address is blocked from accessing this post` is a server-side block. This patch **does not bypass it**; trying another public video or network is diagnostic, not a guarantee. `WARNING: ... impersonation ... no impersonate target` indicates an unavailable optional browser impersonation dependency on Android. The app is not guaranteed to retrieve all TikTok media.

## Testing
1. Keep your previous working APK and repository backup.
2. Upload the *contents* of this project ZIP into your GitHub repository root; do not nest an extra project directory.
3. Build GitHub Actions debug APK and install it.
4. Share the example text containing both `vm.tiktok.com/ZSgN51eEn/` and `/tiktoklite`: only the first should appear.
5. Confirm auto-analysis, no automatic download, 720p default, and other platforms still work.
6. Share text with only `/tiktoklite`: an invalid-link message should appear rather than analyzing it.

Tested: extracted parser logic with Kotlin CLI. **Full Android APK build and device testing not yet performed**.
