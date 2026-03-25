# OpenBrain Ambient Android - Build Instructions

To build this project into a working Android APK, you should ideally use **Android Studio**.

## Prerequisites
1.  **Android Studio**: Download and install the latest version of [Android Studio](https://developer.android.com/studio).
2.  **NDK (Native Development Kit)**: This project uses C++ (whisper.cpp). In Android Studio, go to `Tools > SDK Manager > SDK Tools` and check `NDK (Side by side)` and `CMake` to install them.
3.  **PocketSphinx Assets**: You must download the acoustic models and place them in the correct directory.
    - Create the folder: `app/src/main/assets/sync/`
    - Place `en-us-ptm` and `cmudict-en-us.dict` in that folder. (These can be found in the [PocketSphinx Android repository](https://github.com/cmusphinx/pocketsphinx-android-demo/tree/master/app/src/main/assets/sync)).
4.  **Whisper Model**: Download a GGML model (e.g., `ggml-tiny.en.bin` from the [whisper.cpp releases](https://github.com/ggerganov/whisper.cpp/tree/master/models)).
    - For development, you can place it in your device's external storage later.
5.  **Whisper Native Code**: The native code for whisper is missing from this barebones setup. You need to clone the `whisper.cpp` repository into the `asr/src/main/cpp/` directory before building:
    ```bash
    cd asr/src/main/cpp
    git clone https://github.com/ggerganov/whisper.cpp.git
    ```

## Building with Android Studio (Recommended)
1.  Open Android Studio.
2.  Select **File > Open** and navigate to the `OpenBrainAndroid` folder.
3.  Android Studio will automatically detect the Gradle project and generate the necessary wrapper files (`gradlew`).
4.  Wait for the Gradle sync to finish. Resolve any missing SDK or NDK warnings it prompts you about.
5.  Connect your physical Android device (with Developer Options and USB Debugging enabled).
6.  Click the green **Run (Play)** button in the top toolbar to build and install the APK onto your device.

## Building from Command Line
Once you have opened the project in Android Studio at least once (which generates the `gradlew` wrapper), you can build from the command line:

```bash
# Windows
gradlew assembleDebug

# Mac/Linux
./gradlew assembleDebug
```
The compiled APK will be located in `app/build/outputs/apk/debug/app-debug.apk`.

## Loading Models onto the Device
1.  **PocketSphinx**: Built into the APK via the `assets` folder.
2.  **Whisper Model**: Push the downloaded `ggml-tiny.en.bin` to the app's files directory on your device:
    ```bash
    adb push ggml-tiny.en.bin /sdcard/Android/data/com.openbrain.ambient/files/
    ```
    *(Note: Depending on your Android version, you might need to use `adb shell` to copy it to the exact directory if permissions restrict direct push).*
