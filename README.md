# OpenBrain Ambient Android

Ambient listening and memory extraction for Android. Runs entirely on-device using whisper.cpp for speech-to-text and llama.cpp for LLM-based memory extraction, with optional sync to a Supabase backend.

## Stages

### Stage 1 — Wake Word Detection
- Wake-word detection using Android SpeechRecognizer in offline mode
- Background Foreground Service for persistent listening
- Central `AmbientState` with DataStore-backed persistence
- Simple toggle UI in MainActivity

### Stage 2 — Speech-To-Text
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) integration via JNI for offline transcription
- Real-time audio capture and 5-second transcription chunks
- Live transcript display in MainActivity

### Stage 3 — Memory Extraction & Sync
- Unified AudioPipeline (single AudioRecord, multiple listeners)
- [llama.cpp](https://github.com/ggerganov/llama.cpp) integration via JNI for on-device LLM inference
- MemoryExtractor: extracts decisions, tasks, facts, reminders from transcripts
- OpenBrain client with Retrofit + WorkManager for reliable Supabase sync
- Full AdminActivity settings UI (connection, models, wake words, sync log)
- Samsung Galaxy S26 Ultra optimisations (battery, permissions, service types)

## Device Support

**Primary target**: Samsung Galaxy S26 Ultra (Android 15 / One UI 7)

Samsung-specific handling:
- Battery optimisation exemption request on first launch
- `FOREGROUND_SERVICE_MICROPHONE` permission for Android 14+
- `foregroundServiceType="microphone|dataSync"` for background operation
- `MediaRecorder.AudioSource.MIC` (not VOICE_COMMUNICATION) for Samsung compatibility
- All file writes inside app-private directories (Knox safe)

**Secondary**: Any Android 14+ device

## Model Files

Model files are NOT included in the repository. Download and push them to your device:

### Whisper STT Model
```bash
# Download from https://huggingface.co/ggerganov/whisper.cpp/tree/main
# Options: ggml-tiny.en.bin (75MB), ggml-base.en.bin (142MB), ggml-small.en.bin (466MB)
adb push ggml-tiny.en.bin /sdcard/Android/data/com.openbrain.ambient/files/whisper/
```

### LLM Model (for memory extraction)
```bash
# Download a GGUF model — recommended: Phi-3-mini or Gemma-2B quantized
# From https://huggingface.co/models?search=gguf
adb push phi-3-mini-4k-instruct-q4.gguf /sdcard/Android/data/com.openbrain.ambient/files/llm/
```

## Quick Start

1. See [BUILDING.md](BUILDING.md) for full build instructions
2. Push model files to device (see above)
3. Install APK and grant RECORD_AUDIO permission
4. App requests battery optimisation exemption on first launch
5. Say "Hey Adam" to activate, "Go to sleep" to deactivate
6. Open Settings to configure Supabase connection for cloud sync

## Architecture

See [STAGE3.md](STAGE3.md) for detailed Stage 3 architecture.
