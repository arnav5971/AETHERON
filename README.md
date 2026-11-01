# AETHERON Browser

**The next era of browsing.**

AETHERON is an independently developed Android browser built with Kotlin, Jetpack Compose, and Android System WebView. The project is mobile-first and prioritizes a polished interface, explicit security boundaries, and testable behavior.

## Current implementation

- Native Compose browser shell with address/search input and navigation controls.
- Android System WebView page rendering.
- Google search fallback for search phrases and HTTPS normalization for bare domains.
- Mobile/Desktop request mode toggle using a desktop User-Agent and wide-viewport settings.
- Download handoff to Android DownloadManager.
- Safe Browsing response handling and strict TLS certificate-error cancellation.
- WebView file/content access disabled and mixed content blocked.
- URL resolver unit tests.
- GitHub Actions workflow for unit tests and debug APK assembly.

## Project status

Early development foundation. The current interface is a single-WebView prototype, not yet a complete multi-tab browser. Mobile/Desktop mode requests are best-effort: some websites may ignore the User-Agent or viewport hints, and Android System WebView does not expose every feature of desktop Chrome.

Features are not considered complete until implementation, automated checks, and device testing support that claim.

## Engineering principles

- Never bypass TLS certificate errors.
- Do not expose JavaScript interfaces to arbitrary websites.
- Keep AI provider credentials on a backend, never in the APK or repository.
- Obtain explicit user consent before sending page content to an AI service.
- Keep incognito data isolated before describing it as private.
- Verify behavior on physical Android devices; CI and emulator tests are not substitutes.

## Build

- Android Studio
- JDK 17
- Android SDK 35
- Gradle 8.11.1
- Android Gradle Plugin 8.9.2

Open the repository in Android Studio and allow Gradle sync. CI builds with `gradle testDebugUnitTest` and `gradle assembleDebug`.

## Planned next milestones

1. Persistent tabs and tab switching.
2. Bookmarks and browsing history with Room.
3. Settings and download history.
4. Fullscreen media and file chooser flows with explicit permission handling.
5. Privacy controls and isolated incognito profile.
6. Consent-based AI assistant backed by a secure service.
7. Accessibility, performance, and physical-device regression testing.

## License

No license has been granted. All rights reserved by default.
