# Pixely

Search your Android photos by describing them, entirely on your phone.

Type "dog on a beach" or "receipt from the pharmacy" and Pixely finds the photos, or pick a photo
and find others like it. It uses Google's
[EmbeddingGemma 2](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm) model,
which runs on the phone's GPU. Nothing is uploaded: **the app has no internet permission at all**,
which you can check in Android's app info or in
[`AndroidManifest.xml`](app/app/src/main/AndroidManifest.xml).

- Search by words, by photo, or by photo plus words ("this, but at night").
- Indexes in the background, while charging if you want, and keeps up with new photos.
- Leave folders out, choose the search size, and see what indexing did in Diagnostics.

Status: early (v0.1), tested on one phone so far (POCO X7 Pro, Android 16).

## Install

Pixely needs two things: the app, and the AI model file (485 MB). The model isn't inside the app,
and the app can't download it, because it has no internet access.

1. **The app:** download `pixely-<version>.apk` from [Releases](../../releases) and open it on your
   phone (Android 10 or newer, 64-bit). Android asks you to allow installs from your browser or
   file manager.
2. **The model:** follow [docs/install-model.md](docs/install-model.md). For now this takes a
   computer with `adb`.
3. Open Pixely, allow photo access, and indexing starts. A big library takes hours the first time;
   you can search while it runs.

## Build from source

You need JDK 21 and the Android SDK (platform 37, build tools). From `app/`:

    ./gradlew :app:assembleRelease
    adb install app/build/outputs/apk/release/app-release.apk

Then install the model as above. Debug builds only look at `Pictures/PixelyTest/` on the phone, so
testing never shows your own library; see [app/README.md](app/README.md) for the layout, tests and
benchmarks. Release builds are signed with your debug key unless you add a `keystore.properties`
(see [docs/releasing.md](docs/releasing.md)).

## Licence

Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Pixely includes sqlite-vec (MIT),
the Figtree and Bricolage Grotesque fonts (SIL OFL 1.1) and a Lucide icon (ISC); see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The EmbeddingGemma 2 model is Google's, under
Apache 2.0, and is downloaded separately.
