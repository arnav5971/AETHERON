# AETHERON Browser

**The next era of browsing.**

AETHERON is an original Android browser project built with Kotlin and Android System WebView. It is being developed as a mobile-first browser with a premium interface, clear security boundaries, and testable architecture.

## Project status

Early foundation. Features are added incrementally and are not considered complete until implemented and tested.

## Planned foundation

- Kotlin + Android Gradle Plugin
- Android System WebView as the rendering engine
- Mobile-first browser shell
- URL and search query handling
- Safe WebView configuration and navigation policy
- Incremental tests and CI

## Engineering principles

- Never bypass TLS certificate errors.
- Do not expose JavaScript interfaces to arbitrary websites.
- Keep secrets and AI provider credentials out of the Android application.
- Obtain user consent before sending page content to an AI service.
- Verify behavior on physical Android devices; emulator and CI tests are not substitutes.

## Build

Open this project in a current stable Android Studio release with JDK 17. The Gradle wrapper will be added with the build foundation.

## License

No license has been granted. All rights reserved by default.
