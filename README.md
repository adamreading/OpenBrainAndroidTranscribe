# OpenBrain Ambient Android

Ambient listening and memory extraction for Android. Runs entirely on-device using whisper.cpp for speech-to-text and llama.cpp for LLM-based memory extraction, with automatic sync to your personal [OpenBrain](https://github.com/NateBJones-Projects/OB1) Supabase memory backend.

**No cloud STT. No cloud LLM. Your voice stays on your device.**

---

## What it does

1. Runs silently in the background as a persistent foreground service
2. Listens for **"Hey Adam"** — activates full listening mode
3. Captures and transcribes everything via on-device Whisper STT
4. Every 60 seconds, sends the transcript to an on-device LLM (Phi-3-mini / Gemma)
5. The LLM extracts only the important stuff: decisions, tasks, facts, reminders
6. Sends those extracted memories to your OpenBrain Supabase instance over HTTPS
7. **"Go to sleep"** — returns to low-power wake-word-only mode

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  AmbientService                  │
│                                                  │
│  AudioPipeline (single AudioRecord, 16kHz)       │
│     │                                            │
│     ├──► WakeWordEngine                          │
│     │    (Android SpeechRecognizer, offline)     │
│     │    "Hey Adam" → active=true                │
│     │    "Go to sleep" → active=false            │
│     │                                            │
│     └──► AudioCaptureManager                     │
│          (rolling 60s float buffer)              │
│               │                                  │
│               ▼ every 5s (when active)           │
│          WhisperLib (whisper.cpp JNI)            │
│               │ transcript text                  │
│               ▼                                  │
│          AmbientState                            │
│          (DataStore-backed persistence)          │
│               │                                  │
│               ▼ every 60s (when active)          │
│          MemoryExtractor                         │
│          (LlamaLib + extraction prompt)          │
│               │ List<MemoryItem> JSON            │
│               ▼                                  │
│          MemorySyncWorker (WorkManager)          │
│               │ POST /rest/v1/memories           │
│               ▼                                  │
│          OpenBrainClient (Retrofit)              │
│          → Supabase REST API                     │
│          → 3x exponential backoff retry          │
└─────────────────────────────────────────────────┘
```

### Modules

| Module | Purpose |
|--------|---------|
| `app` | Main service, audio pipeline, state management |
| `asr` | Whisper STT via whisper.cpp JNI |
| `llm` | LLM inference via llama.cpp JNI |
| `openbrain-client` | Supabase REST client + WorkManager sync queue |
| `ui` | Admin / settings screen |
| `wakeword` | Wake word detection engine |

---

## Stages

### Stage 1 — Wake Word Detection ✅
- Always-on background foreground service
- SpeechRecognizer offline fallback (ONNX-ready interface for future custom model)
- "Hey Adam" / "Go to sleep" phrase detection
- DataStore-backed `isActive` state that survives process death
- Manual toggle in MainActivity

### Stage 2 — Local Speech-To-Text ✅
- whisper.cpp integration via JNI (CMake / NDK)
- Unified AudioPipeline — single AudioRecord feeds all consumers
- 5-second chunked transcription loop
- Live transcript in MainActivity

### Stage 3 — Memory Extraction & Cloud Sync ✅
- llama.cpp integration via JNI (CMake / NDK)
- MemoryExtractor: structured JSON extraction prompt (decisions, tasks, facts, reminders)
- Supabase REST client with 3x exponential backoff retry
- WorkManager queue — syncs survive app kill and network outages
- Full AdminActivity: connection config, model settings, wake words, battery, sync log
- Samsung Galaxy S26 Ultra specific optimisations

### Build Status
- Project builds cleanly against current whisper.cpp and llama.cpp APIs (as of April 2026)
- JNI bridges (`whisper-jni.cpp`, `llama-jni.cpp`) are up to date with upstream API changes
- CMakeLists.txt uses `file(GLOB ...)` to include all native source files — updating llama.cpp or whisper.cpp to a newer version automatically picks up new/renamed source files without editing CMake
- All modules target JVM 17
- Launcher icons are placeholder PNGs — replace with real app branding when ready

### Stage 4 — Planned (see TODO.md)
- Custom ONNX wake word model for "Hey Adam"
- Voice Activity Detection (skip silence)
- Streaming Whisper output
- Bidirectional Supabase sync

---

## Device Support

**Primary target**: Samsung Galaxy S26 Ultra (Android 15 / One UI 7)

Samsung-specific handling built in:
- Battery optimisation exemption request on first launch
- `FOREGROUND_SERVICE_MICROPHONE` permission (Android 14+ requirement)
- `foregroundServiceType="microphone|dataSync"` for background operation
- `MediaRecorder.AudioSource.MIC` (Samsung Knox compatible)
- Auto Blocker must be disabled once to enable USB debugging during development

**Minimum**: Android 8.0 (API 26)

---

## Model Files Required

Model files are NOT in the repo — download and push to device separately.

| Model | File | Size | Purpose |
|-------|------|------|---------|
| Whisper tiny | `ggml-tiny.en.bin` | 75 MB | Speech-to-text (fast, lower accuracy) |
| Whisper base | `ggml-base.en.bin` | 142 MB | Speech-to-text (balanced) |
| Phi-3-mini Q4 | `phi-3-mini-4k-instruct-q4.gguf` | ~2 GB | Memory extraction LLM (recommended) |
| Gemma 2B Q4 | `gemma-2b-it-q4_k_m.gguf` | ~1.5 GB | Memory extraction LLM (lighter) |

See [BUILDING.md](BUILDING.md) for download links and push instructions.

---

## Quick Start

See **[BUILDING.md](BUILDING.md)** for the full step-by-step guide including:
- Android Studio setup
- NDK / CMake installation
- Cloning native dependencies (whisper.cpp, llama.cpp)
- Samsung S26 USB debugging setup (Auto Blocker, Developer Mode)
- Pushing model files to device
- Configuring Supabase connection

---

## OpenBrain Backend

This app is designed to work with [Nate B. Jones's OpenBrain](https://promptkit.natebjones.com) architecture:
- Supabase Postgres with pgvector
- Deno Edge Function MCP server (`open-brain-mcp`)
- The app POSTs to `/rest/v1/memories` with your project URL and service role key

The extracted memories become searchable from any MCP-connected AI (Claude, ChatGPT, Cursor, etc.) via the `search_thoughts` tool.
