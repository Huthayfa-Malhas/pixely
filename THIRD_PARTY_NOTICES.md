# Third-party notices

Pixely's own code is Apache-2.0 (see `LICENSE`). These are the other things it uses or ships.

## Shipped inside the app

| What | Where | Licence | Notice |
|---|---|---|---|
| [sqlite-vec](https://github.com/asg017/sqlite-vec) v0.1.9 by Alex Garcia | `app/app/src/main/jniLibs/*/libsqlite_vec.so` | MIT or Apache-2.0 (used under MIT) | `licenses/MIT-sqlite-vec.txt` |
| [Figtree](https://github.com/erikdkennedy/figtree) by Erik Kennedy | `app/app/src/main/res/font/figtree.ttf` | SIL Open Font License 1.1 | `licenses/OFL-Figtree.txt` |
| [Bricolage Grotesque](https://github.com/ateliertriay/bricolage) by Mathieu Triay | `app/app/src/main/res/font/bricolage_grotesque.ttf` | SIL Open Font License 1.1 | `licenses/OFL-Bricolage-Grotesque.txt` |
| [Lucide](https://lucide.dev) "settings" icon | `app/app/src/main/java/app/pixely/ui/Icons.kt` | ISC | `licenses/ISC-Lucide.txt` |

Libraries pulled in by Gradle, all Apache-2.0: AndroidX (Compose, Activity, Lifecycle,
Navigation 3, Room 3, SQLite, WorkManager, Hilt extensions, ProfileInstaller, Benchmark, Test),
Dagger Hilt, Kotlin and kotlinx (coroutines, serialization), Coil 3, MediaPipe Tasks, and LiteRT-LM.
The Gradle wrapper (`gradle/wrapper/gradle-wrapper.jar`) is Apache-2.0.

Pixely leaves out Google's usage-statistics libraries (`com.google.android.datatransport`,
`com.google.firebase`) that MediaPipe depends on. The do-nothing classes that replace them under
`app/app/src/main/java/com/google/android/datatransport/` are Pixely's own code.

## The AI model (downloaded separately)

[EmbeddingGemma 2 740M](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm),
LiteRT-LM build, by Google. Apache-2.0 (base model:
[google/embeddinggemma-2](https://huggingface.co/google/embeddinggemma-2)). It is not in this
repository or in the APK; see the README for how to install it.

## Test data

`app/app/src/androidTest/assets/search_eval.json` holds captions from
[COCO](https://cocodataset.org) (annotations CC BY 4.0, © COCO Consortium) and the altered
captions from [SugarCrepe](https://github.com/RAIVNLab/sugar-crepe) (MIT, © 2023 RAIVN Lab,
`app/app/src/androidTest/assets/MIT-SugarCrepe.txt`). It is only used by the on-phone tests and is not part of the app.
The COCO photos themselves are not in this repository.
