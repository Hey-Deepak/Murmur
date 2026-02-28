# Murmur — Project Overview

> A passive life intelligence system that records audio continuously, analyzes behavioral patterns through Claude AI, identifies people by voice, tracks topics and activities, and provides predictive guidance — all on-device.

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
├── MurmurApplication.kt             # Koin init + notification channels
├── MainActivity.kt                   # Single activity, Compose host
├── core/
│   ├── constants/AppConstants.kt     # All app-wide constants
│   └── util/
│       ├── StorageUtil.kt            # File management, paths, cleanup
│       ├── BatteryUtil.kt            # Battery level, charging state
│       ├── NotificationUtil.kt       # Foreground + progress + prediction notifications
│       └── TermuxBridgeManager.kt    # Termux integration for Claude Bridge
├── data/
│   ├── local/
│   │   ├── MurmurDatabase.kt        # Room database (v3, 12 entities, 11 DAOs)
│   │   ├── entity/
│   │   │   ├── RecordingChunkEntity.kt
│   │   │   ├── SessionEntity.kt
│   │   │   ├── BatteryLogEntity.kt
│   │   │   ├── TranscriptionEntity.kt     # Extended with rich analysis fields
│   │   │   ├── TranscriptionWithChunk.kt  # Join projection
│   │   │   ├── ActivityEntity.kt          # Activity detection per chunk
│   │   │   ├── VoiceProfileEntity.kt      # Speaker identity profiles
│   │   │   ├── SpeakerSegmentEntity.kt    # Speaker-chunk junction
│   │   │   ├── TopicEntity.kt             # Extracted topics
│   │   │   ├── ChunkTopicEntity.kt        # Chunk-topic junction
│   │   │   ├── ConversationLinkEntity.kt  # Cross-chunk links
│   │   │   ├── DailyInsightEntity.kt      # Aggregated daily insights
│   │   │   └── PredictionEntity.kt        # Pattern-based predictions
│   │   └── dao/
│   │       ├── RecordingChunkDao.kt
│   │       ├── SessionDao.kt
│   │       ├── BatteryLogDao.kt
│   │       ├── TranscriptionDao.kt
│   │       ├── ActivityDao.kt             # + ActivityTimeBreakdown
│   │       ├── VoiceProfileDao.kt
│   │       ├── SpeakerSegmentDao.kt       # + SpeakerSegmentWithDate
│   │       ├── TopicDao.kt
│   │       ├── ConversationLinkDao.kt
│   │       ├── DailyInsightDao.kt
│   │       └── PredictionDao.kt
│   └── repository/
│       ├── RecordingRepository.kt
│       ├── BatteryRepository.kt
│       ├── SettingsRepository.kt
│       ├── AnalysisRepository.kt     # Extended with saveFullAnalysis
│       ├── InsightsRepository.kt     # Activities, insights, topics, links, predictions
│       └── PeopleRepository.kt      # Voice profiles, speaker segments
├── service/
│   ├── RecordingService.kt           # Core foreground service
│   ├── CallStateReceiver.kt          # Phone call detection
│   ├── BootReceiver.kt               # Auto-start on reboot
│   └── BatteryWorker.kt              # Periodic battery logging
├── ai/
│   ├── AnalysisPipeline.kt           # decode → STT → rich analysis (Claude/fallback)
│   ├── AnalysisWorker.kt             # WorkManager worker + linking + insights + predictions
│   ├── AnalysisState.kt              # AnalysisStateHolder + AnalysisUiState (StateFlow)
│   ├── AudioDecoder.kt               # M4A → PCM float array (MediaCodec)
│   ├── ModelManager.kt               # Model download, caching, path resolution
│   ├── SpeechModelCatalog.kt         # Available WhisperKit model definitions
│   ├── ModelDownloadState.kt         # Download progress tracking
│   ├── BridgeStatus.kt               # BridgeStatusHolder + BridgeUiState
│   ├── InsightGenerator.kt           # Daily insight aggregation (Claude + local)
│   ├── ConversationLinker.kt         # Cross-chunk conversation linking
│   ├── PredictionEngine.kt           # Pattern-based predictions
│   ├── stt/
│   │   └── WhisperKitTranscriber.kt  # WhisperKit on-device STT
│   └── nlp/
│       ├── ClaudeCodeAnalyzer.kt     # HTTP client to Claude Bridge
│       ├── KeywordExtractor.kt       # Rule-based extraction (fallback)
│       └── TranscriptPostProcessor.kt # Transcript cleanup (fallback)
├── feature/
│   ├── permission/
│   │   └── PermissionScreen.kt       # Runtime permission flow
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── recordings/
│   │   ├── RecordingsScreen.kt
│   │   ├── RecordingsViewModel.kt
│   │   └── AudioPlayer.kt
│   ├── transcriptions/
│   │   ├── TranscriptionsScreen.kt
│   │   └── TranscriptionsViewModel.kt
│   ├── insights/
│   │   ├── InsightsScreen.kt         # Daily/Weekly/Transcripts tabs
│   │   └── InsightsViewModel.kt
│   ├── people/
│   │   ├── PeopleScreen.kt           # Voice profiles list + detail
│   │   └── PeopleViewModel.kt
│   └── stats/
│       ├── StatsScreen.kt            # Stats + settings + trends
│       └── StatsViewModel.kt
├── navigation/
│   └── NavGraph.kt                   # 5-tab navigation
├── di/
│   └── AppModule.kt                  # All Koin modules
└── ui/
    ├── components/
    │   ├── TranscriptionCard.kt      # Transcript + sentiment + keywords
    │   ├── TranscriptionCardV2.kt    # Enhanced with activity/topics/tags
    │   ├── AnalyzeButton.kt          # AnalysisWorker trigger with progress
    │   ├── TimelineBlock.kt          # Activity timeline card
    │   ├── ActivityBreakdownChart.kt  # Stacked bar chart
    │   ├── ActivityTrendChart.kt      # 7-day stacked columns
    │   ├── SentimentTrendChart.kt     # 30-day line chart
    │   ├── InsightCard.kt            # Rich analysis insight card
    │   ├── PredictionCard.kt         # Prediction display with dismiss
    │   ├── PersonCard.kt             # Voice profile card
    │   └── VoiceTagDialog.kt         # Voice naming dialog
    └── theme/
        └── Color.kt                  # M3 theme + activity/prediction colors

claude-bridge/                        # Separate Kotlin module
└── src/main/kotlin/com/dc/murmur/bridge/
    └── Main.kt                       # Ktor server wrapping Claude CLI
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

## Screens (5 tabs)

1. **Home** — Recording toggle (FAB), live timer, waveform animation, today's stats, recent activity, battery chart
2. **Recordings** — Browse by date, search, inline playback, interruption badges
3. **Insights** — Full intelligence dashboard with 3 sub-tabs:
   - **Daily**: Date navigation, insight highlight, activity breakdown chart, timeline blocks, predictions
   - **Weekly**: Weekly insights list with top topics
   - **Transcripts**: Searchable transcription list with rich analysis cards
4. **People** — Voice profiles with tag/untag, interaction history, role/emotional state badges, detail view with stats
5. **Stats** — Battery chart, activity trends (7-day), sentiment trends (30-day), top people/topics, storage, settings (analysis scheduler, recording config, Claude Bridge controls)

## Build & Deploy

Built and deployed via **Buddy** (mobile-first Android dev tool):
- `buddy_write_file` → Write code
- `buddy_gradle_build` → Compile
- `buddy_adb_deploy` → Install APK
- `buddy_adb_launch` → Launch on device
- `buddy_logcat` → Debug

## Author

Deepak — SDE-2, Android SDK Engineer
