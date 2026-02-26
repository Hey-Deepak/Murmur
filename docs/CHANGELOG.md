# Murmur — Changelog

## [0.1.0] — 2026-02-26

### Phase 1, Step 1: Project Setup & Foundation

#### Added
- `gradle/libs.versions.toml` — Full version catalog with all Phase 1 dependencies
  - Koin 3.5.6, Room 2.6.1, Navigation 2.8.5, Vico 1.13.1, WorkManager 2.10.0
  - KSP 2.0.21-1.0.28 for Room annotation processing
  - DataStore Preferences 1.1.1, Coroutines 1.8.1
- `build.gradle.kts` (root) — Added KSP plugin declaration
- `app/build.gradle.kts` — Complete dependency setup
  - minSdk raised from 24 → 28 (Android 9+)
  - KSP for Room compiler (no kapt)
  - All Compose, Room, Koin, WorkManager, Vico, DataStore deps
- `AndroidManifest.xml` — Full permission declarations
  - RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE
  - READ_PHONE_STATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED
  - POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
  - MANAGE_EXTERNAL_STORAGE
  - RecordingService declaration (foregroundServiceType="microphone")
  - BootReceiver + CallStateReceiver declarations
- `core/constants/AppConstants.kt` — All app-wide constants
  - Recording: chunk duration, sample rate, bitrate, channels
  - Storage: paths, thresholds, file naming
  - Notifications: channel IDs, notification IDs
  - Service: actions, wakelock tag
  - Analysis: worker tags, scheduler settings
  - DataStore: all preference keys
- `di/AppModule.kt` — Koin module definitions
  - databaseModule, utilModule, repositoryModule, viewModelModule
- `MurmurApplication.kt` — Application class
  - Koin initialization with all modules
  - Notification channel creation (recording, analysis, insights)

#### Documentation
- `docs/PROJECT_OVERVIEW.md` — Project vision, tech stack, structure, storage layout
- `docs/ARCHITECTURE.md` — System architecture, DI setup, recording pipeline, DB schema, call handling, battery monitoring
- `docs/BUILD_PLAN.md` — Complete 10-step Phase 1 plan + Phase 2 AI roadmap with checkboxes
- `docs/ANALYSIS_SCHEDULER.md` — Three scheduling modes, safety conditions, worker flow, edge cases
- `docs/AI_STRATEGY.md` — On-device AI models for 782G, processing budgets, what works/doesn't, model download strategy
- `docs/DEPENDENCIES.md` — All libraries with versions, purposes, and rationale for choices
- `docs/CHANGELOG.md` — This file

---

## [0.2.0] — Step 2: Room Database Layer

### Added
- `data/local/entity/RecordingChunkEntity.kt` — Full chunk schema with battery fields + interruptedBy + date index
- `data/local/entity/SessionEntity.kt` — Session tracking with nullable endTime for active sessions
- `data/local/entity/BatteryLogEntity.kt` — Battery events at chunk boundaries and periodic intervals
- `data/local/entity/TranscriptionEntity.kt` — Phase 2 entity added early; chunkId FK, text, sentiment, sentimentScore, keywords (JSON), processedAt, modelUsed
- `data/local/dao/RecordingChunkDao.kt` — getByDate, getToday, getTotalSize, getUnprocessed, getUnprocessedCount, markProcessed, search
- `data/local/dao/SessionDao.kt` — getActive, getAll, getById, insert, update
- `data/local/dao/BatteryLogDao.kt` — getBySession, getToday, getAvgDrain, insert
- `data/local/dao/TranscriptionDao.kt` — insert, getRecent(limit), getAll, deleteAll
- `data/local/MurmurDatabase.kt` — Room DB v1, all 4 entities, all 4 DAOs, singleton factory

---

## [0.3.0] — Step 3: Core Recording Service

### Added
- `core/util/StorageUtil.kt` — Directory creation, dated chunk path generation, free space check, total storage usage, orphan file cleanup
- `core/util/NotificationUtil.kt` — Recording foreground notification with elapsed timer, analysis progress notification, insights ready notification, skip notification
- `service/RecordingService.kt` — Foreground service (MICROPHONE type), MediaRecorder (AAC/32kbps/16kHz/mono/m4a), 15-min chunk timer with <500ms gap, START_STICKY, WakeLock, audio focus management
- `data/repository/RecordingRepository.kt` — Service ↔ Room bridge, StateFlows for isRecording, currentSession, todayChunks, todayDuration, todayStorage

---

## [0.4.0] — Step 4: Call & Interruption Handling

### Added
- `service/CallStateReceiver.kt` — Broadcast receiver for PHONE_STATE; RINGING/OFFHOOK → pause recording, IDLE → 2-second delay → auto-resume; saves `interrupted_by="phone_call"` to chunk entity
- AudioFocus handling in RecordingService — AUDIOFOCUS_LOSS → pause, AUDIOFOCUS_GAIN → resume; saves `interrupted_by="audio_focus_loss"`

---

## [0.5.0] — Step 5: Battery Monitoring

### Added
- `core/util/BatteryUtil.kt` — Reads BatteryManager for level (0–100), isCharging state, temperature
- Battery logging at chunk start/end events in RecordingService
- `data/repository/BatteryRepository.kt` — Flows: avgDrainPerHour, todayDrainData (for chart), weeklyTrend
- `service/BatteryWorker.kt` — WorkManager PeriodicWorkRequest (30 min), logs periodic battery events to Room

---

## [0.6.0] — Step 6: Auto-Start & Crash Recovery

### Added
- `service/BootReceiver.kt` — BOOT_COMPLETED broadcast receiver; reads DataStore "was_recording" flag, restarts RecordingService if true
- Battery optimization bypass request — intent to REQUEST_IGNORE_BATTERY_OPTIMIZATIONS with OxygenOS-aware guidance dialog
- START_STICKY crash recovery in RecordingService — detects incomplete chunks on restart, closes orphaned files, resumes recording

---

## [0.7.0] — Step 7: Home Screen UI

### Added
- `navigation/NavGraph.kt` — NavHost with M3 NavigationBar; destinations: home, recordings, stats; handles permission gate
- `feature/permission/PermissionScreen.kt` — Permission request flow for RECORD_AUDIO, POST_NOTIFICATIONS, READ_PHONE_STATE, MANAGE_EXTERNAL_STORAGE; battery optimization dialog
- `feature/home/HomeViewModel.kt` — Koin ViewModel; collects RecordingRepository + BatteryRepository + AnalysisRepository + AnalysisStateHolder flows; exposes start/stop recording, trigger analysis
- `feature/home/HomeScreen.kt` — Recording status card with FAB + live timer + waveform animation; today's stats row (chunks/duration/storage); recent activity list; battery drain chart (Vico); inline analyze button

---

## [0.8.0] — Step 8: Recordings Screen UI

### Added
- `feature/recordings/RecordingsViewModel.kt` — Loads chunks grouped by date from Room; search filter; date navigation
- `feature/recordings/RecordingsScreen.kt` — M3 SearchBar; LazyColumn with sticky date headers; chunk cards with duration/size/interruption chips; inline playback controls
- `feature/recordings/AudioPlayer.kt` — MediaPlayer wrapper; play/pause/seekTo; progress StateFlow; automatic release on completion

---

## [0.9.0] — Step 9: Stats & Settings Screen UI

### Added
- `feature/stats/StatsViewModel.kt` — Aggregates: avg drain/hr, weekly recording hours, storage breakdown by date, interruption count; exposes settings read/write via SettingsRepository
- `feature/stats/StatsScreen.kt` — Summary stat cards; Vico charts for battery drain (line) and weekly hours (bar); storage usage ring; settings list with analysis scheduler + recording config + storage management
- `data/repository/SettingsRepository.kt` — DataStore Preferences wrapper; all preference keys from AppConstants; typed read/write helpers for scheduler mode, chunk duration, audio quality, auto-delete days, auto-start on boot

---

## [0.10.0] — Phase 2: On-Device AI (Partial)

### Added
- `ai/AudioDecoder.kt` — Decodes M4A recordings to PCM float arrays for Vosk input using MediaExtractor + MediaCodec
- `ai/ModelManager.kt` — Downloads and caches Vosk small-en-in model + MobileBERT TFLite sentiment model to app files dir; provides typed paths; tracks download progress
- `ai/stt/VoskTranscriber.kt` — Vosk speech-to-text; accepts PCM + sample rate; returns transcript string; requires model directory initialization
- `ai/nlp/SentimentAnalyzer.kt` — MobileBERT TFLite inference; classifies text as positive/negative/neutral; returns label + confidence score (0.0–1.0)
- `ai/nlp/KeywordExtractor.kt` — Rule-based extraction of named entities, food items, time expressions, action items; TF-IDF scoring; JSON serialization
- `ai/AnalysisPipeline.kt` — Orchestrates the full chain: decode → transcribe → sentiment → keywords → AnalysisResult
- `ai/AnalysisState.kt` — AnalysisStateHolder (singleton) with StateFlow<AnalysisUiState>; states: IDLE / DOWNLOADING_MODELS / RUNNING / COMPLETED / ERROR
- `ai/AnalysisWorker.kt` — WorkManager CoroutineWorker; battery pre-check (skip if <15%); mid-processing battery guard (retry if <10%); per-chunk progress via AnalysisStateHolder; saves results via AnalysisRepository
- `data/repository/AnalysisRepository.kt` — saveTranscription (wraps AnalysisResult → TranscriptionEntity), getRecentTranscriptions(limit), getAllTranscriptions, clearAllTranscriptions
- `ui/components/TranscriptionCard.kt` — Compose card displaying transcript text, sentiment badge (colored chip), keyword chips
- `ui/components/AnalyzeButton.kt` — Compose button that enqueues AnalysisWorker; observes AnalysisStateHolder to show progress bar and status text

### Not yet implemented
- `ai/stt/WhisperTranscriber.kt` — Whisper-tiny TFLite (planned alternative to Vosk)
- `ai/summary/DailySummaryGenerator.kt` — Daily summary template engine
- `ai/patterns/PatternDetector.kt` — 7-day routine / pattern detection
- `feature/insights/InsightsScreen.kt` — Insights feed with mood timeline and keyword cloud

---

## Upcoming

### [1.0.0] — Step 10: Build, Deploy & Test on Nord CE 3
- Gradle build via Buddy → deploy to OnePlus Nord CE 3
- Full lifecycle testing (background, screen off, app switch, recent apps kill)
- Phone call handling (incoming, outgoing, WhatsApp, reject)
- OxygenOS battery kill resilience
- Long-duration test (4+ hours), measure real battery drain
- Edge cases (storage full, permission revoked, airplane mode)
