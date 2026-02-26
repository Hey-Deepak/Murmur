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

## AI Stack

### Layer 1: Speech-to-Text

**Primary: Whisper-tiny via TFLite (Batch)**
- Model: `whisper-tiny.en.tflite` (~75 MB)
- Task: Transcribe 15-min audio chunks to text
- Speed: ~45 seconds per 15-min chunk on 782G
- Quality: Good for English, acceptable for Indian English accents
- When: During scheduled analysis time (batch all day's chunks)

**Optional: Vosk (Real-time)**
- Model: `vosk-model-small-en-in` (~50 MB, Indian English tuned)
- Dependency: `com.alphacephei:vosk-android:0.3.32`
- Task: Live captions while recording (user toggleable)
- Speed: Real-time streaming
- Quality: Good for live, less accurate than Whisper
- When: While recording (if user enables)
- Battery impact: Additional ~3-5% per hour

### Layer 2: Sentiment Analysis

**MobileBERT via TFLite**
- Model: `mobilebert-sentiment.tflite` (~25 MB)
- Dependency: `org.tensorflow:tensorflow-lite-task-text:0.4.4`
- Task: Classify each transcript → positive / negative / neutral + confidence score
- Speed: ~200ms per transcript on 782G
- When: Immediately after transcription of each chunk

### Layer 3: Keyword Extraction (Rule-Based)

**No ML model needed — pure Kotlin**
- Person names: Capitalized word detection + frequency
- Food/order mentions: Dictionary of food-related words
- Time patterns: Regex for time expressions ("at 3 PM", "tomorrow")
- Action items: Pattern matching ("need to", "should", "have to", "don't forget")
- Topic frequency: TF-IDF style scoring across day's transcripts
- Speed: Instant (<50ms per transcript)

### Layer 4: Daily Summary (Rule-Based)

**Template engine — no ML model**
- Input: All transcripts + sentiments + keywords for the day
- Output: Natural language summary using templates

```
Templates:
- "You had {N} recording sessions today, totaling {duration}."
- "Mood: {morning_sentiment} morning, {afternoon_sentiment} afternoon."
- "You mentioned {keyword} {count} times."
- "Most talked about: {top_topics}."
- "You typically {pattern} around {time} — today was {same/different}."
```

### Layer 5: Pattern Detection (7+ days data)

**Statistical analysis — no ML model**
- Routine detection: Cluster daily timestamps for recurring events
- Mood trends: 7-day moving average of sentiment scores
- Contact frequency: Who is mentioned most often
- Habit tracking: Recurring keyword patterns (e.g., "gym" at 7 AM)
- Predictions: "You usually order food at 1:15 PM" based on keyword+time clusters

## Model Storage Budget

| Model | Size | Required |
|-------|------|----------|
| Whisper-tiny TFLite | 75 MB | Phase 2 |
| Vosk Indian English | 50 MB | Phase 2 (optional) |
| MobileBERT Sentiment | 25 MB | Phase 2 |
| Rule-based engines | 0 MB | Phase 2 |
| **Total** | **~150 MB** | |

## Processing Budget (Full Day)

Assuming 16-hour recording day = 64 chunks × 15 minutes:

| Step | Per Chunk | All 64 Chunks | Battery |
|------|-----------|---------------|---------|
| Whisper-tiny transcription | ~45s | ~48 min | ~5-8% |
| MobileBERT sentiment | ~200ms | ~13 sec | negligible |
| Keyword extraction | ~50ms | ~3 sec | negligible |
| Daily summary | — | instant | negligible |
| **Total** | | **~50 min** | **~5-8%** |

## Implementation Plan

### File Structure (Phase 2)

```
com.dc.murmur.ai/
├── stt/
│   ├── WhisperTranscriber.kt     # TFLite Whisper-tiny batch processing
│   ├── VoskTranscriber.kt        # Vosk real-time STT (optional)
│   └── TranscriberInterface.kt   # Common interface
├── nlp/
│   ├── SentimentAnalyzer.kt      # MobileBERT TFLite
│   └── KeywordExtractor.kt       # Rule-based extraction
├── summary/
│   └── DailySummaryGenerator.kt  # Template-based summaries
├── patterns/
│   └── PatternDetector.kt        # Statistical pattern analysis
├── AnalysisWorker.kt             # WorkManager worker
└── AnalysisScheduler.kt          # Schedule management
```

### Koin Modules (Phase 2 additions)

```kotlin
val aiModule = module {
    single { WhisperTranscriber(androidContext()) }
    single { VoskTranscriber(androidContext()) }
    single { SentimentAnalyzer(androidContext()) }
    single { KeywordExtractor() }
    single { DailySummaryGenerator() }
    single { PatternDetector(get()) }
    single { AnalysisScheduler(androidContext(), get()) }
}
```

### Dependencies (Phase 2 additions to libs.versions.toml)

```toml
[versions]
vosk = "0.3.32"
tensorflowLite = "2.14.0"
tensorflowLiteSupport = "0.4.4"
tensorflowLiteTaskText = "0.4.4"

[libraries]
vosk-android = { group = "com.alphacephei", name = "vosk-android", version.ref = "vosk" }
tensorflow-lite = { group = "org.tensorflow", name = "tensorflow-lite", version.ref = "tensorflowLite" }
tensorflow-lite-support = { group = "org.tensorflow", name = "tensorflow-lite-support", version.ref = "tensorflowLiteSupport" }
tensorflow-lite-task-text = { group = "org.tensorflow", name = "tensorflow-lite-task-text", version.ref = "tensorflowLiteTaskText" }
```

## Model Download Strategy

Models are NOT bundled in the APK (would make it 150+ MB). Instead:

1. On first launch or when user enables analysis → show "Download AI Models" card
2. Download models to `Documents/Murmur/models/` using WorkManager
3. Show progress: "Downloading speech model... 45/75 MB"
4. Models persist across app updates (external storage)
5. User can delete models to free space (disable analysis)

```
Documents/Murmur/
├── models/
│   ├── whisper-tiny.tflite        (75 MB)
│   ├── vosk-model-small-en-in/    (50 MB)
│   └── mobilebert-sentiment.tflite (25 MB)
```

## What NOT To Do on 782G

1. **Don't run Whisper in real-time** — too slow, battery killer
2. **Don't use whisper-small or larger** — 782G can't keep up
3. **Don't run NLP during recording** — save all processing for analysis time
4. **Don't keep models in memory** — load on demand, release after analysis
5. **Don't skip the battery/charging check** — analysis is CPU-intensive
