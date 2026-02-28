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

- [x] `ai/stt/WhisperKitTranscriber.kt` — WhisperKit on-device STT, batch processing
- [x] `ai/nlp/ClaudeCodeAnalyzer.kt` — HTTP client to Claude Bridge (/analyze, /cleanup, /link, /predict, /daily-insight)
- [x] `ai/nlp/KeywordExtractor.kt` — Rule-based: names, food, times, action items, TF-IDF, JSON output
- [x] `ai/nlp/TranscriptPostProcessor.kt` — Transcript cleanup fallback when bridge unavailable
- [x] `ai/AnalysisWorker.kt` — WorkManager worker, battery check, progress, conversation linking, insights, predictions
- [x] `ai/AnalysisPipeline.kt` — Orchestrates decode → STT → rich analysis with Claude/fallback
- [x] `ai/AnalysisState.kt` — AnalysisStateHolder + AnalysisUiState + AnalysisStatus enum (StateFlow)
- [x] `ai/AudioDecoder.kt` — M4A → PCM conversion (MediaCodec)
- [x] `ai/ModelManager.kt` — WhisperKit + sentiment model download, caching, path resolution
- [x] `ai/SpeechModelCatalog.kt` — Available model definitions (tiny, base, small)
- [x] `data/local/entity/TranscriptionEntity.kt` — Full schema with rich analysis fields
- [x] `data/local/dao/TranscriptionDao.kt` — insert, getRecent, getAll, deleteAll, with rich field projections
- [x] `data/repository/AnalysisRepository.kt` — saveTranscription, saveFullAnalysis (activities/speakers/topics)
- [x] `ui/components/TranscriptionCard.kt` — Compose card showing transcription text + sentiment + keywords
- [x] `ui/components/AnalyzeButton.kt` — Compose button that triggers AnalysisWorker with progress state
- [x] Claude Bridge server (`claude-bridge/` module) — Ktor server wrapping Claude CLI
- [x] `core/util/TermuxBridgeManager.kt` — Termux integration for starting/stopping bridge

---

## Phase 3: Life Intelligence System (6 sub-phases)

### Phase 3.1: Data Foundation
- [x] 8 new Room entities: ActivityEntity, VoiceProfileEntity, SpeakerSegmentEntity, TopicEntity, ChunkTopicEntity, ConversationLinkEntity, DailyInsightEntity, PredictionEntity
- [x] 7 new DAOs with rich queries
- [x] Room MIGRATION_2_3 (full SQL for all tables/indices)
- [x] TranscriptionEntity extended with 6 new columns (activityType, speakerCount, topicsSummary, behavioralTags, keyMoment, analysisVersion)
- [x] InsightsRepository, PeopleRepository

### Phase 3.2: Claude Bridge + Pipeline Enhancements
- [x] Rich analysis prompt (JSON structured output: activity, speakers, topics, behavioral tags)
- [x] New bridge endpoints: /link, /predict, /daily-insight
- [x] AnalysisPipeline.processChunk → rich analysis with SpeakerResult, TopicResult
- [x] ClaudeCodeAnalyzer.analyzeRich() with structured JSON parsing
- [x] AnalysisRepository.saveFullAnalysis() (activities, speakers, topics, chunk-topics)

### Phase 3.3: Insights Screen
- [x] InsightGenerator (Claude + local aggregation fallback)
- [x] InsightsViewModel (daily/weekly/transcripts views, date navigation)
- [x] InsightsScreen (3-tab view: Daily, Weekly, Transcripts)
- [x] TimelineBlock, ActivityBreakdownChart, InsightCard, PredictionCard components

### Phase 3.4: People/Voice Profiles Screen
- [x] PeopleViewModel (tagged/untagged profiles, detail view)
- [x] PeopleScreen (list + detail views with interaction history)
- [x] PersonCard, VoiceTagDialog components
- [x] 5th navigation tab (People)

### Phase 3.5: Linked Conversations + Predictions
- [x] ConversationLinker (Claude /link + heuristic fallback)
- [x] PredictionEngine (Claude /predict + heuristic fallback)
- [x] Prediction notifications via NotificationUtil
- [x] AnalysisWorker post-processing: linking → daily insight → predictions

### Phase 3.6: Stats Enhancements
- [x] ActivityTrendChart (7-day stacked columns)
- [x] SentimentTrendChart (30-day line chart)
- [x] Top People / Top Topics cards on Stats screen
- [x] StatsViewModel with trend data from InsightsRepository + PeopleRepository

### Performance Budget (782G)

- Full day (64 chunks × 15 min) transcription: ~48 minutes
- Rich analysis (Claude Bridge): ~5 minutes
- Conversation linking + insights + predictions: ~3 minutes
- **Total analysis time: ~55 minutes**
- Battery cost during analysis: ~5-8%
