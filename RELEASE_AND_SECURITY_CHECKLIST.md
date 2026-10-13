# ClipPort 0.3.1 Beta — Security and stable release checklist

## What was changed
- Single-page Discover / Downloads navigation; no duplicated playlist and download queue together.
- Keeps tested FFmpeg init, yt-dlp, queue, MediaStore saves, foreground service and cancel/retry behavior.
- Restricts GitHub Release URLs to the exact https://github.com domain and release path.
- Avoids writing full successful yt-dlp stdout (which may contain private URLs/tracking parameters) to diagnostics.

## Security review (source inspection, NOT an independent penetration test)
- Existing manifest requests INTERNET, foreground dataSync service and notification permission only.
- `DownloadService` is not exported. Application backup disabled; cleartext network disabled.
- yt-dlp and FFmpeg are bundled native components: monitor library advisories and update dependencies.
- Thumbnail requests go to YouTube image CDN; user IP is exposed to the image provider.
- yt-dlp update downloads executable software at runtime: this introduces supply-chain risk; pin/verify update provenance and consider disabling auto-update for a production release.
- Do NOT publish user-uploaded diagnostic logs publicly: logs may contain source URLs.
- Do not claim audit coverage for penetration testing, dependency CVEs or dynamically updated yt-dlp executables.

## Stable release requirements
1. Build and test on physical devices: Android 10, Android 13, Android 15 where possible.
2. Validate playlist selection, single video, audio/video merging, notifications, background and force-stop recovery, 403 and cancel/retry.
3. Create a dedicated Android signing keystore OFFLINE. Preserve keystore and password securely. Never commit them.
4. Build a release APK/AAB with signingConfig using secure CI secrets, not the debug signing key. If users already installed debug builds, a production-signature install generally requires uninstalling the old build (back up desired app data first).
5. Publish only trusted, same-package, same-signature signed release builds as GitHub Releases with a matching SemVer tag. Provide SHA-256 checksum and changelog.
6. UpdateChecker detects new GitHub Release and opens release page; Android installation always needs user approval. Silent self-install isn't supported.
7. Do not call this stable until successful CI release build plus mobile acceptance tests. This package is deliberately beta.
