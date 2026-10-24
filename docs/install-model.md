# Install the AI model

Pixely needs one file on your phone: `embeddinggemma-2-740m.litertlm` (485 MB). You copy it from a
computer with `adb` (Windows, macOS or Linux), once.

## 1. Download the model (on your computer)

[Download embeddinggemma-2-740m.litertlm](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/24d962e906c7d332c6428e71c9676855024569e2/embeddinggemma-2-740m.litertlm)
from Hugging Face (no account needed).

Use exactly this file. The other files on that page (270M, 440M, or names ending in `_Qualcomm_…`,
`_MediaTek_…`, `_Google_Tensor_…`) won't work.

Optional check: its SHA-256 is
`e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`
(`shasum -a 256 <file>` on macOS, `sha256sum` on Linux, `certutil -hashfile <file> SHA256` on Windows).

## 2. Get adb

Download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
and unzip it. `adb` is inside. (macOS with Homebrew: `brew install android-platform-tools`.)

## 3. Turn on USB debugging (on your phone)

1. Settings → About phone → tap **Build number** 7 times.
2. Settings → Developer options → turn on **USB debugging**.
3. Connect the phone by USB and tap **Allow** when it asks.

Check it worked: `adb devices` should list your phone as `device`.

## 4. Copy the model to the phone

Install Pixely and open it once. Then, in the folder with the model file:

    adb shell mkdir -p /sdcard/Android/data/app.pixely/files/models
    adb push embeddinggemma-2-740m.litertlm /sdcard/Android/data/app.pixely/files/models/

## 5. Done

Close Pixely and open it again. Indexing starts. You can turn USB debugging off now.

**Note:** uninstalling Pixely deletes the model and the index. Updating Pixely keeps them.

## Problems

- **"The AI model isn't on this phone yet":** check the file is there with the exact name:
  `adb shell ls -l /sdcard/Android/data/app.pixely/files/models/` (size 484622336).
- **`adb devices` shows `unauthorized`:** unlock the phone and accept the USB debugging prompt.
- **Anything else:** Settings → Diagnostics → Share, and attach it to a
  [GitHub issue](https://github.com/Huthayfa-Malhas/pixely/issues).
