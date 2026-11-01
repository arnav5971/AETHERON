# AETHERON Browser

**Your space, your pace.**

AETHERON is an independently developed Android browser built with Kotlin, Jetpack Compose, Material 3 and Android System WebView.

## 0.4.0 implementation

- Reference-inspired light new-tab page with tab strip, browser toolbar, centered search, quick-access tiles and bottom navigation.
- AETHERON adaptive launcher icon (including round and monochrome variants) and Android 12+ SplashScreen with a legacy splash fallback.
- Web video fullscreen custom-view handling with immersive system-bar hiding and Back-to-exit behavior.
- Mobile/desktop request profile switching with responsive viewport support in mobile mode.
- URL navigation and Google search fallback.
- Back, forward, reload and home navigation.
- Persistent bookmarks and browsing history stored locally on-device.
- Quick access to Wikipedia, YouTube, GitHub and Reddit.
- Android DownloadManager handoff and Downloads shortcut.
- Desktop-site request using the installed WebView Chromium version, a desktop CSS viewport and phone-width initial fit with pinch zoom.
- Compact phone toolbar that preserves address-bar space on narrow displays.
- Safe Browsing response handling and strict TLS certificate-error cancellation.
- WebView file/content access disabled, mixed content blocked, and JavaScript interfaces not exposed to arbitrary sites.
- GitHub Actions unit-test and debug-APK build workflow.

## Not complete yet

This is an early browser build, not a Chrome replacement. Fullscreen support depends on each website using the standard WebView custom-view API; some embedded players may not expose a fullscreen action. The visible tab counter/new-tab affordance is a UI prototype; independent multi-tab sessions and tab switching are not implemented yet. Incognito isolation, full settings, find-in-page, media fullscreen/file chooser handling and a real AI assistant backend are also not implemented. The AI panel explains the backend requirement and does not send page content anywhere. A Ktor API source project is included with request-size limits, bearer-token gate, bounded per-process rate limiting, upstream timeouts and response-size caps; it is not deployed or connected to the Android app. Desktop mode asks sites for a desktop CSS viewport, but websites may still override their layout and Android System WebView is not desktop Chrome.

Features are only marked complete after implementation, automated checks and device testing support that claim.

## Security principles

- Never bypass TLS certificate errors.
- Do not expose JavaScript interfaces to arbitrary websites.
- Keep AI provider credentials on a backend, never in the APK or repository.
- Obtain explicit consent before sending page content to an AI service.
- Keep incognito data isolated before describing it as private.
- Verify behavior on physical Android devices; CI and emulator tests are not substitutes.

## Build

- Android Studio
- JDK 17
- Android SDK 35
- Gradle 8.11.1
- Android Gradle Plugin 8.9.2

Open the repository in Android Studio and allow Gradle sync. CI runs `gradle testDebugUnitTest` and `gradle assembleDebug`.

## Next engineering milestones

1. Independent multi-tab WebView sessions and lifecycle management.
2. Room-backed bookmarks/history and import/export.
3. Incognito profile with isolated cookies, cache, DOM storage and service workers.
4. Find-in-page, fullscreen media, file chooser and download management.
5. Accessible settings, permissions and privacy controls.
6. Deploy and connect the consent-based AI assistant through a secure authenticated service.
7. Automated UI tests and physical-device regression testing.

## License

No license has been granted. All rights reserved by default.
