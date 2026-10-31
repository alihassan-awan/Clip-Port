# ClipPort 1.1 beta — sharing, Instagram, and Status imports

## New
- TikTok Lite and TikTok share text is parsed to the first **allowlisted HTTPS media URL** (extra app-promo links are ignored).
- Share a link to ClipPort: metadata analysis begins automatically. Download always requires a tap.
- Fresh quality selection defaults to 720p. Actual available formats depend on the platform.
- Public Instagram reels/videos use the existing yt-dlp downloader. Public single-photo posts have a **best-effort** HTML `og:image` fallback, with Instagram/Facebook image CDN allowlisting and a 50MB cap. Some posts are unavailable without login; multi-photo carousels aren't supported.
- New Statuses tab: pick a directory with Android's system folder picker, or import image/video files one at a time or as a selection using the system file picker. Saved to `Downloads/ClipPort/Statuses`.

## WhatsApp-specific limitations
Android 11+ scoped storage restricts access to other apps' internal media. `Android/data` and `Android/obb` cannot be selected with the standard picker. The visible WhatsApp `.Statuses` directory may also be inaccessible or hidden on some phones. **No attempt to bypass restrictions.** The user must have already viewed the status; no downloading unseen or expired statuses from WhatsApp servers.

## Privacy / security boundaries
- No WhatsApp permission, contact access, SMS, accessibility service, or `MANAGE_EXTERNAL_STORAGE` added.
- User-granted SAF directory permissions are stored locally and can be revoked with **Disconnect folder**.
- Saved media goes through Android MediaStore Downloads; failed writes are removed.
- Only allowlisted HTTPS social URLs are accepted for downloads.
- Instagram photo metadata may be requested from Instagram and image bytes from Instagram/Facebook CDNs. Social sites receive network metadata/IP as usual.
- Thumbnails load from external CDNs, and yt-dlp may check for engine updates. Thus **"no data ever leaves the device" is not a valid security claim**.
- The source is not independently audited. A clean package is not proof of malware-free behavior.

## Testing
1. Share TikTok Lite text containing `vm.tiktok.com/...` plus promotional `tiktok.com/tiktoklite`; ClipPort should show the short video URL and start analysis without auto-downloading.
2. Share a YouTube video and share a new link while ClipPort remains open; both should trigger analysis.
3. Check default 720p on a fresh launch.
4. Try public Instagram reels and photo posts; verify audio/video/photo output as appropriate.
5. Open **Statuses**, pick a readable folder or select media files with the Android file picker. Save and confirm `Downloads/ClipPort/Statuses`.
6. Verify YouTube playlists, Facebook, notification, FFmpeg merging, queue persistence, retry, and cancellation after upgrade.
7. Android 11, 13, 14, and 15 device testing; especially WhatsApp SAF folder accessibility.

## Release note
This package is a beta until GitHub Actions and device tests pass. Version code increment is necessary for upgrade installs. Release signing key consistency is required for production upgrades.
