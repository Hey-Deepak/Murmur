# Murmur Rust AI Pipeline Library: Bootstrap Prompt

Copy everything below the line into this project as context.

---

## Project: Murmur (Rust Library)

**Source project location**: `/Users/deepak/AndroidStudioProjects/Test/Murmur` (the Android app this library is extracted from)
**This Rust library location**: `/Users/deepak/RustroverProjects/Murmur-rs`

Murmur-rs is a Rust library crate (`cdylib`) that extracts the AI analysis pipeline from the Murmur Android app into a reusable, cross-platform native library. Multiple Android apps (and potentially desktop tools) will consume this library via JNI (Android) or FFI (desktop).

## Why This Exists

Murmur is a background audio recorder for Android that transcribes audio (Whisper), identifies speakers (sherpa-onnx diarization), and analyzes conversations (sentiment, topics, activity detection, behavioral tags). Currently, all of this logic lives inside the Android app as Kotlin code + a separate Kotlin/Ktor bridge server running in Termux.

The goal of murmur-rs is to **move the core pipeline into a single Rust library** so that:
1. Multiple apps (Murmur, Buddy, future apps) can share the same pipeline
2. Native performance for audio processing, embedding math, and ML inference
3. No more Termux bridge dependency — the library calls AI APIs directly
4. Cross-platform: Android (`.so` via NDK), macOS/Linux (`.dylib`/`.so`), potentially WASM later

## Crate Type

```toml
[lib]
crate-type = ["cdylib"]  # For JNI/FFI shared library
```

## Architecture to Implement

### The Pipeline (port from Kotlin)

The current Murmur pipeline has these stages, all of which murmur-rs should handle:

```
Input: Raw audio file (M4A/WAV/OGG)
  │
  ├─ Stage 1: Audio Decode → PCM (16kHz, mono, 16-bit LE)
  │     Currently: Android MediaCodec (platform-specific)
  │     murmur-rs: Use symphonia or ffmpeg-sys for decoding
  │     Output: Vec<i16> or Vec<f32> at 16kHz mono
  │
  ├─ Stage 2: Speaker Diarization + Fingerprinting (CORE — NOT optional)
  │     Currently: sherpa-onnx (ONNX Runtime) — Pyannote segmentation + speaker embedding
  │     murmur-rs: Use ort (ONNX Runtime Rust bindings) with same models
  │     Models: pyannote-segmentation-3.0.onnx + 3dspeaker_speech_eres2net_base_200k.onnx
  │     Output: Vec<DiarizedSegment { start_ms, end_ms, speaker_index, embedding }>
  │
  │     THIS IS A CRITICAL FEATURE — see "Speaker Fingerprinting System" section below
  │
  ├─ Stage 3: Speech-to-Text (Whisper)
  │     Currently: WhisperKit (Apple's framework, iOS/Android via JNI)
  │     murmur-rs: Use whisper-rs (whisper.cpp bindings) or candle-whisper
  │     Output: String (raw transcript)
  │
  ├─ Stage 4: Transcript Cleanup
  │     Currently: Claude API via Termux bridge, fallback to regex post-processing
  │     murmur-rs: Direct HTTP to Claude API (or any LLM API), fallback to rule-based cleanup
  │     Output: String (cleaned transcript)
  │
  └─ Stage 5: NLP Analysis
        Currently: Claude API via Termux bridge, fallback to keyword extraction
        murmur-rs: Direct HTTP to Claude API, fallback to on-device analysis
        Output: AnalysisResult (see data structures below)
```

### Data Structures to Port

These are the core types from Murmur that murmur-rs needs to define as Rust structs:

```rust
// From AnalysisPipeline.kt
pub struct AnalysisResult {
    pub chunk_id: i64,
    pub text: String,
    pub sentiment: String,           // "positive"|"negative"|"neutral"|"anxious"|"frustrated"|"confident"|"hesitant"|"excited"
    pub sentiment_score: f32,        // 0.0–1.0
    pub keywords: String,            // JSON: {"summary":"...","tags":["..."]}
    pub model_used: String,
    pub activity_type: Option<String>,      // "eating"|"meeting"|"working"|"commuting"|"idle"|"phone_call"|"casual_chat"|"solo"
    pub activity_confidence: Option<f32>,
    pub activity_sub_type: Option<String>,
    pub speakers: Vec<SpeakerResult>,
    pub topics: Vec<TopicResult>,
    pub behavioral_tags: Vec<String>,
    pub key_moment: Option<String>,
    pub diarized_speakers: Vec<DiarizedSpeakerInfo>,
}

pub struct SpeakerResult {
    pub label: String,
    pub speaking_ratio: f32,
    pub turn_count: i32,
    pub role: Option<String>,           // "dominant"|"listener"|"equal"
    pub emotional_state: Option<String>, // "calm"|"engaged"|"frustrated"|"excited"|"hesitant"
    pub matched_profile_id: Option<i64>,
}

pub struct TopicResult {
    pub name: String,
    pub relevance: f32,
    pub category: Option<String>,       // "work"|"personal"|"health"|"finance"|"entertainment"|"education"
    pub key_points: Vec<String>,
}

pub struct DiarizedSpeakerInfo {
    pub speaker_index: i32,
    pub total_ms: i64,
    pub ratio: f32,
    pub matched_profile_id: Option<i64>,
    pub matched_profile_name: Option<String>,
    pub match_confidence: Option<f32>,
    pub embedding: Option<Vec<f32>>,
    pub timings: Vec<(i64, i64)>,       // (start_ms, end_ms) pairs
}

pub struct DiarizedSegment {
    pub start_ms: i64,
    pub end_ms: i64,
    pub speaker_index: i32,
    pub embedding: Vec<f32>,
}

pub struct DiarizationResult {
    pub segments: Vec<DiarizedSegment>,
    pub speaker_count: i32,
    pub speaker_embeddings: HashMap<i32, Vec<f32>>,
}

pub struct PcmAudio {
    pub data: Vec<i16>,    // or Vec<f32> depending on downstream needs
    pub sample_rate: u32,  // always 16000 after resampling
    pub channels: u16,     // always 1 (mono)
}
```

### JNI Interface Design

The library exposes C-compatible functions for Android consumption:

```rust
// Core lifecycle
#[no_mangle]
pub extern "C" fn murmur_pipeline_create(config_json: *const c_char) -> *mut Pipeline;
#[no_mangle]
pub extern "C" fn murmur_pipeline_destroy(pipeline: *mut Pipeline);

// Initialize models (downloads if needed, returns progress via callback)
#[no_mangle]
pub extern "C" fn murmur_pipeline_init(
    pipeline: *mut Pipeline,
    progress_callback: extern "C" fn(f32),
) -> i32;  // 0 = success

// Process a chunk
#[no_mangle]
pub extern "C" fn murmur_pipeline_process(
    pipeline: *mut Pipeline,
    chunk_id: i64,
    file_path: *const c_char,
) -> *mut c_char;  // Returns JSON string, caller must free

// Free a returned string
#[no_mangle]
pub extern "C" fn murmur_string_free(s: *mut c_char);

// Speaker embedding extraction (for profile enrollment)
#[no_mangle]
pub extern "C" fn murmur_extract_embedding(
    pipeline: *mut Pipeline,
    pcm_data: *const i16,
    pcm_len: usize,
    sample_rate: i32,
) -> *mut c_char;  // Returns JSON float array string

// Health/status
#[no_mangle]
pub extern "C" fn murmur_is_ready(pipeline: *mut Pipeline) -> bool;
```

### Suggested Rust Crate Dependencies

```toml
[dependencies]
# Audio decoding
symphonia = { version = "0.5", features = ["all"] }  # or rodio

# Whisper STT
whisper-rs = "0.12"  # bindings to whisper.cpp

# ONNX Runtime for diarization models
ort = "2"  # ONNX Runtime bindings

# Async runtime (for subprocess + pipeline orchestration)
tokio = { version = "1", features = ["rt-multi-thread", "macros", "process"] }

# Serialization
serde = { version = "1", features = ["derive"] }
serde_json = "1"

# JNI support (optional, only for Android builds)
jni = { version = "0.21", optional = true }

# Logging
log = "0.4"
env_logger = "0.11"
android_logger = { version = "0.14", optional = true }

# Math utilities
ndarray = "0.16"  # for cosine similarity, L2 norm

[features]
default = []
android = ["jni", "android_logger"]

[lib]
crate-type = ["cdylib"]
```

### Project Structure

```
murmur-rs/
├── Cargo.toml
├── src/
│   ├── lib.rs              # FFI/JNI exports, crate root
│   ├── pipeline.rs         # Main Pipeline struct, orchestrates stages
│   ├── audio/
│   │   ├── mod.rs
│   │   ├── decoder.rs      # Audio file → PCM conversion (symphonia)
│   │   └── resample.rs     # Resampling to 16kHz mono
│   ├── diarization/
│   │   ├── mod.rs
│   │   ├── diarizer.rs     # Speaker diarization via ONNX
│   │   └── embedding.rs    # Speaker embedding extraction + cosine similarity
│   ├── stt/
│   │   ├── mod.rs
│   │   └── whisper.rs      # Whisper transcription
│   ├── nlp/
│   │   ├── mod.rs
│   │   ├── claude.rs       # Claude CLI subprocess wrapper (replaces Termux bridge server)
│   │   ├── cleanup.rs      # Transcript cleanup (Claude + rule-based fallback)
│   │   └── analyzer.rs     # Rich analysis (Claude + on-device fallback)
│   ├── models/
│   │   ├── mod.rs
│   │   └── manager.rs      # Model download + caching
│   └── types.rs            # All shared data structures
├── models/                 # Downloaded ONNX models go here at runtime
└── android/                # Android-specific build scripts
    └── build.sh            # Cross-compile for aarch64-linux-android
```

### Claude Integration (via CLI subprocess — NO API key needed)

**Assumption**: Every user has Claude Code (`claude` CLI) installed in Termux on their Android device via their Claude Max subscription. There is NO Claude API key. The Rust library calls Claude by shelling out to the `claude` CLI directly.

This **replaces the entire Termux bridge server** (the Ktor server is no longer needed). The Rust library does what the bridge did — subprocess call to `claude -p "prompt"` — plus handles all native compute (audio, STT, diarization) itself.

```rust
// In nlp/claude.rs
use std::process::Command;

pub struct ClaudeCli {
    /// Path to claude binary. Auto-detected from known Termux paths:
    /// - /data/data/com.termux/files/usr/bin/claude
    /// - /data/data/com.termux/files/home/.npm-global/bin/claude
    /// - /data/data/com.termux/files/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude
    /// Falls back to `which claude`.
    claude_path: String,
    timeout_secs: u64,  // default 60s
}

impl ClaudeCli {
    /// Find claude binary on the system
    pub fn detect() -> Result<Self>;

    /// Check if claude CLI is available
    pub fn is_available(&self) -> bool;

    /// Run claude with a prompt, return raw output
    fn run(&self, prompt: &str) -> Result<String> {
        // Equivalent to current bridge's runClaude():
        // 1. Spawn: claude -p "<prompt>"
        // 2. Remove CLAUDECODE/CLAUDE_CODE_SSE_PORT env vars to avoid nesting issues
        // 3. Close stdin immediately
        // 4. Read stdout with timeout
        // 5. Return output string
    }

    /// Clean up raw STT transcript
    pub fn cleanup(&self, raw_transcript: &str) -> Result<String>;

    /// Rich behavioral analysis — returns structured JSON
    pub fn analyze_rich(&self, transcript: &str, speaker_context: Option<&str>) -> Result<AnalysisResult>;

    /// Generate daily insight summary
    pub fn generate_daily_insight(&self, request: &DailyInsightRequest) -> Result<DailyInsightResponse>;

    /// Detect links between conversation chunks
    pub fn detect_links(&self, current: &ChunkContext, candidates: &[ChunkContext]) -> Result<Vec<DetectedLink>>;

    /// Generate behavioral predictions from patterns
    pub fn predict(&self, patterns: &str, date: &str, time: &str) -> Result<Vec<Prediction>>;
}
```

**Important env vars to strip** before spawning claude (prevents nested invocation errors):
- `CLAUDECODE`
- `CLAUDE_CODE_SSE_PORT`
- `CLAUDE_CODE_ENTRYPOINT`

### Claude CLI Auth Lifecycle

The `claude` CLI requires authentication via Claude Max subscription. The Rust library cannot handle login itself (it's a `.so` with no UI), so it communicates auth status back to the Android app via error codes.

**Flow:**

```
Rust lib: murmur_pipeline_process()
  │
  ├─ Tries: claude -p "prompt"
  │
  ├─ Exit code 0 → success, parse output
  │
  ├─ Exit code != 0 OR stderr contains "not authenticated" / "login" / "unauthorized"
  │     │
  │     └─ Return error with status code: CLAUDE_AUTH_REQUIRED (= 2)
  │
  └─ Binary not found
        └─ Return error with status code: CLAUDE_NOT_FOUND (= 3)
```

**Error codes returned by FFI functions:**
```rust
pub const MURMUR_OK: i32 = 0;
pub const MURMUR_ERROR: i32 = 1;
pub const MURMUR_CLAUDE_AUTH_REQUIRED: i32 = 2;  // Need user to open Termux & run `claude login`
pub const MURMUR_CLAUDE_NOT_FOUND: i32 = 3;      // claude binary not installed in Termux
```

**Android app side** (Kotlin — already has `TermuxBridgeManager`):
When the Rust library returns `CLAUDE_AUTH_REQUIRED`:
1. Show a notification/dialog: "Claude authentication needed"
2. On tap → launch Termux via `TermuxBridgeManager` with command: `claude login`
3. User authenticates in Termux (one-time OAuth flow)
4. App retries the pipeline

The existing `TermuxBridgeManager` at the source project already handles launching Termux and running commands via `RunCommandService` intent. This pattern stays — just the command changes from "start bridge server" to "claude login" when auth is needed.

**Session persistence**: Once authenticated, `claude` CLI stores credentials in `~/.claude/` inside Termux. They persist across reboots. Re-authentication is rarely needed (only on token expiry, which is infrequent with Max subscription).

**Fallback**: When `claude` CLI is not found or fails, the pipeline falls back to rule-based processing:
- Transcript cleanup: regex-based (remove `[BLANK_AUDIO]`, fix capitalization, basic punctuation)
- Analysis: keyword extraction only, sentiment defaults to "neutral"

The prompts to use are defined in the source project at:
`/Users/deepak/AndroidStudioProjects/Test/Murmur/claude-bridge/src/main/kotlin/com/dc/murmur/bridge/Main.kt`

Key prompts to port into Rust string constants:

**Cleanup prompt** — cleans raw STT output:
- Add punctuation, fix capitalization
- Preserve Hindi/Hinglish words in Latin script
- Remove filler artifacts like "[BLANK_AUDIO]"
- Preserve hesitations (um, uh, hmm) for behavioral analysis
- Return plain text only, no markdown

**Rich analysis prompt** — full behavioral analysis returning JSON with:
- Sentiment + score
- Activity detection (eating, meeting, working, commuting, etc.)
- Speaker analysis (roles, emotional states, speaking ratios)
- Topic extraction (name, relevance, category, key points)
- Behavioral tags (rapid speech, code-switching, interrupting, etc.)
- Key moment detection

**Daily insight prompt** — summarize a full day from aggregated data

**Link detection prompt** — find relationships between conversation chunks

**Prediction prompt** — generate behavioral predictions from observed patterns

### Android Cross-Compilation

```bash
# Install Android NDK targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Build with cargo-ndk
cargo ndk -t arm64-v8a -t armeabi-v7a -o ./jniLibs build --release --features android
```

The output `.so` files go into the Android app's `app/src/main/jniLibs/` directory.

### Config Passed from App

The Pipeline is created with a JSON config:

```json
{
  "claude_binary_path": null,
  "whisper_model": "base",
  "language": "en",
  "diarization_threshold": 0.45,
  "speaker_match_threshold": 0.6,
  "models_dir": "/data/data/com.dc.murmur/files/models",
  "max_audio_duration_sec": 300,
  "fallback_to_local": true
}
```

### Speaker Fingerprinting System (CRITICAL)

Speaker identification is a **core feature**, not optional. The system must:

1. **Diarize every chunk** — Determine how many speakers are in each audio chunk and segment the audio by speaker (who speaks when).

2. **Extract voice embeddings** — For each detected speaker, extract a 192/256-dimensional voice embedding vector using the ONNX embedding model (3dspeaker ERes2Net). This is the speaker's "fingerprint."

3. **Match against enrolled profiles** — Compare each extracted embedding against a database of known speaker profiles using cosine similarity:
   - L2-normalize all embeddings before comparison
   - Match threshold: ~0.6 cosine similarity
   - Return the best match with confidence score
   - If no match above threshold → "unknown speaker"

4. **Speaker enrollment (tagging)** — Users can "tag" an unknown speaker with a name. The pipeline must support:
   ```rust
   // Enroll a new speaker profile
   pub fn enroll_speaker(
       &self,
       name: &str,
       pcm_data: &[i16],
       sample_rate: i32,
   ) -> Result<SpeakerProfile>;

   // Match a speaker embedding against all enrolled profiles
   pub fn match_speaker(
       &self,
       embedding: &[f32],
       profiles: &[SpeakerProfile],
   ) -> Option<SpeakerMatch>;
   ```

5. **Cross-chunk speaker tracking** — The same person should be recognized across different recording chunks, different days, different contexts. The embedding is the persistent identity.

6. **Speaker profile storage** — Embeddings are stored as base64-encoded float arrays in the app's database. The Rust library handles:
   - Embedding extraction from raw PCM
   - Cosine similarity computation
   - L2 normalization
   - The app (Kotlin side) handles database storage

```rust
pub struct SpeakerProfile {
    pub id: i64,
    pub name: String,
    pub embedding: Vec<f32>,        // L2-normalized, 192 or 256 dims
    pub total_interaction_ms: i64,
    pub interaction_count: i32,
    pub last_seen_at: i64,          // unix timestamp ms
}

pub struct SpeakerMatch {
    pub profile: SpeakerProfile,
    pub confidence: f32,            // cosine similarity score
}

// Utility functions the library MUST expose via FFI:
pub fn cosine_similarity(a: &[f32], b: &[f32]) -> f32;
pub fn l2_normalize(embedding: &mut [f32]);
pub fn embedding_to_base64(embedding: &[f32]) -> String;
pub fn base64_to_embedding(b64: &str) -> Vec<f32>;
```

The diarization pipeline flow per chunk:
```
Audio chunk (PCM)
  │
  ├─ Pyannote segmentation → time segments with speaker labels (Speaker 0, 1, 2...)
  │
  ├─ For each speaker: extract audio slices → compute voice embedding
  │
  ├─ For each embedding: compare against all enrolled profiles
  │     ├─ Match found (cosine > 0.6) → tag segment with profile name + confidence
  │     └─ No match → tag as "Unknown Speaker N"
  │
  └─ Output: DiarizationResult with matched identities + embeddings for potential enrollment
```

### Key Behavioral Notes

1. **Hindi-English code-switching (Hinglish)** — The user speaks Hinglish. All NLP prompts must handle this.
2. **Fallback is critical** — When `claude` CLI is unavailable or fails, the pipeline must still work with on-device/rule-based analysis.
3. **Speaker diarization is MANDATORY** — Every audio chunk must be diarized. Speaker fingerprinting/matching runs on every chunk to track who is speaking.
4. **Audio format** — Input is typically M4A (AAC encoded). Must decode to 16kHz mono 16-bit PCM.
5. **Chunk-based processing** — Audio is recorded in chunks (typically 30s–2min). Each chunk is processed independently.
6. **Speaker persistence** — Speaker identities must persist across chunks, sessions, and days. The voice embedding is the stable identifier.

## Starting Point

Begin by:
1. Setting up the Cargo.toml with the dependencies above
2. Defining all types in `src/types.rs`
3. Implementing audio decoding in `src/audio/decoder.rs` (use symphonia)
4. Implementing the Pipeline struct in `src/pipeline.rs` with the stage orchestration
5. Adding FFI exports in `src/lib.rs`
6. Implementing Claude CLI subprocess wrapper in `src/nlp/claude.rs`

Leave Whisper STT as a trait-based abstraction initially — it can be filled in once the core pipeline structure compiles and the FFI layer works. Speaker diarization and fingerprinting should be implemented early as it is a core feature.
