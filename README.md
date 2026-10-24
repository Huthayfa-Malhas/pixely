# Pixely

Search your photos by describing them. Everything runs on your phone.

Type "dog on a beach" or "receipt from the pharmacy" and Pixely finds the photos. Or pick a photo and
find similar ones. Pixely uses Google's EmbeddingGemma 2 AI model on the phone itself and has **no
internet permission**: your photos never leave the phone.

**Android only** (Android 10 or newer, 64-bit). iOS is coming soon.

## Features

- Search by words, by photo, or by photo plus words ("this, but at night").
- Indexes in the background (optionally only while charging) and keeps up with new photos.
- Leave folders out of search.
- Diagnostics you can share if something goes wrong.

## Install

You need two files: the app and the AI model. The model (485 MB) is separate because the app can't
download anything.

1. **Install the app.** Download `pixely-0.1.apk` (78 MB) from [Releases](https://github.com/Huthayfa-Malhas/pixely/releases) on your phone and
   open it, or from a computer: `adb install pixely-0.1.apk`
2. **Install the model.** Follow [docs/install-model.md](docs/install-model.md) (needs a computer
   and a USB cable, about 5 minutes).
3. **Open Pixely** and allow photo access. Indexing starts; you can search while it runs.

## Coming soon

- **iOS app.**
- **Full phone search:** find files and documents, not only photos.
- **Install the model inside the app**, without a computer.
- **Google Play** release.

## Build from source

Needs JDK 21 and the Android SDK (platform 37). From `app/`:

    ./gradlew :app:assembleRelease

The APK is in `app/build/outputs/apk/release/`. Developer notes: [app/README.md](app/README.md).
Publishing a release: [docs/releasing.md](docs/releasing.md).

## Licence

Apache 2.0, see [LICENSE](LICENSE). Third-party parts (sqlite-vec, fonts, an icon, the model) are
listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
