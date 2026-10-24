# Installing the AI model

Pixely uses one file: `embeddinggemma-2-740m.litertlm` (484,622,336 bytes), Google's EmbeddingGemma 2
740M in LiteRT-LM format, Apache 2.0. Other EmbeddingGemma files (270M, 440M, or the chip-specific
`_Qualcomm_…`, `_MediaTek_…` and `_Google_Tensor_…` builds) won't work.

Android only lets the app itself or `adb` write to Pixely's folder, so for now you need a computer.

## 1. Download it

From Hugging Face, no account needed. This link is pinned to the exact version Pixely was tested with:

    https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/24d962e906c7d332c6428e71c9676855024569e2/embeddinggemma-2-740m.litertlm

Check it (macOS: `shasum -a 256`, Linux: `sha256sum`, Windows: `certutil -hashfile <file> SHA256`):

    e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c

## 2. Copy it to the phone

1. Install [Android platform tools](https://developer.android.com/tools/releases/platform-tools)
   (`adb`).
2. On the phone: Settings → About phone → tap *Build number* 7 times, then Developer options →
   *USB debugging* on. Connect the cable and accept the prompt on the phone.
3. Install and open Pixely once, then:

       adb shell mkdir -p /sdcard/Android/data/app.pixely/files/models
       adb push embeddinggemma-2-740m.litertlm /sdcard/Android/data/app.pixely/files/models/

4. Close and reopen Pixely. Indexing starts and the Photos tab shows its progress.

You can turn USB debugging off afterwards. The file lives in Pixely's own storage, so **uninstalling
Pixely deletes it** (and the index). Updating the app keeps it.

## If it doesn't work

- "The AI model isn't on this phone yet": check the file name and folder exactly
  (`adb shell ls -l /sdcard/Android/data/app.pixely/files/models/`) and the size above.
- Settings → Diagnostics shows whether the model was found, any photos that failed, and a log you
  can share.
