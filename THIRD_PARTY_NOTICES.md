# Third-party notices

## Windows GUI

- QRCoder 1.7.0 — Raffael Herrmann, Shane Krueger and contributors; MIT; <https://github.com/Shane32/QRCoder>. The complete notice/license is in `LICENSES/QRCoder-MIT.txt` and supplied in the Windows release ZIP. QRCoder is used unmodified.
- The self-contained Microsoft .NET / ASP.NET Core / Windows Desktop runtime is supplied under its upstream licenses and notices. The release ZIP includes the upstream .NET `LICENSE.txt` and `ThirdPartyNotices.txt`.

## Android QR scanning

- ZXing Android Embedded 4.3.0 — ZXing authors, Journey Mobile / JourneyApps; Apache-2.0; <https://github.com/journeyapps/zxing-android-embedded>.
- ZXing Core 3.4.1 — ZXing authors; Apache-2.0; <https://github.com/zxing/zxing>. Both libraries are used unmodified; QR decoding is local and does not require Google Play services.

The Android APK's runtime dependency graph contains these third-party components, each distributed under Apache License 2.0:

- OkHttp 5.3.2 (`com.squareup.okhttp3:okhttp`) — Square, Inc. and contributors; <https://github.com/square/okhttp>.
- Okio 3.16.4 (`com.squareup.okio:okio-jvm`) — Square, Inc. and contributors; <https://github.com/square/okio>.
- AndroidX Annotation 1.9.1, Startup Runtime 1.2.0, and Tracing 1.0.0 — The Android Open Source Project; <https://developer.android.com/jetpack/androidx>.
- Kotlin Standard Library 2.2.21 — Kotlin Team; <https://kotlinlang.org/>.
- JetBrains Annotations 13.0 — JetBrains; <https://github.com/JetBrains/intellij-community>.

The complete Apache-2.0 license text is in [`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt) and is bundled into the APK as `assets/Apache-2.0.txt`.

The Android APK packages the same license text at `assets/Apache-2.0.txt`. OkHttp is used unmodified. The project’s own source remains under the MIT License in [`LICENSE`](LICENSE); this notice does not change either license.
