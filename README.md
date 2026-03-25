# OpenBrain Ambient Android - Stage 1

## Overview
Stage 1 implements the foundation for ambient listening:
- **Wake-word detection**: Uses [PocketSphinx](https://github.com/cmusphinx/pocketsphinx) (via `pocketsphinx-android`) for fully open-source, offline hotword detection.
- **Background Service**: A persistent Android `Foreground Service` that runs the wake-word engine and maintains the "Active" state.
- **State Management**: A central `AmbientState` object that tracks whether the app is currently listening for conversations or just the wake-word.
- **UI**: A simple `MainActivity` to view the status and manually toggle the state.

## Why PocketSphinx?
- **Fully Open Source**: No API keys or enterprise accounts required.
- **Offline**: Runs entirely on the device.
- **Customizable**: Keywords and thresholds can be easily adjusted in code.
- **Classic Solution**: A long-standing, community-vetted engine for offline speech recognition.

## How to Build and Run
1.  **Acoustic Models**: PocketSphinx requires acoustic models (`en-us-ptm`) and a dictionary (`cmudict-en-us.dict`). These should be placed in the `assets/sync` folder of the `wakeword` or `app` module.
2.  **Keywords**:
    - "Hey Adam" triggers `AmbientState.setActive(true)`.
    - "Go to sleep" triggers `AmbientState.setActive(false)`.
3.  **Thresholds**: Sensitivity is controlled by thresholds (currently set to `1e-20` in `WakeWordEngine.kt`).
4.  **Permissions**: The app will request `RECORD_AUDIO` permission. Ensure this is granted on the device.
5.  **Build**: Run `./gradlew assembleDebug` or use Android Studio's "Run" button.

## Background Behavior & Battery
- The `AmbientService` is a `Foreground Service`, which prevents the OS from killing it while the app is in the background.
- Whisper STT and LLM inference are NOT implemented in this stage, keeping CPU usage low.

## Known Limitations
- **Threshold Tuning**: PocketSphinx may require threshold tuning to balance sensitivity and false positives.
- **Assets**: You must provide the PocketSphinx `en-us-ptm` and `cmudict` assets in the `assets` folder for it to initialize correctly.

# OpenBrain Ambient Android - Stage 2

## Overview
Stage 2 adds local Speech-To-Text (STT) capabilities:
- **Whisper Integration**: Uses [whisper.cpp](https://github.com/ggerganov/whisper.cpp) for high-quality, offline transcription.
- **Real-time Capture**: An `AudioCaptureManager` captures 16kHz mono audio from the microphone.
- **Streaming Loop**: When the app is "Active", audio chunks are periodically transcribed in the background.
- **Live UI**: MainActivity displays the live transcript and provides a "Clear" button.

## How to Build and Run
1.  **Whisper Model**: Download a quantized GGML model (e.g., `ggml-tiny.en.bin`).
2.  **Model Placement**: Place the model file in the app's external files directory:
    `/Android/data/com.openbrain.ambient/files/ggml-tiny.en.bin`
3.  **Permissions**: The app now requests `RECORD_AUDIO` permission at runtime.
4.  **Native Code**: The `asr` module contains the JNI wrapper and CMake configuration for `whisper.cpp`. Ensure you have the NDK installed.

## Implementation Details
- **Audio Chunks**: Audio is captured in 1-second segments and transcribed in 5-second chunks.
- **JNI Layer**: A minimal JNI bridge (`whisper-jni.cpp`) exposes `init`, `transcribe`, and `free` methods.
- **Performance**: Whisper runs on 4 threads by default for efficient mobile transcription.

## Known Limitations
- **Model Size**: Larger models (base, small) provide better accuracy but require more memory and CPU.
- **Latency**: Transcription occurs every 5 seconds; real-time word-by-word display is not yet implemented.
- **Battery**: Continuous Whisper inference is CPU-intensive; use "Sleeping" mode to conserve power.
