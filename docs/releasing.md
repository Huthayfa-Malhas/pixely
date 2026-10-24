# Releasing

## One-time: the release key

    keytool -genkeypair -v -keystore pixely-release.jks -alias pixely \
        -keyalg RSA -keysize 4096 -validity 10000

Keep `pixely-release.jks` and its password outside the repo and back both up. Every update must be
signed with the same key, or Android refuses to install it over the old version. Then create
`app/keystore.properties` (ignored by git):

    storeFile=/absolute/path/to/pixely-release.jks
    storePassword=…
    keyAlias=pixely
    keyPassword=…

Without this file, release builds are signed with the debug key, which is fine for your own phone
but not for publishing.

## Each release

1. Bump `versionCode` (by 1) and `versionName` in `app/app/build.gradle.kts`.
2. Build and check it (from `app/`):

       ./gradlew :app:testDebugUnitTest :app:assembleRelease
       $ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

3. Install it on a phone over the previous release, check search still works with the existing
   index, and run `MainFlowTest` (see `app/README.md`).
4. Tag and publish (`vX.Y`, matching `versionName`):

       cp app/build/outputs/apk/release/app-release.apk pixely-X.Y.apk
       shasum -a 256 pixely-X.Y.apk > pixely-X.Y.apk.sha256
       git tag -a vX.Y -m "Pixely X.Y" && git push origin vX.Y
       gh release create vX.Y pixely-X.Y.apk pixely-X.Y.apk.sha256 --title "Pixely X.Y" --notes-file notes.md

   The notes should link [install-model.md](install-model.md) and say which model file the release
   expects (it changes only when `MODEL_ID` in `ModelFiles.kt` does).

## The model is not attached to releases

The APK and the release stay small (a few MB); the model comes from Hugging Face, pinned to a
revision and a SHA-256 in `install-model.md`. Putting the model inside the APK would make every
update a 485 MB download, and LiteRT-LM needs a plain file, so the app would have to copy it out of
the APK and keep two copies (about 1 GB). It would also differ from the Play build, which gets the
model from a Play AI pack.

## Google Play

Play signs the app with its own key (Play App Signing), so a Play install can't update a GitHub
install, or the other way round, without uninstalling first, which deletes the model and index.
Say so in the release notes once Pixely is on Play.
