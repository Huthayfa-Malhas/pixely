# Pixely

Developer notes for the Android app. For what Pixely is and how to install it, see the
[main README](../README.md).

## Build

    export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
    export ANDROID_HOME=$(brew --prefix)/share/android-commandlinetools
    ./gradlew :app:assembleDebug :app:testDebugUnitTest
    adb install -r app/build/outputs/apk/debug/app-debug.apk

The model file goes in the app's external files dir; see [docs/install-model.md](../docs/install-model.md).

## Layout

- `data/db` — Room 3 entities (`photo`, `photo_embedding`, `index_meta`), DAOs, and the database
  (bundled driver; `photo_search` vec0 table and clean-up triggers are created in its callback).
- `data/search` — `VectorIndex`, `SqliteVecIndex` (search size + re-check at 768), `KotlinScanIndex` fallback.
- `data/media` — `PhotoAccess` (full / partial / none), `MediaStoreSource`, `PhotoSync` (full diff),
  `LibraryWatcher` (sync on foreground and on MediaStore changes, debounced).
- `ui` — Navigation 3 screens (welcome, photos, viewer, search, settings, folders, diagnostics,
  licences), theme and icons.

`app/src/main/jniLibs/*/libsqlite_vec.so` is sqlite-vec v0.1.9, pinned.

## Test photos

Debug and benchmark builds read only `Pictures/PixelyTest/` so testing never shows the phone's own
library (`-Ppixely.testFolder=` for everything in debug; release builds always see everything).

## On-phone UI test

Some phones (Xiaomi's among them) block Gradle's test installer and adb input, so install both APKs
by hand (approve each on the phone) and run the test with `am instrument`:

    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w -e class app.pixely.MainFlowTest app.pixely.test/androidx.test.runner.AndroidJUnitRunner
    adb pull /sdcard/Android/data/app.pixely/files/test-shots

`MainFlowTest` walks every main screen and fails on anything tappable without a TalkBack label or
smaller than 48 dp.

## Baseline profile and benchmarks

`:benchmark` measures cold start, grid scrolling and a search against the app's `benchmark` build
(release code, profileable, debug key, test folder only), and generates `app/src/main/baseline-prof.txt`.
Never run it through Gradle's `connected…` tasks: they uninstall the app afterwards, which deletes
the index and the model file. With the phone unlocked:

    ./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark
    adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
    adb install -r benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
    # New profile:
    adb shell am instrument -w -e class app.pixely.benchmark.BaselineProfileGenerator app.pixely.benchmark/androidx.test.runner.AndroidJUnitRunner
    adb pull /sdcard/Android/media/app.pixely.benchmark/BaselineProfileGenerator_generate-startup-prof.txt app/src/main/baseline-prof.txt
    # Measure (rebuild and reinstall the app first if the profile changed):
    adb shell am instrument -w -e class app.pixely.benchmark.PixelyBenchmarks app.pixely.benchmark/androidx.test.runner.AndroidJUnitRunner

The journeys use accessibility actions, not touches, because some phones block injected input.
Reinstall the debug APK afterwards for the UI tests.
