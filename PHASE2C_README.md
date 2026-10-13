# ClipPort Phase 2c — Persistent queue metadata

Built on the device-tested Phase 2b version. Existing FFmpeg initialization, yt-dlp engine, format selection, playlist support and foreground service were retained.

## New behavior
- Jobs and statuses are stored in Android SharedPreferences.
- After Android kills the process or phone reboots, the queue list survives.
- Previously `Downloading` jobs are marked `Interrupted` on next launch. Tap **Retry** to start them again from the beginning.
- Previously `Queued` jobs remain queued; tap **Resume queue**.
- **Clear finished** removes completed/failed/cancelled history (not downloaded media files).
- Progress snapshots are persisted at 5% increments rather than on every callback.

## What this is NOT
- This version does *not* persist partial downloaded media or support mid-file resume across reboot.
- This version does *not* autostart download processing after reboot; Android foreground-service/background-start restrictions require an explicit user action.
- No Android build / device test executed in this container. Run GitHub Actions and test on your phone.

## Device test plan
1. Test normal 720p video with sound and a 2-item playlist; verify notifications and background transfer.
2. Start a 2-item playlist. Swipe away the UI, reopen app; verify jobs persist.
3. Force-stop ClipPort in Android Settings, relaunch; verify `Interrupted` / `Queued` states and manual Retry/Resume.
4. Reboot phone, relaunch app; verify queue records remain and that no job auto-downloads.
5. Tap Clear finished; ensure saved videos remain in `Downloads/ClipPort`.

## GitHub
Upload the entire project contents to repo root. Preserve `.github/workflows/build-apk.yml`. Actions should produce the debug APK.
