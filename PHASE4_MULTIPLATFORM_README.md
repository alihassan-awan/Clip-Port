# ClipPort v0.4.0 beta: Multi-platform + refinement

## Added
- Public YouTube, Facebook, TikTok single-video URLs (allowlisted HTTPS hosts)
- YouTube playlist support kept unchanged
- Source pills on Discover, platform labels in Downloads
- Actual thumbnail metadata for Facebook/TikTok/YouTube, persisted for download history
- Refined palette, typography, onboarding copy
- No remote download server or required login

## Retained byte-for-byte
- `ClipPortApp.kt`: yt-dlp and FFmpeg initialization (the crucial working fix)
- `DownloadService.kt`: foreground downloading, notifications, cancellation, retry
- `UpdateChecker.kt`, `AndroidManifest.xml`, GitHub build workflow and existing icons

## Notes
- Public downloads only. Facebook/TikTok may require login, may change formats or deny requests. No guarantee of every link.
- TikTok and Facebook playlist/profile bulk downloading NOT implemented; YouTube playlists continue working.
- Quality availability varies by video. yt-dlp fallbacks may select a lower/different actual quality.
- Audio M4A attempts audio extraction on Facebook/TikTok using FFmpeg.
- This is a beta source ZIP, NOT a verified compiled APK. Test on device before release.
- Do not redistribute VidBee source; this implementation uses the ClipPort codebase and existing yt-dlp integration. VidBee README review: https://github.com/nexmoe/VidBee, MIT licensed, desktop/web/server app.

## Testing checklist
1. Build on GitHub Actions; install over existing ClipPort app.
2. YouTube video in 360p/720p with sound, then multi-video playlist.
3. Public Facebook video URL: analyze, thumbnail, MP4 audio, download entry.
4. Public TikTok video URL: analyze, thumbnail, MP4 audio, download entry.
5. Launch background download; lock phone; verify notification.
6. Cancel, retry, restart app and inspect queue history.
7. Confirm source-specific thumbnail persists after restarting app.
8. Test restricted links: show explicit failure, do not store media.

Use downloads only when you own media or have permission. GitHub Release updates require user approval to install.
