# Stage 3 — Memory Extraction & Cloud Sync

## Architecture

```
┌──────────────────────────────────────────────┐
│                 AmbientService                │
│                                               │
│  ┌───────────────────────────────────┐       │
│  │          AudioPipeline            │       │
│  │   (single AudioRecord, 16kHz)     │       │
│  │                                   │       │
│  │  broadcasts 100ms PCM chunks to:  │       │
│  │   ├── AudioCaptureManager         │       │
│  │   └── WakeWordEngine (feedAudio)  │       │
│  └───────────────────────────────────┘       │
│                                               │
│  ┌─────────────────┐  ┌──────────────────┐   │
│  │ WakeWordEngine   │  │AudioCaptureManager│  │
│  │ (SpeechRecognizer│  │ (rolling 60s      │  │
│  │  offline fallback)│ │  float buffer)    │  │
│  └────────┬─────────┘  └───────┬──────────┘  │
│           │                     │             │
│      wake/sleep             getNextChunk(5)   │
│      callbacks                  │             │
│           │            ┌────────▼─────────┐   │
│           │            │   WhisperLib     │   │
│           │            │ (whisper.cpp JNI) │   │
│           │            └────────┬─────────┘   │
│           │                     │ transcript  │
│           ▼                     ▼             │
│  ┌─────────────────────────────────────┐     │
│  │           AmbientState              │     │
│  │  isActive (DataStore)               │     │
│  │  transcript (in-memory)             │     │
│  │  syncLog (DataStore, last 50)       │     │
│  └─────────────────────────────────────┘     │
│                     │                         │
│              every 60s active                 │
│                     ▼                         │
│  ┌─────────────────────────────────────┐     │
│  │         MemoryExtractor             │     │
│  │   (extraction prompt → LlamaLib)    │     │
│  └────────────────┬────────────────────┘     │
│                   │ List<MemoryItem>          │
│                   ▼                           │
│  ┌─────────────────────────────────────┐     │
│  │         MemorySyncWorker            │     │
│  │   (JSON file queue + WorkManager)   │     │
│  └────────────────┬────────────────────┘     │
│                   │                           │
│                   ▼                           │
│  ┌─────────────────────────────────────┐     │
│  │         OpenBrainClient             │     │
│  │   (Retrofit → Supabase REST API)    │     │
│  │   3x retry with exponential backoff │     │
│  └─────────────────────────────────────┘     │
└──────────────────────────────────────────────┘
```

## New Files Created

### app module
- `AudioPipeline.kt` — Unified audio capture, single AudioRecord, broadcasts to listeners
- `AppSettings.kt` — DataStore preferences keys for all configuration
- `AmbientState.kt` — Rewritten with DataStore persistence for isActive and syncLog

### app module (modified)
- `AudioCaptureManager.kt` — Refactored to AudioPipelineListener (no own AudioRecord)
- `AmbientService.kt` — Full pipeline wiring: audio, whisper, llama, extraction, sync
- `MainActivity.kt` — Battery exemption, DataStore init, sync log passing

### wakeword module
- `WakeWordEngine.kt` — Replaced PocketSphinx with SpeechRecognizer offline fallback

### llm module (all new)
- `cpp/CMakeLists.txt` — CMake for llama.cpp native build
- `cpp/llama-jni.cpp` — JNI bridge: init, inference (greedy sampling), free
- `LlamaLib.kt` — Kotlin native method wrapper
- `MemoryItem.kt` — Data class for extracted memories
- `MemoryExtractor.kt` — Extraction prompt + JSON parsing

### openbrain-client module (all new)
- `OpenBrainApi.kt` — Retrofit interface for Supabase REST API
- `MemoryRequest.kt` — Request body data class
- `OpenBrainClient.kt` — HTTP client with retry logic
- `MemorySyncWorker.kt` — WorkManager-based background sync

### ui module
- `AdminActivity.kt` — Full settings UI with SharedPreferences
- `SyncLogAdapter.kt` — RecyclerView adapter for sync log display
- `activity_admin.xml` — Complete settings layout
- `item_sync_log.xml` — Sync log list item layout

## Model Downloads

### Whisper (STT)
- **Tiny** (75 MB): `ggml-tiny.en.bin` — fastest, lowest accuracy
- **Base** (142 MB): `ggml-base.en.bin` — good balance
- **Small** (466 MB): `ggml-small.en.bin` — best accuracy, more RAM

Source: https://huggingface.co/ggerganov/whisper.cpp/tree/main

### LLM (Memory Extraction)
- **Phi-3-mini Q4** (~2 GB): `phi-3-mini-4k-instruct-q4.gguf` — recommended
- **Gemma 2B Q4** (~1.5 GB): `gemma-2b-it-q4_k_m.gguf` — lighter alternative

Source: https://huggingface.co/models?search=gguf

### Placement
```bash
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/whisper
adb shell mkdir -p /sdcard/Android/data/com.openbrain.ambient/files/llm
adb push ggml-tiny.en.bin /sdcard/Android/data/com.openbrain.ambient/files/whisper/
adb push phi-3-mini-4k-instruct-q4.gguf /sdcard/Android/data/com.openbrain.ambient/files/llm/
```

## Build Instructions

Requires NDK 25+ with C++17 support. See [BUILDING.md](BUILDING.md) for full instructions.

Native libraries (whisper.cpp and llama.cpp) must be cloned into their respective module directories before building — they are not committed to the repo.
