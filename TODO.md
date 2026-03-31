# OpenBrain Ambient Android — TODO

## Current Status
- ✅ Stage 1: Wake word detection (SpeechRecognizer offline fallback)
- ✅ Stage 2: Whisper STT via whisper.cpp JNI
- ✅ Stage 3: LLM extraction + Supabase sync
- 🔲 Stage 4: items below

---

## Known Limitations (fix before production)

- [ ] **Wake word accuracy**: SpeechRecognizer restarts every ~5–10s — brief gap between recognition cycles. Works for dev/testing but needs ONNX replacement for production
- [ ] **Whisper latency**: 5-second chunks means up to 5s delay before transcription. Real-time word-by-word not yet implemented
- [ ] **LLM cold start**: First inference after loading model is slow (~10–30s on Phi-3-mini). Consider warming up on service start
- [ ] **AdminActivity ↔ DataStore**: Settings saved to SharedPreferences in AdminActivity but DataStore used in AmbientService — needs unification (see Stage 4 cleanup below)
- [ ] **Test connection button**: Currently shows placeholder text — needs to call OpenBrainClient.testConnection() directly

---

## Stage 4 — Wake Word

- [ ] Train custom OpenWakeWord ONNX model for "Hey Adam" and "Go to Sleep"
  - Use [openWakeWord training guide](https://github.com/dscripka/openWakeWord)
  - Bundle `.onnx` model files in `wakeword/src/main/assets/`
- [ ] Integrate ONNX Runtime for Android into wakeword module
  - Replace SpeechRecognizer fallback with direct PCM processing via `feedAudio()`
  - The `feedAudio()` method is already wired in `AudioPipeline` — just implement it
- [ ] Add configurable wake word sensitivity threshold in AdminActivity
- [ ] Support multiple simultaneous wake word models

---

## Stage 4 — Whisper Improvements

- [ ] Voice Activity Detection (VAD) — skip silent audio chunks before sending to Whisper
  - Saves CPU, reduces blank transcripts
  - whisper.cpp has built-in VAD params (`vad_thold`, `freq_thold`)
- [ ] Streaming Whisper — word-by-word real-time display
  - Use whisper.cpp partial results / segment callbacks
- [ ] Dynamic model switching based on battery/charging state
  - tiny when < 30% battery, base when charging
- [ ] Configurable chunk size (currently 5s hardcoded)

---

## Stage 4 — LLM / Memory Extraction

- [ ] Unify AdminActivity SharedPreferences → DataStore (AppSettings)
  - AdminActivity currently writes to SharedPreferences
  - AmbientService reads from DataStore
  - These are currently disconnected — settings changes don't take effect until restart
- [ ] Structured output / JSON grammar constraints in llama.cpp
  - Use llama.cpp grammar sampling to guarantee valid JSON output
  - Eliminates need for fragile JSON extraction in MemoryExtractor.parseMemoryItems()
- [ ] Context window management for long sessions
  - Current: full transcript sent every 60s — will exceed context window in long sessions
  - Fix: sliding window (last N tokens) + rolling summary
- [ ] Customizable extraction prompts in AdminActivity
- [ ] Streaming LLM output for real-time extraction feedback

---

## Stage 4 — Battery & Performance

- [ ] Battery usage monitoring in AdminActivity
- [ ] Adaptive processing frequency (more aggressive batching when on battery)
- [ ] CPU thermal throttling detection and backoff
- [ ] Wake lock management audit — ensure PARTIAL_WAKE_LOCK released properly
- [ ] Profile memory usage with Samsung Phi-3-mini loaded simultaneously with Whisper

---

## Stage 4 — Sync & Backend

- [ ] Wire up `testConnection` button in AdminActivity to actually call `OpenBrainClient`
- [ ] Bidirectional sync — pull recent memories from Supabase on app start
- [ ] Sync conflict resolution for offline edits
- [ ] Sync status indicator in MainActivity (last sync time, pending count)
- [ ] Support posting to OpenBrain MCP `capture_thought` endpoint as alternative to REST

---

## Stage 4 — UI / UX

- [ ] Material Design 3 / dynamic colour theme (Samsung One UI compatible)
- [ ] Notification actions — tap to toggle active, view last transcript
- [ ] Quick Settings tile for fast toggle without opening app
- [ ] Memory browser — view and search extracted memories on device
- [ ] Transcript history with timestamps and session markers
- [ ] Widget for home screen status display

---

## Stage 4 — Platform

- [ ] Wear OS companion — wrist status display and toggle
- [ ] Android Auto integration
- [ ] Headless mode — run without MainActivity ever opened (pure background service)
- [ ] Export session transcripts as text files

---

## Technical Debt

- [ ] Add unit tests for MemoryExtractor JSON parsing
- [ ] Add unit tests for OpenBrainClient retry logic
- [ ] Add integration test for AudioPipeline listener dispatch
- [ ] Replace `apply plugin:` syntax with `plugins {}` block in all build.gradle files (Gradle 9.0 deprecation warning)
- [ ] Move to Gradle version catalog (`libs.versions.toml`) for dependency management
