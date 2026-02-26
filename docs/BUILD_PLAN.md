# Murmur — Build Plan

## Phase 1: Core App (10 Steps)

### Step 1: Project Setup & Foundation
- [x] `gradle/libs.versions.toml` — All dependency versions (Koin, Room, Compose, Navigation, Vico, WorkManager, DataStore, Coroutines)
- [x] `build.gradle.kts` (root) — KSP plugin declaration
- [x] `app/build.gradle.kts` — All dependencies, minSdk 28, targetSdk 36, KSP for Room
- [x] `AndroidManifest.xml` — All permissions, service + receiver declarations
- [x] `core/constants/AppConstants.kt` — Recording, storage, notification, service, battery, analysis, DataStore constants
- [x] `di/AppModule.kt` — Koin modules: database, util, repository, viewModel
- [x] `MurmurApplication.kt` — startKoin + notification channels (recording, analysis, insights)

### Step 2: Room Database Layer
- [x] `data/local/entity/RecordingChunkEntity.kt` — id, sessionId, filePath, fileName, startTime, endTime, durationMs, fileSizeBytes, batteryLevelStart/End, interruptedBy, date
- [x] `data/local/entity/SessionEntity.kt` — id, startTime, endTime, totalChunks, totalSizeBytes, totalDurationMs, batteryConsumed
- [x] `data/local/entity/BatteryLogEntity.kt` — id, timestamp, batteryLevel, isCharging, temperature, sessionId, event
- [x] `data/local/entity/TranscriptionEntity.kt` — chunkId, text, sentiment, sentimentScore, keywords, processedAt, modelUsed (Phase 2, added early)
- [x] `data/local/dao/RecordingChunkDao.kt` — getByDate, getToday, getTotalSize, getUnprocessed, search
- [x] `data/local/dao/SessionDao.kt` — getActive, getAll, getById
- [x] `data/local/dao/BatteryLogDao.kt` — getBySession, getToday, getAvgDrain
- [x] `data/local/dao/TranscriptionDao.kt` — insert, getRecent, getAll, deleteAll (Phase 2, added early)
- [x] `data/local/MurmurDatabase.kt` — Room DB v1, all entities, all DAOs

### Step 3: Core Recording Service
- [x] `core/util/StorageUtil.kt` — Directory creation, chunk path generation, free space check, total usage, orphan cleanup
- [x] `core/util/NotificationUtil.kt` — Recording notification (with timer), analysis progress notification, insights notification
- [x] `service/RecordingService.kt` — Foreground service, MediaRecorder (AAC/32kbps/16kHz/mono), 15-min chunk timer, <500ms gap, START_STICKY, WakeLock
- [x] `data/repository/RecordingRepository.kt` — Bridge service ↔ Room, StateFlows for isRecording, currentSession, todayChunks, todayDuration, todayStorage

### Step 4: Call & Interruption Handling
- [x] `service/CallStateReceiver.kt` — RINGING/OFFHOOK → pause, IDLE → 2s delay → resume, save interrupted_by metadata
- [x] AudioFocus in RecordingService — LOSS → pause, GAIN → resume
- [x] Interruption metadata saved to RecordingChunkEntity

### Step 5: Battery Monitoring
- [x] `core/util/BatteryUtil.kt` — Read BatteryManager level, charging state, temperature
- [x] Battery logging at chunk boundaries in RecordingService
- [x] `data/repository/BatteryRepository.kt` — Flows: avgDrainPerHour, todayDrainData, weeklyTrend
- [x] `service/BatteryWorker.kt` — WorkManager periodic logging every 30 min

### Step 6: Auto-Start & Crash Recovery
- [x] `service/BootReceiver.kt` — BOOT_COMPLETED → check DataStore → restart service if was recording
- [x] Battery optimization bypass request + OxygenOS-specific guidance dialog
- [x] START_STICKY crash recovery: detect incomplete chunks, clean up, restart

### Step 7: UI — Home Screen
- [x] `navigation/NavGraph.kt` — NavHost + M3 NavigationBar (Home, Recordings, Stats)
- [x] `feature/home/HomeViewModel.kt` — Koin viewModel, observe RecordingRepository flows
- [x] `feature/home/HomeScreen.kt` — Recording card + FAB + timer, stats row, recent activity, battery chart
- [x] `feature/permission/PermissionScreen.kt` — Permission flow with M3 dialogs + battery optimization request

### Step 8: UI — Recordings Screen
- [x] `feature/recordings/RecordingsViewModel.kt` — Paginated chunk list, grouped by date, search
- [x] `feature/recordings/RecordingsScreen.kt` — M3 SearchBar, LazyColumn, sticky headers, chunk cards, interruption chips
- [x] `feature/recordings/AudioPlayer.kt` — MediaPlayer wrapper, play/pause/seek, progress updates

### Step 9: UI — Stats & Settings Screen
- [x] `feature/stats/StatsViewModel.kt` — Aggregate drain/hr, weekly hours, storage breakdown, interruption count
- [x] `feature/stats/StatsScreen.kt` — Summary cards, Vico charts (battery/weekly/storage), settings list
- [x] `data/repository/SettingsRepository.kt` — DataStore wrapper for all preferences
- [x] Analysis scheduler settings: trigger mode (fixed time / on charging / manual), time picker, day selector, safety conditions (require charging, min battery)
- [x] Recording settings: chunk duration (10/15/20 min), audio quality (low/normal/high), auto-delete (7/14/30 days), auto-start on boot

### Step 10: Build, Deploy & Test
- [ ] Gradle build via Buddy → deploy to OnePlus Nord CE 3
- [ ] Test: Recording lifecycle (30+ min, background, screen off, app switch, recent apps kill)
- [ ] Test: Phone call handling (incoming, outgoing, WhatsApp, reject)
- [ ] Test: OxygenOS battery kill (with optimization disabled)
- [ ] Test: Long duration (4+ hours), measure real battery drain on 782G
- [ ] Test: Edge cases (storage full, permission revoked, airplane mode)

---

## Phase 2: On-Device AI Analysis (Future)

### AI Models for Snapdragon 782G

| Model | Size | Task | Speed on 782G |
|-------|------|------|---------------|
| Vosk (Indian English) | 50 MB | Live STT (optional) | Real-time capable |
| Whisper-tiny (TFLite) | 75 MB | Batch STT (primary) | ~45s per 15-min chunk |
| MobileBERT (TFLite) | 25 MB | Sentiment analysis | ~200ms per transcript |
| Rule-based engine | 0 MB | Keywords + summary | Instant |
| **Total** | **~150 MB** | | |

### Analysis Pipeline

```
User's scheduled time triggers
        ↓
Check: battery >= min% AND (charging if required)
        ↓
AnalysisWorker starts
        ↓
For each unprocessed chunk:
  1. Whisper-tiny → transcribe → save text
  2. MobileBERT → sentiment → save score
  3. Rule-based → keywords → save
        ↓
DailySummaryGenerator → create summary
        ↓
Notification: "Your daily insights are ready 🎯"
```

### Phase 2 Tasks

- [ ] `ai/stt/WhisperTranscriber.kt` — Whisper-tiny TFLite, batch processing
- [x] `ai/stt/VoskTranscriber.kt` — Vosk live STT, PCM decoding, model initialization
- [x] `ai/nlp/SentimentAnalyzer.kt` — MobileBERT TFLite sentiment (positive/negative/neutral + score)
- [x] `ai/nlp/KeywordExtractor.kt` — Rule-based: names, food, times, action items, TF-IDF, JSON output
- [ ] `ai/summary/DailySummaryGenerator.kt` — Template engine for daily summaries
- [x] `ai/AnalysisWorker.kt` — WorkManager one-shot worker, battery check, progress state, model lifecycle
- [x] `ai/AnalysisPipeline.kt` — Orchestrates decode → transcribe → sentiment → keywords flow
- [x] `ai/AnalysisState.kt` — AnalysisStateHolder + AnalysisUiState + AnalysisStatus enum (StateFlow)
- [x] `ai/AudioDecoder.kt` — M4A → PCM conversion for Vosk input
- [x] `ai/ModelManager.kt` — Vosk + sentiment model download, caching, path resolution
- [ ] `ai/patterns/PatternDetector.kt` — 7-day pattern analysis, routine detection
- [x] `data/local/entity/TranscriptionEntity.kt` — chunkId, text, sentiment, sentimentScore, keywords, processedAt, modelUsed
- [x] `data/local/dao/TranscriptionDao.kt` — insert, getRecent, getAll, deleteAll
- [x] `data/repository/AnalysisRepository.kt` — saveTranscription, getRecentTranscriptions, clearAll
- [x] `ui/components/TranscriptionCard.kt` — Compose card showing transcription text + sentiment + keywords
- [x] `ui/components/AnalyzeButton.kt` — Compose button that triggers AnalysisWorker with progress state
- [ ] `feature/insights/InsightsScreen.kt` — Daily summary, mood timeline, keyword cloud, predictions
- [ ] Room migration v1 → v2 (TranscriptionEntity included in v1 already; no migration needed)

### Performance Budget (782G)

- Full day (64 chunks × 15 min) transcription: ~48 minutes
- Sentiment for all 64 transcripts: ~13 seconds
- Keyword extraction: ~5 seconds
- Summary generation: instant
- **Total analysis time: ~50 minutes**
- Battery cost during analysis: ~5-8%
