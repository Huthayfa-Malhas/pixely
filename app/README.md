# Pixely

On-device photo search for Android. See the "Pixely v1 Build Plan" doc for scope and decisions.

Status: weeks 2–3 (Foundation) — Welcome, photo access, MediaStore sync, Photos grid, viewer,
Room 3 + sqlite-vec with the plan's tables, and `VectorIndex`. No model yet (weeks 4–5).

## Build

    export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
    export ANDROID_HOME=$(brew --prefix)/share/android-commandlinetools
    ./gradlew :app:assembleDebug :app:testDebugUnitTest
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Layout

- `data/db` — Room 3 entities (`photo`, `photo_embedding`, `index_meta`), DAOs, and the database
  (bundled driver; `photo_search` vec0 table and clean-up triggers are created in its callback).
- `data/search` — `VectorIndex`, `SqliteVecIndex` (search size + re-check at 768), `KotlinScanIndex` fallback.
- `data/media` — `PhotoAccess` (full / partial / none), `MediaStoreSource`, `PhotoSync` (full diff),
  `LibraryWatcher` (sync on foreground and on MediaStore changes, debounced).
- `ui` — Navigation 3 (`Welcome`, `Photos`, `Viewer`), theme and icons from the design canvas.

`app/src/main/jniLibs/*/libsqlite_vec.so` is sqlite-vec v0.1.9, pinned.

## Test photos

Debug builds read only `Pictures/PixelyTest/` so testing never shows the phone's own library
(`-Ppixely.testFolder=` for everything; release builds always see everything).
