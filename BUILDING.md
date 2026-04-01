# OpenBrain Ambient Android — Build Instructions

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Panda 2 / 2025.3.2 or later | Tested on this version |
| NDK (Side by side) | 25.0+ (26.1 recommended) | Install via SDK Manager → SDK Tools |
| CMake | Any version in SDK Manager | Install via SDK Manager → SDK Tools |
| Android SDK | API 35 | compileSdk and targetSdk both set to 35 |
| Kotlin | 1.9.25 | Bundled with Android Studio |
| Java / JVM target | 17 | Set in all build.gradle files |
| Git | Any recent version | Needed to clone native dependencies |

---

## Step 1 — Clone the project

In Android Studio welcome screen: **Clone Repository**

```
https://github.com/adamreading/OpenBrainAndroidTranscribe
```

Or via command line:
```bash
git clone https://github.com/adamreading/OpenBrainAndroidTranscribe
```

---

## Step 2 — Install NDK and CMake

In Android Studio: **Tools → SDK Manager → SDK Tools tab**

Tick both:
- ✅ NDK (Side by side)
- ✅ CMake

Click OK and let them install.

---

## Step 3 — Clone native C++ dependencies

whisper.cpp and llama.cpp are NOT committed to the repo (too large). You must clone them locally before building.

Open **Git Bash** (Windows) or Terminal (Mac/Linux) and run:

```bash
# Whisper STT engine
cd "C:/Users/YOUR_USERNAME/StudioProjects/OpenBrainAndroidTranscribe/asr/src/main/cpp"
git clone https://github.com/ggerganov/whisper.cpp.git

# LLM inference engine
cd "C:/Users/YOUR_USERNAME/StudioProjects/OpenBrainAndroidTranscribe/llm/src/main/cpp"
git clone https://github.com/ggerganov/llama.cpp.git
```

Replace `YOUR_USERNAME` with your actual Windows username.

After cloning, the structure should look like:
```
asr/src/main/cpp/
  ├── CMakeLists.txt
  ├── whisper-jni.cpp
  └── whisper.cpp/           ← cloned here
      ├── src/whisper.cpp
      ├── include/whisper.h
      └── ggml/src/*.c

llm/src/main/cpp/
  ├── CMakeLists.txt
  ├── llama-jni.cpp
  └── llama.cpp/             ← cloned here
      ├── src/llama.cpp
      ├── include/llama.h
      └── ggml/src/*.c
```

---

## Step 4 — Sync and Build

1. In Android Studio: **File → Sync Project with Gradle Files**
2. Wait for sync to complete (3–5 minutes first time)
3. Should show: **"BUILD SUCCESSFUL"** in the Build panel

### Common sync errors and fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `CMake Error: Cannot find source file` | whisper.cpp or llama.cpp not cloned | Run Step 3 above |
| Linker errors (`undefined symbol: llm_build_*`) | Outdated explicit source list in CMake | Fixed — CMakeLists.txt now uses `file(GLOB)` to include all source files automatically |

> **Note on source file inclusion**: The CMakeLists.txt files use `file(GLOB ...)` to include all `.c` and `.cpp` files from llama.cpp and whisper.cpp source directories. If you update your llama.cpp or whisper.cpp clone to a newer version, the GLOB approach will automatically pick up any new source files — no CMakeLists.txt changes needed. You only need to re-run CMake configure (Gradle does this on sync).

> **Note on llama.cpp API compatibility**: The JNI bridge (`llama-jni.cpp`) targets the current llama.cpp API as of April 2026. If you update your llama.cpp clone to a newer version, the API may have changed — check for renamed functions (e.g. model init, vocab access, KV cache / memory management) and update the JNI bridge accordingly.

---

## Step 5 — Connect your Android device

### Samsung Galaxy S26 Ultra (primary target)

Samsung has several security features that need configuring once:

**1. Enable Developer Mode**
- Settings → About phone → Software information
- Tap **Build number** 7 times
- Enter your PIN when prompted
- "Developer mode has been enabled" appears

**2. Disable Auto Blocker (Samsung-specific)**
- Settings → Security and privacy → Auto Blocker
- Toggle **OFF**
- (Can be re-enabled after app is installed)

**3. Enable USB Debugging**
- Settings → Developer options
- Toggle **USB debugging** ON

**4. Connect USB cable**
- Phone will show "Allow USB debugging?" popup
- Tap **Allow** (tick "Always allow from this computer" to avoid repeating)

**5. Battery Optimisation (important for background service)**
- The app will request this on first launch — tap Allow
- Or manually: Settings → Apps → OpenBrain Ambient → Battery → **Unrestricted**

---

## Step 6 — Run the app

1. In Android Studio, confirm the device selector at top shows your Samsung
2. Click the green **▶ Run** button
3. First build takes 5–15 minutes (compiling C++ for ARM64)
4. App installs and launches automatically on phone
5. Grant **RECORD_AUDIO** permission when prompted

---

## Step 7 — Push model files to device

The app needs these two model files on the device to fully function. Without them it will still launch, but Whisper transcription and LLM extraction won't work.

### Download the models

**Whisper STT model** (start with tiny — 75 MB):
- Go to: https://huggingface.co/ggerganov/whisper.cpp/tree/main
- Download: `ggml-tiny.en.bin`

**LLM model for memory extraction** (~2 GB):
- Go to: https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf
- Download: `Phi-3-mini-4k-instruct-q4.gguf`
- Alternative (smaller, 1.5 GB): search HuggingFace for `gemma-2b-it-q4_k_m.gguf`

### Push to device via ADB

Open **Command Prompt** in Windows (not Git Bash — regular cmd):

```cmd
REM Create directories on device
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/whisper
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/llm

REM Push Whisper model (run from folder where you downloaded it)
adb push ggml-tiny.en.bin /sdcard/Android/data/com.openbrain.ambient/files/whisper/

REM Push LLM model
adb push Phi-3-mini-4k-instruct-q4.gguf /sdcard/Android/data/com.openbrain.ambient/files/llm/
```

> **Note**: `adb` is automatically available in Command Prompt once Android Studio is installed. If it says "adb not found", restart Command Prompt after installing Android Studio.

### If adb push fails with permission error

On Android 11+ Samsung devices, try this alternative:
```cmd
adb push ggml-tiny.en.bin /data/local/tmp/
adb shell run-as com.openbrain.ambient cp /data/local/tmp/ggml-tiny.en.bin /storage/emulated/0/Android/data/com.openbrain.ambient/files/whisper/
```

---

## Step 8 — Configure Supabase connection

1. Open the app → tap **Admin**
2. Enter your **Supabase Project URL** (e.g. `https://xxxx.supabase.co`)
3. Enter your **Supabase API key** (service role key from Supabase dashboard → Settings → API)
4. Tap **Test Connection** — should show "OK"
5. Tap **Save Settings**

---

## App behaviour after setup

- Say **"Hey Adam"** → app activates, notification changes to "Listening"
- Say **"Go to sleep"** → app deactivates
- Every 60 seconds of active listening, transcripts are sent to the LLM for memory extraction
- Extracted memories are queued and synced to your Supabase OpenBrain instance
- Sync log visible in Admin screen

---

## Building from command line

Once the project has been opened in Android Studio at least once (which generates wrapper files):

```bash
# Windows
gradlew.bat assembleDebug

# Mac/Linux
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Module structure

```
OpenBrainAndroidTranscribe/
├── app/                    Main app module — service, state, pipeline
├── asr/                    Whisper STT module (JNI + C++)
├── llm/                    LLaMA LLM module (JNI + C++)
├── openbrain-client/       Supabase REST client + WorkManager sync
├── ui/                     AdminActivity settings UI
└── wakeword/               Wake word engine (SpeechRecognizer)
```
