# ClipPort Android v0.2.0-alpha — Phase 2a

Built by Ali Hassan Awan.

## Changes from Phase 1

- Playlist links (`list=`) automatically recognized.
- Show up to 50 video entries; select all/none or individual videos.
- Download selected entries one by one, while the app is open.
- Format selector revised to prefer a separate video plus audio track, then fall back to combined A/V streams (when available).
- yt-dlp now supplies the final processed output path (`--print after_move:filepath`), so the app no longer blindly saves the largest temporary media stream.
- Saves to Android `MediaStore.Downloads` in `Downloads/ClipPort`.
- Existing developer branding retained.

## Build / install

1. Upload project **contents**, not the containing folder, to GitHub repository root. Keep `.github/workflows/build-apk.yml`.
2. GitHub Actions > Build ClipPort Debug APK > latest successful run > Artifacts > `ClipPort-Phase2a-Debug-APK`.
3. Extract `app-debug.apk` and install it on an Android 10+ phone.

## Important limits

- Source project generated and structurally inspected; **APK not compiled or tested here**. Check CI logs and device tests.
- A video-only result or a download failure may still occur due to yt-dlp, FFmpeg runtime or YouTube access restrictions. Do not treat the new selector as a guarantee of an audio track.
- Phase 2a queue **is not a foreground/background Android service**. Keep app open. Download cancellation, persistent jobs, retries, notification controls and dynamic per-video format discovery are Phase 2b work.
- Playback compatibility may vary when a selected video/audio pair uses different codecs.
- Playlist extraction limits first 50 entries; unavailable/private videos may be skipped or fail individually.
- Download only media you own or are authorized to save.


## Phase 2a hotfix — media validation
- No longer requires `--print after_move:filepath` to be present in wrapper output.
- Selects an existing completed media file and checks for audio/video tracks using Android MediaMetadataRetriever.
- Refuses to label a silent video as a successful download.
- If no merged file exists, see yt-dlp/FFmpeg device logs: this patch cannot manufacture a missing audio track.
- APK compilation and device playback remain to be tested.
