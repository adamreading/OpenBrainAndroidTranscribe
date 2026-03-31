# OpenBrain Ambient Android — TODO

## Stage 4 Ideas

### Wake Word Improvements
- [ ] Train custom OpenWakeWord ONNX model for "Hey Adam" / "Go to Sleep"
- [ ] Integrate ONNX Runtime for direct PCM wake word detection (replace SpeechRecognizer fallback)
- [ ] Add configurable wake word sensitivity threshold
- [ ] Support multiple wake word models simultaneously

### Whisper Improvements
- [ ] Streaming Whisper — word-by-word real-time display
- [ ] Voice Activity Detection (VAD) to skip silent chunks
- [ ] Dynamic model switching based on battery level (tiny when low, base when charging)
- [ ] Whisper large-v3 turbo support for improved accuracy

### LLM / Memory Extraction
- [ ] Streaming LLM output for real-time extraction feedback
- [ ] Structured output mode (JSON grammar constraints in llama.cpp)
- [ ] Context window management for long transcripts (sliding window + summary)
- [ ] Customizable extraction prompts via settings UI

### Battery & Performance
- [ ] Battery usage monitoring dashboard in AdminActivity
- [ ] Adaptive processing frequency based on battery state
- [ ] CPU thermal throttling detection and backoff
- [ ] Benchmark mode for comparing model performance on device

### Sync & Backend
- [ ] Bidirectional sync (pull memories from Supabase)
- [ ] Conflict resolution for offline/online edits
- [ ] End-to-end encryption for synced memories
- [ ] Support alternative backends (self-hosted, local SQLite export)

### UI / UX
- [ ] Material Design 3 / dynamic color theme
- [ ] Notification actions (toggle active, view transcript)
- [ ] Widget for quick status view and toggle
- [ ] Memory browser — view and search extracted memories on device
- [ ] Transcript history with timestamps and session markers

### Platform
- [ ] Wear OS companion app for wrist-based status/control
- [ ] Android Auto integration for in-car ambient listening
- [ ] Tile for Quick Settings panel
