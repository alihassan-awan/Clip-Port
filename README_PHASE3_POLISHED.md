# ClipPort Phase 3 Polished (0.3.1-beta)

## UX
- Two tabs: Discover and Downloads.
- Choose playlist videos in Discover. Tap Download selected → navigates directly to Downloads activity with stats and each job, instead of duplicating the full playlist below.
- Discover preserves selection for returning to it. To analyze a new URL, enter a new URL and tap Analyze.
- Developer credit remains in header. Thumbnail and GitHub Release check remain.

## Working engine retained
Downloader, background service, queue and FFmpeg initialization retained from Phase 3; the only Downloader change suppresses verbose successful output logs for privacy. GitHub links are validated on exact host.

## Install
Copy full files from this ZIP to GitHub repository root, commit and run Actions. Keep the last working APK until the new APK is verified.

## Status
Source package created and static checks performed; NOT compiled or tested on an Android phone. Not production-stable yet.
