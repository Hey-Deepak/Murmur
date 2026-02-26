# Murmur — Project Overview

> An always-listening Android app that records audio continuously, observes behavioral patterns, and provides predictive guidance through on-device AI analysis.

## Vision

Murmur silently records your day in the background, then analyzes conversations and audio to understand your routines, moods, and habits. It learns when you order food, who you talk to, what stresses you out — and proactively nudges you with helpful suggestions.

## Core Idea

- **Record all day** → Background audio capture in 15-min AAC chunks
- **Analyze on schedule** → User picks when analysis runs (not just night)
- **Understand patterns** → Speech-to-text → sentiment → keyword extraction → daily summary
- **Predict and nudge** → "You usually order food at 1 PM" / "Mood dipped after 3 PM calls"

## Target Device

- **OnePlus Nord CE 3** (primary development target)
- Snapdragon 782G, Adreno 642L GPU
- 8/12 GB LPDDR4X RAM
- 5000 mAh battery + 80W charging
- Android 13+ (OxygenOS 13.1+)

## Tech Stack

| Component | Choice | Reason |
|-----------|--------|--------|
| Language | Kotlin | Modern Android standard |
| UI | Jetpack Compose + Material 3 | Declarative, M3 design system |
| DI | Koin 3.5.6 | Lightweight, no annotation processing, pure Kotlin DSL |
| Database | Room + KSP | Compile-time SQL verification, coroutine support |
| Background | Foreground Service (MICROPHONE) | Required for continuous recording |
| Scheduling | WorkManager | Reliable scheduling for analysis tasks |
| Settings | DataStore Preferences | Modern replacement for SharedPreferences |
| Charts | Vico 1.13.1 | Compose-native charting with M3 support |
| Min SDK | API 28 (Android 9) | Foreground service types, modern APIs |
| Package | com.dc.murmur | — |

## Why NOT Hilt?

Koin was chosen over Hilt because:
- No annotation processing overhead (faster builds)
- Pure Kotlin DSL — easier to read and debug
- Lighter footprint for a single-developer project
- No kapt/KSP conflict with Room (Room already uses KSP)

## Project Structure

```
com.dc.murmur/
├── MurmurApplication.kt          # Koin init + notification channels
├── MainActivity.kt                # Single activity, Compose host
├── core/
│   ├── constants/AppConstants.kt  # All app-wide constants
│   └── util/
│       ├── StorageUtil.kt         # File management, paths, cleanup
│       ├── BatteryUtil.kt         # Battery level, charging state
│       └── NotificationUtil.kt    # Foreground + progress notifications
├── data/
│   ├── local/
│   │   ├── MurmurDatabase.kt     # Room database (v1)
│   │   ├── entity/
│   │   │   ├── RecordingChunkEntity.kt
│   │   │   ├── SessionEntity.kt
│   │   │   ├── BatteryLogEntity.kt
│   │   │   └── TranscriptionEntity.kt
│   │   └── dao/
│   │       ├── RecordingChunkDao.kt
│   │       ├── SessionDao.kt
│   │       ├── BatteryLogDao.kt
│   │       └── TranscriptionDao.kt
│   └── repository/
│       ├── RecordingRepository.kt
│       ├── BatteryRepository.kt
│       ├── SettingsRepository.kt
│       └── AnalysisRepository.kt
├── service/
│   ├── RecordingService.kt        # Core foreground service
│   ├── CallStateReceiver.kt       # Phone call detection
│   ├── BootReceiver.kt            # Auto-start on reboot
│   └── BatteryWorker.kt           # Periodic battery logging
├── ai/
│   ├── AnalysisPipeline.kt        # decode → transcribe → sentiment → keywords
│   ├── AnalysisWorker.kt          # WorkManager worker, battery guard, progress
│   ├── AnalysisState.kt           # AnalysisStateHolder + AnalysisUiState (StateFlow)
│   ├── AudioDecoder.kt            # M4A → PCM float array (MediaCodec)
│   ├── ModelManager.kt            # Model download, caching, path resolution
│   ├── stt/
│   │   └── VoskTranscriber.kt     # Vosk speech-to-text (Indian English)
│   └── nlp/
│       ├── SentimentAnalyzer.kt   # MobileBERT TFLite (positive/negative/neutral)
│       └── KeywordExtractor.kt    # Rule-based + TF-IDF, JSON output
├── feature/
│   ├── permission/
│   │   └── PermissionScreen.kt    # Runtime permission flow + battery opt dialog
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── recordings/
│   │   ├── RecordingsScreen.kt
│   │   ├── RecordingsViewModel.kt
│   │   └── AudioPlayer.kt
│   └── stats/
│       ├── StatsScreen.kt
│       └── StatsViewModel.kt
├── navigation/
│   └── NavGraph.kt
├── di/
│   └── AppModule.kt              # All Koin modules (database/util/ai/repository/viewModel)
└── ui/
    ├── components/
    │   ├── TranscriptionCard.kt   # Transcript + sentiment + keyword chips
    │   └── AnalyzeButton.kt       # Triggers AnalysisWorker with progress state
    └── theme/                     # Material 3 theme
```

## Storage Layout (on device)

```
Documents/Murmur/
├── recordings/
│   ├── 2026-02-26/
│   │   ├── chunk_2026-02-26_08-00-00.m4a
│   │   ├── chunk_2026-02-26_08-15-00.m4a
│   │   └── ...
│   └── 2026-02-27/
├── logs/
│   └── app_log.txt
└── metadata/
    └── sessions.json
```

## Screens

1. **Home** — Recording toggle (FAB), live timer, waveform animation, today's stats (chunks/duration/storage), recent activity list, battery chart
2. **Recordings** — Browse by date, search, inline playback, interruption badges
3. **Stats** — Battery drain chart, weekly hours chart, storage ring, settings
4. **Settings** — Analysis scheduler (fixed time / on charging / manual), recording config, storage management

## Build & Deploy

Built and deployed via **Buddy** (mobile-first Android dev tool):
- `buddy_write_file` → Write code
- `buddy_gradle_build` → Compile
- `buddy_adb_deploy` → Install APK
- `buddy_adb_launch` → Launch on device
- `buddy_logcat` → Debug

## Author

Deepak — SDE-2, Android SDK Engineer
