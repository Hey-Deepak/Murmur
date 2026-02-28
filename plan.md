# Murmur Life Intelligence System - Implementation Plan

## Context

Murmur currently records audio, transcribes with WhisperKit, and produces basic sentiment analysis (sentiment + tags + summary per chunk). The goal is to transform it into a **full life intelligence system** that detects activities, identifies people by voice, extracts topics, links conversations across time, generates predictions, and surfaces deep behavioral insights.

This is a 6-phase implementation touching ~32 new files and ~16 modified files.

---

## PHASE 1: Data Foundation

**Goal**: Create all new Room entities, migration v2->v3, new DAOs, and new repositories. No UI or pipeline changes.

### New Entities (8 files in `data/local/entity/`)

| Entity | Table | Key Fields | FK |
|--------|-------|------------|-----|
| `ActivityEntity` | `activities` | chunkId, activityType (eating/meeting/working/commuting/idle/phone_call/casual_chat/solo), confidence, subActivity, date, startTime, durationMs | chunkId -> recording_chunks CASCADE |
| `VoiceProfileEntity` | `voice_profiles` | voiceId (Claude-assigned), label (user-assigned name), photoUri, firstSeenAt, lastSeenAt, totalInteractionMs, interactionCount, notes | none |
| `SpeakerSegmentEntity` | `speaker_segments` | chunkId, voiceProfileId, speakerLabel, speakingDurationMs, turnCount, role, emotionalState | chunkId CASCADE, voiceProfileId SET_NULL |
| `TopicEntity` | `topics` | name, firstMentionedAt, lastMentionedAt, totalMentions, totalDurationMs, category | none |
| `ChunkTopicEntity` | `chunk_topics` | chunkId+topicId (composite PK), relevance, keyPoints | both CASCADE |
| `ConversationLinkEntity` | `conversation_links` | sourceChunkId, targetChunkId, linkType (same_person/same_topic/same_time_slot/cause_effect/continuation), description, strength | both CASCADE |
| `DailyInsightEntity` | `daily_insights` | date (unique), timelineJson, timeBreakdownJson, peopleSummaryJson, topTopics, highlight, overallSentiment, overallSentimentScore | none |
| `PredictionEntity` | `predictions` | predictionType, message, confidence, basedOnDays, triggerTime, date, isActive, wasFulfilled | none |

### Modify TranscriptionEntity (add 6 columns)

```kotlin
val activityType: String? = null       // detected activity (denormalized for quick access)
val speakerCount: Int? = null          // number of distinct speakers detected
val topicsSummary: String? = null      // JSON: ["topic1","topic2"] (denormalized)
val behavioralTags: String? = null     // JSON: ["rapid speech","code-switching"]
val keyMoment: String? = null          // most notable moment in this chunk
val analysisVersion: Int = 1           // tracks which prompt version produced this
```

### Update TranscriptionWithChunk to include the 6 new fields

### Database Migration v2 -> v3

In `MurmurDatabase.kt`:
- Bump to version 3
- Add all 8 new entities to `@Database` annotation
- Add 7 new abstract DAO methods
- Create `MIGRATION_2_3`:

```sql
-- New columns on transcriptions
ALTER TABLE transcriptions ADD COLUMN activityType TEXT DEFAULT NULL;
ALTER TABLE transcriptions ADD COLUMN speakerCount INTEGER DEFAULT NULL;
ALTER TABLE transcriptions ADD COLUMN topicsSummary TEXT DEFAULT NULL;
ALTER TABLE transcriptions ADD COLUMN behavioralTags TEXT DEFAULT NULL;
ALTER TABLE transcriptions ADD COLUMN keyMoment TEXT DEFAULT NULL;
ALTER TABLE transcriptions ADD COLUMN analysisVersion INTEGER NOT NULL DEFAULT 1;

-- activities table
CREATE TABLE IF NOT EXISTS activities (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    chunkId INTEGER NOT NULL,
    activityType TEXT NOT NULL,
    confidence REAL NOT NULL,
    subActivity TEXT DEFAULT NULL,
    date TEXT NOT NULL,
    startTime INTEGER NOT NULL,
    durationMs INTEGER NOT NULL,
    detectedAt INTEGER NOT NULL,
    FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_activities_chunkId ON activities(chunkId);
CREATE INDEX IF NOT EXISTS index_activities_date ON activities(date);
CREATE INDEX IF NOT EXISTS index_activities_activityType ON activities(activityType);

-- voice_profiles table
CREATE TABLE IF NOT EXISTS voice_profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    voiceId TEXT NOT NULL,
    label TEXT DEFAULT NULL,
    photoUri TEXT DEFAULT NULL,
    firstSeenAt INTEGER NOT NULL,
    lastSeenAt INTEGER NOT NULL,
    totalInteractionMs INTEGER NOT NULL DEFAULT 0,
    interactionCount INTEGER NOT NULL DEFAULT 0,
    notes TEXT DEFAULT NULL
);
CREATE INDEX IF NOT EXISTS index_voice_profiles_label ON voice_profiles(label);

-- speaker_segments table
CREATE TABLE IF NOT EXISTS speaker_segments (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    chunkId INTEGER NOT NULL,
    voiceProfileId INTEGER DEFAULT NULL,
    speakerLabel TEXT NOT NULL,
    speakingDurationMs INTEGER NOT NULL,
    turnCount INTEGER NOT NULL,
    role TEXT DEFAULT NULL,
    emotionalState TEXT DEFAULT NULL,
    FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
    FOREIGN KEY (voiceProfileId) REFERENCES voice_profiles(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS index_speaker_segments_chunkId ON speaker_segments(chunkId);
CREATE INDEX IF NOT EXISTS index_speaker_segments_voiceProfileId ON speaker_segments(voiceProfileId);

-- topics table
CREATE TABLE IF NOT EXISTS topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    firstMentionedAt INTEGER NOT NULL,
    lastMentionedAt INTEGER NOT NULL,
    totalMentions INTEGER NOT NULL DEFAULT 1,
    totalDurationMs INTEGER NOT NULL DEFAULT 0,
    category TEXT DEFAULT NULL
);
CREATE INDEX IF NOT EXISTS index_topics_name ON topics(name);

-- chunk_topics junction
CREATE TABLE IF NOT EXISTS chunk_topics (
    chunkId INTEGER NOT NULL,
    topicId INTEGER NOT NULL,
    relevance REAL NOT NULL DEFAULT 1.0,
    keyPoints TEXT DEFAULT NULL,
    PRIMARY KEY (chunkId, topicId),
    FOREIGN KEY (chunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
    FOREIGN KEY (topicId) REFERENCES topics(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_chunk_topics_chunkId ON chunk_topics(chunkId);
CREATE INDEX IF NOT EXISTS index_chunk_topics_topicId ON chunk_topics(topicId);

-- conversation_links table
CREATE TABLE IF NOT EXISTS conversation_links (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    sourceChunkId INTEGER NOT NULL,
    targetChunkId INTEGER NOT NULL,
    linkType TEXT NOT NULL,
    description TEXT DEFAULT NULL,
    strength REAL NOT NULL DEFAULT 1.0,
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (sourceChunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE,
    FOREIGN KEY (targetChunkId) REFERENCES recording_chunks(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_conversation_links_sourceChunkId ON conversation_links(sourceChunkId);
CREATE INDEX IF NOT EXISTS index_conversation_links_targetChunkId ON conversation_links(targetChunkId);
CREATE INDEX IF NOT EXISTS index_conversation_links_linkType ON conversation_links(linkType);

-- daily_insights table
CREATE TABLE IF NOT EXISTS daily_insights (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date TEXT NOT NULL,
    timelineJson TEXT NOT NULL,
    timeBreakdownJson TEXT NOT NULL,
    peopleSummaryJson TEXT NOT NULL,
    topTopics TEXT NOT NULL,
    highlight TEXT DEFAULT NULL,
    overallSentiment TEXT NOT NULL,
    overallSentimentScore REAL NOT NULL,
    totalRecordedMs INTEGER NOT NULL,
    totalAnalyzedChunks INTEGER NOT NULL,
    generatedAt INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS index_daily_insights_date ON daily_insights(date);

-- predictions table
CREATE TABLE IF NOT EXISTS predictions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    predictionType TEXT NOT NULL,
    message TEXT NOT NULL,
    confidence REAL NOT NULL,
    basedOnDays INTEGER NOT NULL,
    triggerTime INTEGER DEFAULT NULL,
    date TEXT NOT NULL,
    isActive INTEGER NOT NULL DEFAULT 1,
    wasFulfilled INTEGER DEFAULT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_predictions_date ON predictions(date);
CREATE INDEX IF NOT EXISTS index_predictions_isActive ON predictions(isActive);
```

### New DAOs (7 files in `data/local/dao/`)

**ActivityDao**:
- `insert(activity)`, `getByChunk(chunkId)`, `getByDate(date): Flow`
- `getTimeBreakdown(date)` -> List<ActivityTimeBreakdown(activityType, totalMs)>
- `getTimeBreakdownRange(startDate, endDate)`
- `getAllDates(): Flow`, `deleteOlderThan(beforeDate)`

**VoiceProfileDao**:
- `insert(profile)`, `update(profile)`, `getAll(): Flow`, `getById(id)`, `getByVoiceId(voiceId)`
- `getTagged(): Flow` (label IS NOT NULL), `getUntagged(): Flow` (label IS NULL)
- `setLabel(id, label)`, `setPhoto(id, photoUri)`
- `incrementInteraction(id, addMs, lastSeen)`, `deleteById(id)`

**SpeakerSegmentDao**:
- `insertAll(segments)`, `getByChunk(chunkId)`, `getByProfile(profileId): Flow`
- `getRecentByProfile(profileId, limit)` -> SpeakerSegmentWithDate (JOIN with chunks)
- `tagSpeaker(label, profileId)`, `getTotalSpeakingTime(profileId)`

**TopicDao**:
- `insert(topic)`, `update(topic)`, `getByName(name)`, `getTopTopics(limit): Flow`
- `getRecentTopics(limit): Flow`, `getByChunk(chunkId)` (JOIN via chunk_topics)
- `getByCategory(category): Flow`, `insertChunkTopic(chunkTopic)`, `deleteById(id)`

**ConversationLinkDao**:
- `insert(link)`, `insertAll(links)`, `getByChunk(chunkId)`, `getByType(type, limit): Flow`
- `deleteOlderThan(before)`

**DailyInsightDao**:
- `insert(insight)`, `getByDate(date)`, `getByDateFlow(date): Flow`
- `getRecent(limit): Flow`, `getRange(startDate, endDate)`, `deleteOlderThan(beforeDate)`

**PredictionDao**:
- `insert(prediction)`, `getActiveForDate(date): Flow`, `getActive(limit): Flow`
- `dismiss(id)`, `markFulfillment(id, fulfilled)`, `deleteOldDismissed(before)`

### Update TranscriptionDao

All projection queries (getAllWithChunks, searchWithChunks, getWithChunkByChunkId) must include:
`t.activityType, t.speakerCount, t.topicsSummary, t.behavioralTags, t.keyMoment, t.analysisVersion`

### New Repositories (2 files in `data/repository/`)

**InsightsRepository**(activityDao, dailyInsightDao, topicDao, conversationLinkDao, predictionDao):
- Activity: saveActivity, getActivitiesByDate, getTimeBreakdown, getTimeBreakdownRange
- Insights: saveDailyInsight, getDailyInsight, getDailyInsightFlow, getRecentInsights
- Topics: getOrCreateTopic, linkChunkToTopic, getTopTopics, getRecentTopics
- Links: saveLinks, getLinksForChunk
- Predictions: savePrediction, getActivePredictions, dismissPrediction

**PeopleRepository**(voiceProfileDao, speakerSegmentDao):
- Profiles: getAllProfiles, getTaggedProfiles, getUntaggedProfiles, getOrCreateProfile, tagProfile, setProfilePhoto, deleteProfile
- Segments: saveSpeakerSegments, getSegmentsForChunk, getSegmentsForProfile, getRecentInteractions, getTotalSpeakingTime

### DI Updates (`AppModule.kt`)

```kotlin
// databaseModule - add all new DAOs
single { get<MurmurDatabase>().activityDao() }
single { get<MurmurDatabase>().voiceProfileDao() }
single { get<MurmurDatabase>().speakerSegmentDao() }
single { get<MurmurDatabase>().topicDao() }
single { get<MurmurDatabase>().conversationLinkDao() }
single { get<MurmurDatabase>().dailyInsightDao() }
single { get<MurmurDatabase>().predictionDao() }

// repositoryModule - add new repos
single { InsightsRepository(get(), get(), get(), get(), get()) }
single { PeopleRepository(get(), get()) }
```

### Phase 1 Files Summary
- **14 new files**: 8 entities + 7 DAOs (ActivityTimeBreakdown and SpeakerSegmentWithDate live in their respective DAO files) + 2 repositories = 17 total, but some DAOs include helper data classes
- **5 modified files**: TranscriptionEntity, TranscriptionWithChunk, TranscriptionDao, MurmurDatabase, AppModule

---

## PHASE 2: Claude Bridge + Analysis Pipeline

**Goal**: Update Claude to return rich structured JSON with activity, speakers, topics. Update pipeline to parse and store everything.

### Claude Bridge Updates (`Main.kt`)

**New data classes**:
```kotlin
@Serializable
data class RichAnalyzeResponse(
    val sentiment: String, val score: Float, val tags: List<String>, val summary: String,
    val activity: ActivityDetection,
    val speakers: List<SpeakerDetection>,
    val topics: List<TopicDetection>,
    val keyMoment: String? = null,
    val behavioralTags: List<String> = emptyList()
)

@Serializable
data class ActivityDetection(
    val type: String,          // eating|meeting|working|commuting|idle|phone_call|casual_chat|solo
    val confidence: Float,
    val subActivity: String? = null
)

@Serializable
data class SpeakerDetection(
    val label: String,         // "Speaker A", "Speaker B"
    val speakingRatio: Float,
    val turnCount: Int,
    val role: String? = null,  // dominant|listener|equal
    val emotionalState: String? = null
)

@Serializable
data class TopicDetection(
    val name: String,
    val relevance: Float,
    val category: String? = null,
    val keyPoints: List<String> = emptyList()
)
```

**New analysis prompt** (JSON-only output):
```
You are a behavioral intelligence analyst. Analyze this audio transcript to understand
the full context of what happened during this recording segment.

The speaker may use Hindi-English code-switching (Hinglish). Preserve Hindi words as-is.

Respond in EXACTLY this JSON format (valid JSON, no markdown, no comments):

{
  "sentiment": "<positive|negative|neutral|anxious|frustrated|confident|hesitant|excited>",
  "score": <0.00-1.00>,
  "tags": ["tag1", "tag2"],
  "summary": "One paragraph behavioral analysis...",
  "activity": {
    "type": "<eating|meeting|working|commuting|idle|phone_call|casual_chat|solo>",
    "confidence": <0.0-1.0>,
    "subActivity": "<optional: lunch, standup, debugging>"
  },
  "speakers": [
    {
      "label": "Speaker A",
      "speakingRatio": <0.0-1.0>,
      "turnCount": <int>,
      "role": "<dominant|listener|equal|null>",
      "emotionalState": "<calm|engaged|frustrated|excited|hesitant|null>"
    }
  ],
  "topics": [
    {
      "name": "topic name (lowercase, 1-3 words)",
      "relevance": <0.0-1.0>,
      "category": "<work|personal|health|finance|entertainment|education|null>",
      "keyPoints": ["point 1", "point 2"]
    }
  ],
  "keyMoment": "The most notable moment or shift in this segment, or null",
  "behavioralTags": ["rapid speech", "code-switching", "interrupting", "leading discussion"]
}

Rules:
- Detect distinct speakers from speech patterns (turn-taking, different perspectives)
- Activity type inferred from context clues (food mentions = eating, multiple speakers + agenda = meeting)
- Topics normalized to lowercase 1-3 word phrases
- Behavioral tags describe HOW the person speaks, not WHAT they say
- Respond ONLY with valid JSON, nothing else
```

### AnalysisPipeline Updates (`AnalysisPipeline.kt`)

Expand `AnalysisResult`:
```kotlin
data class AnalysisResult(
    val chunkId: Long, val text: String, val sentiment: String, val sentimentScore: Float,
    val keywords: String, val modelUsed: String,
    // New fields
    val activityType: String? = null, val activityConfidence: Float? = null, val activitySubType: String? = null,
    val speakers: List<SpeakerResult> = emptyList(),
    val topics: List<TopicResult> = emptyList(),
    val behavioralTags: List<String> = emptyList(),
    val keyMoment: String? = null
)

data class SpeakerResult(val label: String, val speakingRatio: Float, val turnCount: Int, val role: String?, val emotionalState: String?)
data class TopicResult(val name: String, val relevance: Float, val category: String?, val keyPoints: List<String>)
```

### ClaudeCodeAnalyzer Updates (`ClaudeCodeAnalyzer.kt`)

Add `analyzeRich(transcript: String): ClaudeRichAnalysis`:
- Calls bridge `/analyze` with rich prompt
- Parses full JSON into ClaudeRichAnalysis
- Falls back to old `analyze()` if JSON parsing fails

### AnalysisRepository Updates (`AnalysisRepository.kt`)

Add `saveFullAnalysis(result: AnalysisResult, chunkDate: String, chunkStartTime: Long, chunkDurationMs: Long)`:
1. Insert expanded TranscriptionEntity (with new fields populated)
2. Insert ActivityEntity via InsightsRepository
3. For each speaker: getOrCreate VoiceProfileEntity, insert SpeakerSegmentEntity
4. For each topic: getOrCreate TopicEntity, insert ChunkTopicEntity junction
5. Mark chunk as processed

### AnalysisWorker - call `saveFullAnalysis()` instead of `saveTranscription()`

### Phase 2 Files Summary
- **0 new files**
- **5 modified files**: Main.kt (bridge), AnalysisPipeline.kt, ClaudeCodeAnalyzer.kt, AnalysisRepository.kt, AnalysisWorker.kt

---

## PHASE 3: Insights Screen

**Goal**: Replace Transcriptions tab with full intelligence dashboard.

### InsightsViewModel (`feature/insights/InsightsViewModel.kt`)
```kotlin
class InsightsViewModel(insightsRepo, analysisRepo, recordingRepo, peopleRepo) : ViewModel() {
    val viewMode: StateFlow<String>                          // "daily" | "weekly" | "monthly"
    val selectedDate: StateFlow<String>                      // YYYY-MM-DD
    val dailyInsight: StateFlow<DailyInsightEntity?>
    val dailyActivities: StateFlow<List<ActivityEntity>>
    val dailyTranscriptions: StateFlow<List<TranscriptionWithChunk>>
    val timeBreakdown: StateFlow<List<ActivityTimeBreakdown>>
    val weeklyInsights: StateFlow<List<DailyInsightEntity>>
    val weeklyTopTopics: StateFlow<List<TopicEntity>>
    val weeklyTimeBreakdown: StateFlow<List<ActivityTimeBreakdown>>
    val predictions: StateFlow<List<PredictionEntity>>

    fun selectDate(date: String)
    fun setViewMode(mode: String)
    fun dismissPrediction(id: Long)
    fun generateDailyInsight(date: String)
}
```

### InsightsScreen (`feature/insights/InsightsScreen.kt`)
- **Daily view**: Timeline blocks, time breakdown chart, people encountered row, top topics, highlight card, per-chunk insight cards
- **Weekly view**: Activity trend chart, people frequency, topic trends, predictions list
- **Monthly view**: Calendar heatmap, monthly summaries

### New UI Components
- `TimelineBlock.kt` - time range, activity icon, people badges, topic tags, expandable
- `ActivityBreakdownChart.kt` - stacked bar or donut chart (Vico)
- `InsightCard.kt` - enhanced TranscriptionCardV2 with activity/people/topics/keyMoment/links
- `PredictionCard.kt` - prediction with type icon, message, confidence, dismiss/feedback buttons
- `InsightGenerator.kt` - aggregates day's data, optionally sends to Claude for daily summary

### Navigation: Rename Transcriptions -> Insights (icon: Psychology), keep old TranscriptionsScreen accessible from Insights

### Phase 3 Files Summary
- **7 new files**: InsightsViewModel, InsightsScreen, TimelineBlock, ActivityBreakdownChart, InsightCard, PredictionCard, InsightGenerator
- **3 modified files**: NavGraph.kt, AppModule.kt, Main.kt (optional /daily-insight endpoint)

---

## PHASE 4: People/Voice Profiles Screen

**Goal**: New People tab for voice detection, tagging, and per-person profiles.

### PeopleViewModel (`feature/people/PeopleViewModel.kt`)
```kotlin
class PeopleViewModel(peopleRepo, insightsRepo) : ViewModel() {
    val taggedProfiles: StateFlow<List<VoiceProfileEntity>>
    val untaggedProfiles: StateFlow<List<VoiceProfileEntity>>
    val selectedProfile: StateFlow<VoiceProfileEntity?>
    val selectedProfileSegments: StateFlow<List<SpeakerSegmentWithDate>>
    val selectedProfileTopics: StateFlow<List<TopicEntity>>

    fun selectProfile(id: Long)
    fun tagProfile(profileId: Long, name: String)
    fun setProfilePhoto(profileId: Long, uri: String)
    fun deleteProfile(profileId: Long)
    fun mergeProfiles(sourceId: Long, targetId: Long)
}
```

### PeopleScreen (`feature/people/PeopleScreen.kt`)
- **Untagged voices**: "Voice A" cards with tag button, last seen, total time, sample chunk reference
- **Tagged people**: Name/photo cards with stats, click for detail
- **Detail view**: Total time, common topics, recent interactions, emotional state distribution, frequency chart, role distribution

### New Components
- `PersonCard.kt` - avatar (initials/photo), name, stats, interaction sparkline
- `VoiceTagDialog.kt` - text field for name + optional photo picker

### Navigation: Add People as 5th bottom tab (Home, Recordings, Insights, People, Stats)

### Phase 4 Files Summary
- **4 new files**: PeopleViewModel, PeopleScreen, PersonCard, VoiceTagDialog
- **2 modified files**: NavGraph.kt, AppModule.kt

---

## PHASE 5: Linked Conversations + Predictions

**Goal**: Cross-chunk relationship detection and behavioral predictions with notifications.

### ConversationLinker (`ai/ConversationLinker.kt`)

Runs after each chunk is analyzed:
1. Query recent chunks with same speakers (via speaker_segments)
2. Query recent chunks with overlapping topics (via chunk_topics)
3. Query chunks at same time-of-day on previous days
4. Send context to Claude `/link` endpoint for intelligent relationship detection
5. Store ConversationLinkEntity entries

### PredictionEngine (`ai/PredictionEngine.kt`)

Runs after daily insight generation:
1. Detect routine patterns (same activity at same time for 3+ days)
2. Detect anomalies (missing expected activity)
3. Detect relationship trends (fading interactions)
4. Send patterns to Claude `/predict` for natural language predictions
5. Store PredictionEntity entries + schedule notifications

### Claude Bridge New Endpoints

**POST /link**: Input = current chunk + candidate related chunks; Output = list of links with types and descriptions

**POST /predict**: Input = pattern data + current date/time; Output = list of predictions with type, message, confidence, triggerTime

### Integration
- AnalysisWorker calls `conversationLinker.findLinks()` for each newly analyzed chunk
- After all chunks done, calls `insightGenerator.generateDailyInsight()` for today
- Then calls `predictionEngine.generatePredictions()` for tomorrow

### Phase 5 Files Summary
- **2 new files**: ConversationLinker.kt, PredictionEngine.kt
- **5 modified files**: Main.kt (bridge), AnalysisWorker.kt, AppConstants.kt, NotificationUtil.kt, AppModule.kt

---

## PHASE 6: Stats Enhancements

**Goal**: Trend visualizations and deeper analytics on Stats screen.

### StatsViewModel additions
- `weeklyActivityTrend: StateFlow<Map<String, List<ActivityTimeBreakdown>>>` (7 days)
- `monthlySentimentTrend: StateFlow<List<Pair<String, Float>>>` (date -> avg score)
- `topPeopleThisWeek: StateFlow<List<VoiceProfileEntity>>`
- `topTopicsThisWeek: StateFlow<List<TopicEntity>>`
- `predictionAccuracy: StateFlow<Float>` (% fulfilled)

### StatsScreen new sections
- Activity Trends card (stacked bar chart, 7 days, Vico)
- Sentiment Trends card (line chart, 30 days)
- Top People This Week card (horizontal person chips with time)
- Top Topics This Week card (tag cloud with counts)
- Prediction Accuracy card

### New Components
- `ActivityTrendChart.kt` - stacked column chart using Vico
- `SentimentTrendChart.kt` - line chart using Vico

### Phase 6 Files Summary
- **2 new files**: ActivityTrendChart.kt, SentimentTrendChart.kt
- **2 modified files**: StatsViewModel.kt, StatsScreen.kt

---

## Phase Dependency Graph

```
Phase 1 (Data Foundation)
    |
    v
Phase 2 (Claude Bridge + Pipeline) -- depends on Phase 1 entities
    |
    v
Phase 3 (Insights Screen) -- depends on Phase 2 data population
    |
    v
Phase 4 (People Screen) -- depends on Phase 2 speaker segments
    |
    v
Phase 5 (Linked Conversations + Predictions) -- depends on Phases 2, 3, 4
    |
    v
Phase 6 (Stats Enhancements) -- depends on all prior phases
```

---

## Complete File Manifest

### New Files (32)

| # | Path | Purpose |
|---|------|---------|
| 1 | `data/local/entity/ActivityEntity.kt` | Activity detection per chunk |
| 2 | `data/local/entity/VoiceProfileEntity.kt` | Speaker identity profiles |
| 3 | `data/local/entity/SpeakerSegmentEntity.kt` | Speaker-chunk junction with details |
| 4 | `data/local/entity/TopicEntity.kt` | Extracted topics |
| 5 | `data/local/entity/ChunkTopicEntity.kt` | Chunk-topic junction |
| 6 | `data/local/entity/ConversationLinkEntity.kt` | Links between conversations |
| 7 | `data/local/entity/DailyInsightEntity.kt` | Aggregated daily intelligence |
| 8 | `data/local/entity/PredictionEntity.kt` | Pattern-based predictions |
| 9 | `data/local/dao/ActivityDao.kt` | Activity queries |
| 10 | `data/local/dao/VoiceProfileDao.kt` | Voice profile queries |
| 11 | `data/local/dao/SpeakerSegmentDao.kt` | Speaker segment queries |
| 12 | `data/local/dao/TopicDao.kt` | Topic queries |
| 13 | `data/local/dao/ConversationLinkDao.kt` | Conversation link queries |
| 14 | `data/local/dao/DailyInsightDao.kt` | Daily insight queries |
| 15 | `data/local/dao/PredictionDao.kt` | Prediction queries |
| 16 | `data/repository/InsightsRepository.kt` | Insights data access |
| 17 | `data/repository/PeopleRepository.kt` | People data access |
| 18 | `feature/insights/InsightsViewModel.kt` | Insights screen state |
| 19 | `feature/insights/InsightsScreen.kt` | Intelligence dashboard UI |
| 20 | `feature/people/PeopleViewModel.kt` | People screen state |
| 21 | `feature/people/PeopleScreen.kt` | Voice profiles UI |
| 22 | `ui/components/TimelineBlock.kt` | Timeline segment component |
| 23 | `ui/components/ActivityBreakdownChart.kt` | Activity pie/bar chart |
| 24 | `ui/components/InsightCard.kt` | Enhanced analysis card |
| 25 | `ui/components/PredictionCard.kt` | Prediction display card |
| 26 | `ui/components/PersonCard.kt` | Person profile card |
| 27 | `ui/components/VoiceTagDialog.kt` | Name assignment dialog |
| 28 | `ai/ConversationLinker.kt` | Cross-chunk link detection |
| 29 | `ai/PredictionEngine.kt` | Pattern-based prediction generation |
| 30 | `ai/InsightGenerator.kt` | Daily insight aggregation |
| 31 | `ui/components/ActivityTrendChart.kt` | Weekly activity trend chart |
| 32 | `ui/components/SentimentTrendChart.kt` | Sentiment trend line chart |

### Modified Files (16)

| # | Path | Changes |
|---|------|---------|
| 1 | `data/local/entity/TranscriptionEntity.kt` | Add 6 new nullable columns |
| 2 | `data/local/entity/TranscriptionWithChunk.kt` | Add 6 new fields |
| 3 | `data/local/dao/TranscriptionDao.kt` | Update query projections |
| 4 | `data/local/MurmurDatabase.kt` | v3, 8 new entities, 7 new DAOs, MIGRATION_2_3 |
| 5 | `di/AppModule.kt` | Register all new DAOs, repos, VMs, AI classes |
| 6 | `claude-bridge/.../Main.kt` | Rich prompt, new response types, /link, /predict endpoints |
| 7 | `ai/AnalysisPipeline.kt` | Expanded AnalysisResult, SpeakerResult, TopicResult |
| 8 | `ai/nlp/ClaudeCodeAnalyzer.kt` | Add analyzeRich(), ClaudeRichAnalysis |
| 9 | `data/repository/AnalysisRepository.kt` | Add saveFullAnalysis() with multi-table writes |
| 10 | `ai/AnalysisWorker.kt` | Call saveFullAnalysis, linker, prediction engine |
| 11 | `navigation/NavGraph.kt` | Replace Transcriptions with Insights, add People tab |
| 12 | `feature/stats/StatsViewModel.kt` | Add trend queries |
| 13 | `feature/stats/StatsScreen.kt` | Add trend visualization sections |
| 14 | `core/constants/AppConstants.kt` | Prediction notification constants |
| 15 | `core/util/NotificationUtil.kt` | Prediction notification methods |
| 16 | `ui/theme/Color.kt` | Add activity type colors |

---

## Backward Compatibility

1. **Migration v2->v3**: All new `transcriptions` columns have NULL defaults. Existing data preserved. `analysisVersion = 1` marks pre-upgrade rows.
2. **Old analysis results**: UI checks `analysisVersion` and renders old-style card for v1 data.
3. **Claude bridge**: Rich JSON response with fallback to old SENTIMENT/SCORE/TAGS/SUMMARY format if JSON parsing fails.
4. **Existing screens**: Home, Recordings, Stats continue unchanged. Transcriptions screen retained within Insights.
5. **On-device fallback**: When bridge unavailable, all new fields are null. UI hides unavailable sections.

---

## Verification Checklist

- [ ] **Phase 1**: Build succeeds, DB migration runs, new tables visible in DB inspector
- [ ] **Phase 2**: Analyze a chunk -> all new tables (activities, speaker_segments, topics, chunk_topics) populated
- [ ] **Phase 3**: Insights screen shows daily timeline with activity/people/topic data
- [ ] **Phase 4**: People screen shows detected voices, tagging persists across sessions
- [ ] **Phase 5**: Conversation links appear on insight cards, predictions generated for next day
- [ ] **Phase 6**: Stats shows activity trend + sentiment trend charts with real data
