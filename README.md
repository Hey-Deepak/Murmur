# Murmur

**A passive life intelligence system for Android.**

Murmur silently records your day in the background, transcribes it on-device with Whisper, and analyzes it with Claude to reconstruct what actually happened — what you did, who you talked to, what you discussed, and what patterns repeat across your days. No logging, no journaling, no cloud.

> Your phone already hears everything. Murmur makes it understand.

See [VISION.md](VISION.md) for the full idea.

## What it does

- **Record** — Foreground service captures ambient audio in 15-minute AAC chunks (~15 MB/hour). Pauses during phone calls, survives reboots, respects battery.
- **Transcribe** — On-device speech-to-text via WhisperKit (LiteRT-optimized Whisper). Audio never leaves the device.
- **Analyze** — Claude (running locally via a Termux bridge) extracts activities, speakers, topics, sentiment, and behavioral tags per chunk. Falls back to on-device MobileBERT + rule-based extraction when the bridge is unavailable.
- **Understand people** — Speaker diarization detects distinct voices; you tag them once ("Voice A" → "Rahul") and each person accumulates a profile: time spent together, common topics, interaction dynamics.
- **Connect time** — Chunks are linked across days by person, topic, and time slot, building a continuous narrative instead of isolated summaries.
- **Predict** — Pattern-based predictions and nudges ("You ate at 1:15 PM the last 4 days — it's 1:10 PM now").

## Architecture

```
Microphone
  │
  ▼
RecordingService (foreground, 15-min AAC chunks)
  │
  ▼
AnalysisWorker (WorkManager — scheduled, on charge, or manual)
  │
  ├─ AudioDecoder      M4A → PCM
  ├─ WhisperKit        on-device STT
  ├─ Claude Bridge     Termux, localhost:8735
  │    ├─ /cleanup        transcript post-processing
  │    ├─ /analyze        activity, speakers, topics, sentiment
  │    ├─ /link           cross-chunk conversation linking
  │    ├─ /predict        pattern-based predictions
  │    └─ /daily-insight  aggregated daily narrative
  ├─ [Fallback]        MobileBERT + keyword extraction
  │
  ▼
Room DB (12 entities: chunks, transcriptions, voice profiles,
         topics, activities, links, insights, predictions)
  │
  ▼
UI — Home · Recordings · Insights · People · Stats
```

### Modules

| Module | Description |
|---|---|
| `app` | The Android app (Kotlin, Jetpack Compose) |
| `claude-bridge` | Ktor HTTP server that runs inside Termux and wraps the Claude CLI, exposed to the app on `127.0.0.1:8735` |

### Rust pipeline (experimental)

The `feat/murmur-rs` branch integrates [Murmur-rs](https://github.com/Hey-Deepak/Murmur-rs), a native Rust rewrite of the analysis pipeline (symphonia + whisper-rs + ONNX diarization) consumed over JNI, with an in-app benchmark screen comparing it against the Kotlin pipeline.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single-activity
- **Room** (KSP) for storage, **DataStore** for settings
- **Koin** for DI, **WorkManager** for scheduling
- **WhisperKit** + QNN/LiteRT delegates for on-device STT
- **Vico** for charts
- Min SDK 28, target SDK 35

## Getting started

### Build the app

```bash
./gradlew :app:assembleDebug
```

Open in Android Studio and run on a device (a real device is required — the whole point is the microphone and battery behavior). Grant microphone and notification permissions on first launch.

### Set up the Claude Bridge (optional, recommended)

The app works standalone with the on-device fallback, but the rich analysis comes from Claude via the bridge:

1. Install [Termux](https://termux.dev) on the device and set up Node.js + the Claude CLI inside it.
2. Deploy the bridge server from the `claude-bridge` module into Termux (see `claude-bridge/scripts/`).
3. Start the bridge — it listens on `127.0.0.1:8735` (localhost only, no auth).
4. The app auto-detects the bridge and switches from the on-device fallback to Claude analysis.

## Privacy

Everything runs on-device or through the localhost bridge. No cloud APIs, no telemetry, no audio or transcripts leaving the phone. Recordings are disposable raw material — they get consumed by the pipeline and turned into local structured data.

## Docs

- [VISION.md](VISION.md) — the idea, in depth
- [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) — project structure and tech decisions
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architecture details
- [docs/AI_STRATEGY.md](docs/AI_STRATEGY.md) — analysis pipeline strategy
- [docs/ANALYSIS_SCHEDULER.md](docs/ANALYSIS_SCHEDULER.md) — scheduling and battery guards

## Status

Personal experimental project. Core pipeline (record → transcribe → analyze → insights) is implemented and running on-device; speaker matching via voice embeddings, long-duration battery profiling, and the Rust pipeline migration are in progress.
