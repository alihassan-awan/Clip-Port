# ClipPort Phase 2c — HTTP 403 & Launcher Icon Patch

Based on the previous Phase 2c project, preserving ClipPortApp.kt FFmpeg initialization, merged-file validation, persistent queue, service, notifications, and storage implementation.

- One bounded automatic retry for 403 Forbidden (only) with 1.5s delay, then clear error and proceed to next queued video. This does not bypass YouTube access restrictions or guarantee a download.
- Existing manual Retry button is retained.
- Native Android launcher icon in `mipmap-*` directories and notification small icon in drawable.
- Version bumped to `0.2.2-beta`.

## Testing
1. Upload contents of this folder to repository ROOT, including new `app/src/main/res` folder.
2. Confirm Actions successful, install latest APK.
3. Test one successful video with audio and 360p/720p; confirm launcher/notification icon.
4. Test a playlist that includes one video that returns 403 and confirm it marks Failed and continues to subsequent videos.
5. Restart the app and verify queue persistence and manual retry.

Source ZIP code has not been compiled in an Android SDK or tested on device.
