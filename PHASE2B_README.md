# ClipPort Phase 2b beta

Built on the existing Phase 2a code. FFmpeg initialization in ClipPortApp.kt was restored from the verified working GitHub version (`com.yausername.ffmpeg.FFmpeg`). yt-dlp update, playlist parsing, MediaStore Downloads, output validation and diagnostics remain intact.

## Added
- User-initiated Android foreground service (dataSync)
- Notification progress (permission prompt on Android 13+)
- Serial queue while app in background
- Cancel queued/active jobs using youtubedl-android process IDs
- Retry failed/cancelled items
- Activity can be reopened to see in-memory queue progress

## Known limitations
- Queue is in-memory; force-stop, process death, reboot or OS service restrictions can end the queue. Durable persistence is scheduled for a later release.
- Android 15 enforces foreground dataSync time quotas; very long lists may be interrupted.
- A debug APK compile and real phone tests have NOT been performed here.
- Long output detection and audio tracks depend on yt-dlp/FFmpeg and still need device testing.
- Queue is bounded to one active media download; do not start two foreground services concurrently.

## Test
1. Build APK in Actions.
2. Download 720p video and confirm it contains audio.
3. Queue two playlist videos; press Home (not Force Stop); observe notification and resulting Downloads/ClipPort.
4. Try cancelling second queued item, and active item.
5. Retry an intentionally failed item.
6. Review background restrictions and notification permission on your phone.
