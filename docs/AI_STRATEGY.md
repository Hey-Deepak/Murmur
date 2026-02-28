# Murmur — On-Device AI Strategy

## Target Hardware: Snapdragon 782G (OnePlus Nord CE 3)

### What We Have
- CPU: Kryo 670 (1× A710 prime @ 2.7 GHz + 3× A710 @ 2.4 GHz + 4× A510 @ 1.8 GHz)
- GPU: Adreno 642L
- NPU: Hexagon 770 (limited — no QNN runtime like 8-series)
- RAM: 8/12 GB LPDDR4X
- Storage: UFS 3.1

### What Works vs What Doesn't

| Technology | Works on 782G? | Why |
|-----------|----------------|-----|
| Vosk STT | ✅ Yes | CPU-only, lightweight, 50 MB model |
| Whisper-tiny TFLite | ✅ Yes | TFLite runs on CPU/GPU, 75 MB model |
| MobileBERT TFLite | ✅ Yes | Small model, ~200ms inference |
| WhisperKit (QNN) | ❌ No | Requires Snapdragon 8-series NPU |
| Gemini Nano | ❌ No | Only Pixel 9/10, select flagships |
| Large Whisper models | ❌ Too slow | whisper-small/medium would drain battery |

## AI Stack (Current)

### Layer 1: Speech-to-Text

**Primary: WhisperKit (on-device, QNN-accelerated where available)**
- Multiple model sizes available via SpeechModelCatalog (tiny, base, small)
- Task: Transcribe 15-min audio chunks to text
- Speed: ~45 seconds per 15-min chunk (tiny model on 782G)
- Multi-language support (configurable, default: Hindi)
- When: During scheduled analysis time (batch all day's chunks)

### Layer 2: Transcript Post-Processing

**TranscriptPostProcessor (on-device, rule-based)**
- Cleans WhisperKit output artifacts (repetitions, filler words)
- Normalizes punctuation and capitalization
- Falls back to local processing when Claude Bridge unavailable

**Claude Bridge /cleanup endpoint (when available)**
- Higher quality cleanup preserving hesitations and natural speech patterns
- Fixes transcription errors using language context

### Layer 3: Rich Behavioral Analysis

**Primary: Claude Bridge /analyze (structured JSON)**
- Activity detection: eating, meeting, working, commuting, idle, phone_call, casual_chat, solo
- Speaker identification: distinct voices with role and emotional state
- Topic extraction: subjects with key points
- Sentiment analysis: positive/negative/neutral with 0.0-1.0 score
- Behavioral tags: rapid_speech, code_switching, leading_discussion, etc.
- Key moments: significant conversation highlights

**Fallback: On-device MobileBERT + KeywordExtractor + TranscriptPostProcessor**
- MobileBERT TFLite for sentiment classification (~200ms per transcript)
- Rule-based keyword extraction: names, food, times, action items, TF-IDF scoring
- TranscriptPostProcessor for activity heuristics from keywords

### Layer 4: Conversation Linking

**ConversationLinker**
- Primary: Claude Bridge /link endpoint (cross-references chunks by person/topic/time)
- Fallback: Heuristic linking
  - Topic overlap (shared keywords/topics between chunks)
  - Time-of-day patterns (same time slot across days)
  - Sequential proximity (chunks within same session)
- Link types: same_person, same_topic, same_time_slot, cause_effect, continuation

### Layer 5: Daily Insights

**InsightGenerator**
- Primary: Claude Bridge /daily-insight endpoint (full daily narrative)
- Fallback: Aggregation from transcriptions, activities, topics, people data
- Produces: daily timeline, activity breakdown, top people, top topics, highlight

### Layer 6: Predictions

**PredictionEngine**
- Primary: Claude Bridge /predict endpoint (pattern-based predictions)
- Fallback: Heuristic analysis of weekly patterns
  - Routine predictions (recurring activities at same time)
  - Habit predictions (consistent daily patterns)
  - Relationship predictions (interaction frequency changes)
- Types: routine, anomaly, relationship, habit
- Notification support for active predictions

## Model Storage Budget

| Model | Size | Required |
|-------|------|----------|
| WhisperKit tiny | ~75 MB | STT (primary) |
| WhisperKit base | ~150 MB | STT (optional, higher quality) |
| MobileBERT Sentiment | ~25 MB | Fallback sentiment |
| Rule-based engines | 0 MB | Always available |
| **Total** | **~100-250 MB** | |

## Processing Budget (Full Day)

Assuming 16-hour recording day = 64 chunks × 15 minutes:

| Step | Per Chunk | All 64 Chunks | Battery |
|------|-----------|---------------|---------|
| WhisperKit transcription | ~45s | ~48 min | ~5-8% |
| Claude Bridge rich analysis | ~5s | ~5 min | negligible |
| Conversation linking | ~2s | ~2 min | negligible |
| Daily insight generation | — | ~10s | negligible |
| Prediction generation | — | ~10s | negligible |
| **Total** | | **~55 min** | **~5-8%** |

*When Claude Bridge unavailable, MobileBERT fallback reduces analysis time to ~50 min but with less rich output.*

## Implementation (Current)

### File Structure

```
com.dc.murmur.ai/
├── stt/
│   └── WhisperKitTranscriber.kt  # WhisperKit on-device STT
├── nlp/
│   ├── ClaudeCodeAnalyzer.kt     # HTTP client to Claude Bridge
│   ├── KeywordExtractor.kt       # Rule-based extraction (fallback)
│   └── TranscriptPostProcessor.kt # Transcript cleanup (fallback)
├── AnalysisPipeline.kt           # decode → STT → rich analysis
├── AnalysisWorker.kt             # WorkManager worker + post-analysis tasks
├── AnalysisState.kt              # AnalysisStateHolder + AnalysisUiState
├── AudioDecoder.kt               # M4A → PCM (MediaCodec)
├── ModelManager.kt               # Model download, caching, paths
├── SpeechModelCatalog.kt         # Available WhisperKit model definitions
├── InsightGenerator.kt           # Daily insight aggregation
├── ConversationLinker.kt         # Cross-chunk conversation linking
├── PredictionEngine.kt           # Pattern-based predictions
└── BridgeStatus.kt               # BridgeStatusHolder + BridgeUiState
```

### Koin Module (aiModule)

```kotlin
val aiModule = module {
    single { AudioDecoder() }
    single { ModelManager(androidContext()) }
    single { KeywordExtractor() }
    single { ClaudeCodeAnalyzer(get()) }
    single { TranscriptPostProcessor() }
    single { AnalysisPipeline(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { AnalysisStateHolder() }
    single { BridgeStatusHolder() }
    single { InsightGenerator(get(), get(), get(), get(), get()) }
    single { ConversationLinker(get(), get(), get(), get()) }
    single { PredictionEngine(get(), get(), get()) }
}
```

## Claude Bridge Architecture

The Claude Bridge is a Ktor HTTP server running in Termux that wraps the Claude CLI for on-device AI processing.

### Endpoints

| Endpoint | Input | Output |
|----------|-------|--------|
| `POST /analyze` | transcript text | Rich JSON: activity, speakers, topics, sentiment, tags, key moment |
| `POST /cleanup` | raw transcript | Cleaned transcript preserving natural speech |
| `POST /link` | list of chunk summaries | Detected conversation links with types and explanations |
| `POST /predict` | weekly activity/topic/people summaries | Pattern-based predictions with confidence |
| `POST /daily-insight` | day's aggregated data | Narrative daily insight with highlight |
| `GET /health` | — | `{"status":"ok"}` |

### Fallback Strategy

When Claude Bridge is unavailable:
1. **STT**: WhisperKit still runs (on-device, no bridge needed)
2. **Cleanup**: TranscriptPostProcessor handles basic cleanup
3. **Analysis**: MobileBERT sentiment + KeywordExtractor + heuristic activity detection
4. **Linking**: Heuristic topic overlap + time pattern matching
5. **Insights**: Aggregation from available data (no narrative)
6. **Predictions**: Heuristic weekly pattern analysis

## Model Download Strategy

Models are NOT bundled in the APK. Instead:

1. On first launch or when user enables analysis → show "Download AI Models" card
2. Download via ModelManager with progress tracking
3. SpeechModelCatalog defines available models (tiny, base, small)
4. Models persist across app updates
5. User can delete models to free space

## What NOT To Do on 782G

1. **Don't run Whisper in real-time** — too slow, battery killer
2. **Don't use whisper-small or larger** — 782G can't keep up
3. **Don't run NLP during recording** — save all processing for analysis time
4. **Don't keep models in memory** — load on demand, release after analysis
5. **Don't skip the battery/charging check** — analysis is CPU-intensive
