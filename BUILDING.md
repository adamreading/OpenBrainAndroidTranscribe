# OpenBrain Ambient Android - Build Instructions

## Prerequisites

1. **Android Studio**: Latest version from [developer.android.com/studio](https://developer.android.com/studio)
2. **NDK 25+**: In Android Studio: `Tools > SDK Manager > SDK Tools` — install `NDK (Side by side)` (version 25 or higher) and `CMake`
3. **Android SDK 35**: Install API 35 SDK platform via SDK Manager
4. **Kotlin 1.9.10+**: Included with Android Studio

## Clone Native Dependencies

Both whisper.cpp and llama.cpp source must be cloned locally before building (they are not committed to the repo due to size):

```bash
# Clone whisper.cpp for the ASR module
cd asr/src/main/cpp
git clone https://github.com/ggerganov/whisper.cpp.git
cd ../../../..

# Clone llama.cpp for the LLM module
cd llm/src/main/cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd ../../../..
```

**Important**: The CMakeLists.txt files reference `${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp` and `${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp` subdirectories. The native build will fail if these directories don't exist.

## Building with Android Studio (Recommended)

1. Open Android Studio
2. Select **File > Open** and navigate to the project root folder
3. Wait for Gradle sync to finish — resolve any missing SDK/NDK warnings
4. Connect your physical Android device (Developer Options + USB Debugging enabled)
5. Click **Run** to build and install

## Building from Command Line

```bash
# Generate wrapper if needed (open project in Android Studio first)
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Loading Models onto Device

### Whisper STT Model
```bash
# Create directories on device
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/whisper
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/llm

# Push whisper model (tiny recommended for development)
adb push ggml-tiny.en.bin /sdcard/Android/data/com.openbrain.ambient/files/whisper/
```

### LLM Model (for memory extraction)
```bash
# Push LLM model (Phi-3-mini Q4 recommended)
adb push phi-3-mini-4k-instruct-q4.gguf /sdcard/Android/data/com.openbrain.ambient/files/llm/
```

Alternative LLM models:
- `gemma-2b-it-q4_k_m.gguf` — smaller, faster
- `phi-3-mini-4k-instruct-q4.gguf` — better extraction quality

**Note**: On Android 11+ with scoped storage, you may need to use `adb shell` to copy files if direct push fails:
```bash
adb push model.bin /data/local/tmp/
adb shell run-as com.openbrain.ambient cp /data/local/tmp/model.bin /storage/emulated/0/Android/data/com.openbrain.ambient/files/whisper/
```

## Samsung S26 Ultra Notes

- The app requests battery optimisation exemption on first launch — tap "Allow" for reliable background operation
- If the service gets killed, open the app again to restart it
- One UI Settings > Apps > OpenBrain Ambient > Battery > Unrestricted (manual override if needed)

## NDK Version Requirements

- **Minimum**: NDK 25.0.x
- **Recommended**: NDK 26.1.x or later
- C++17 is required for both whisper.cpp and llama.cpp
- CMake 3.18.1+ is required

## Troubleshooting

| Issue | Solution |
|-------|----------|
| CMake error "whisper.cpp not found" | Clone whisper.cpp into `asr/src/main/cpp/` |
| CMake error "llama.cpp not found" | Clone llama.cpp into `llm/src/main/cpp/` |
| "Whisper model not found" at runtime | Push model file to device (see above) |
| Service killed on Samsung | Grant battery optimisation exemption |
| AudioRecord init failed | Check RECORD_AUDIO permission is granted |
