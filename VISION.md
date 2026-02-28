# Murmur — The Idea

## The Problem

You live your entire day — eating, talking, meeting people, working, thinking out loud — but you never step back and see the full picture. You don't remember what time you ate yesterday. You don't realize you spend 3 hours a day talking to one person and 10 minutes with another. You don't notice that every Thursday evening you're in the same kind of conversation. You can't recall what topic dominated your week.

**Your life has patterns, but you're too busy living it to see them.**

No tool passively observes your day and tells you how you actually spend it. Calendars only track what you *plan*. Journals require effort and memory (both unreliable). Screen time trackers only see your phone, not your real life. What if your phone — the device always with you — could silently observe your day through audio and reconstruct *what actually happened*?

## What Murmur Does

Murmur is a **passive life intelligence system** that runs on your Android phone.

It continuously listens to your day and builds a complete picture of how you spend your time, who you interact with, what you talk about, and what patterns define your life.

### 1. Record (Always-On, Silent)
- Runs as a background service, recording ambient audio continuously
- Splits into lightweight 15-minute AAC chunks (~15 MB/hour)
- Handles real life: pauses during phone calls, survives reboots, respects battery
- You press record once and forget about it

### 2. Analyze (AI Pipeline)
- **Transcribes** audio using WhisperKit (on-device Whisper model — no data leaves your phone)
- **Identifies activities** — eating, meeting, working, commuting, idle silence, phone call
- **Detects people** — recognizes distinct voices, lets you tag them with names, builds profiles
- **Extracts topics** — what subjects are being discussed, what decisions are being made
- **Analyzes behavior** — emotional state, confidence, speech patterns, engagement level
- All via Claude (running locally through Termux bridge) with on-device MobileBERT fallback

### 3. Surface Insights (The Key Screen)
This is not a summary card. This is a **full behavioral intelligence report**.

For every analyzed chunk, the app produces deep insights:
- **Activity**: What was happening — eating, meeting, casual chat, working silently, commuting
- **People**: Who was present (by voice profile), how long they spoke, interaction dynamics
- **Topics**: What subjects were discussed, key points, decisions made
- **Behavior**: Emotional state, confidence level, engagement, speech patterns
- **Time allocation**: How much time spent on what, with whom

Over days and weeks, this accumulates into:
- **Daily timelines**: A full reconstruction of your day — when you ate, who you met, what you discussed, when you were alone
- **People map**: Who do you spend the most time with? What do you talk about with each person? How do your interactions differ?
- **Topic trends**: What subjects dominate your week? What's new? What keeps recurring?
- **Predictions**: "You ate at 1:15 PM the last 4 days — it's 1:10 PM now" → notification
- **Pattern detection**: "Every Monday after your standup, your confidence drops for 2 hours"

## Voice Profiles — Knowing Who You're With

This is a core feature. Murmur doesn't just hear words — it hears *voices*.

### How It Works
1. The AI detects distinct speakers in each audio chunk (speaker diarization)
2. Unknown voices show up as "Voice A", "Voice B", etc. with sample clips
3. You tag them: "Voice A" → "Rahul", "Voice B" → "Mom"
4. From that point on, Murmur knows when you're talking to Rahul, Mom, or a stranger
5. Each person gets a **profile** that accumulates over time:
   - Total time spent together
   - Common topics discussed
   - Your emotional state when talking to them
   - Frequency of interaction (daily? weekly? fading?)
   - Interaction style (do you lead the conversation? are you quieter with them?)

### Why This Matters
- See who actually gets your time (not who you *think* gets your time)
- Notice relationships that are fading (haven't talked to X in 2 weeks)
- Understand dynamics — you're confident with A but hesitant with B
- Track professional vs. personal time allocation

## The Insights Screen — Not Summaries, Intelligence

The insights screen is where everything comes together. It's not a list of one-liner descriptions. It's a **full intelligence dashboard**.

### Per-Chunk View
For every 15-minute window:
| Field | Example |
|-------|---------|
| **Activity** | Meeting with 3 people |
| **People** | Rahul, Priya, Unknown voice |
| **Topic** | Sprint planning, blocked tasks, deployment timeline |
| **Sentiment** | Frustrated → Confident (shifted mid-conversation) |
| **Key Moment** | "Speaker became noticeably more engaged when discussing the new architecture" |
| **Behavioral Tags** | "rapid speech", "code-switching", "interrupting", "leading discussion" |

### Daily View
- **Timeline**: Visual reconstruction of the entire day — blocks of activity, people, topics
- **Time breakdown**: 3h meetings, 1.5h solo work, 45min eating, 2h casual conversation
- **People summary**: 1.5h with Rahul (work), 30min with Mom (call), 45min with strangers (café)
- **Highlight**: Most significant moment or pattern of the day

### Weekly/Monthly View
- **Trends**: Topic frequency, people frequency, activity patterns
- **Predictions**: Based on patterns, what's likely to happen tomorrow
- **Anomalies**: Things that broke the pattern ("you didn't eat lunch on Wednesday — unusual")
- **Growth signals**: "Your confidence in meetings has increased 15% over 2 weeks"

## Linked Conversations — Memory That Spans Time

Every conversation is connected to past and future.

When Murmur analyzes a chunk, it doesn't treat it in isolation. It links:
- **Same person**: "Last time you talked to Rahul (2 days ago), you discussed the deployment bug. Today you discussed it again — still unresolved."
- **Same topic**: "This is the 4th time this week the team discussed the migration. Total time spent: 3.5 hours."
- **Same time slot**: "Every day at 2 PM you're in a meeting. Today is different — you were eating alone."
- **Cause and effect**: "After your standup meetings, your sentiment drops for the next hour. After calls with Mom, it rises."

This is what makes Murmur more than a recorder. It builds a **continuous narrative of your life** where every new conversation adds context to past ones and informs predictions about future ones.

## What Makes This Different

**It's not a transcription app.** Transcription is raw material. Murmur consumes it and produces behavioral intelligence — activities, people, topics, patterns, predictions.

**It's not a mood tracker.** You don't self-report anything. The AI observes everything and tells you what it sees.

**It's not a voice recorder.** You're never going to play back audio. The recordings get consumed by the pipeline and turned into structured behavioral data. Audio is disposable.

**It's not a calendar.** Calendars show what you *planned*. Murmur shows what *actually happened*.

**It's a life observer.** It sees your day the way an attentive, always-present assistant would — noticing who you met, what you discussed, when you ate, how you felt, and how today connects to yesterday.

**It's privacy-first.** Everything runs on-device or through a local bridge. No cloud APIs. No data leaving your device. Your life stays yours.

## The Core Insight

> You don't know how you spend your day. Not really. If you could see it from the outside — who you talked to, what you discussed, when you ate, how your mood shifted, what patterns repeat — you'd understand yourself in ways that no amount of self-reflection can achieve.

Murmur is that outside view.

## Who It's For

- **Anyone who wants to understand their daily life** without the effort of logging or journaling
- **People who want to see their real patterns** — where their time goes, who they spend it with
- **Professionals** tracking how they allocate time across meetings, people, and topics
- **People managing relationships** who want to see who actually gets their attention
- **Quantified-self enthusiasts** who track steps and sleep but not their conversations and social life
- **Anyone curious about predictions** — what will I probably do tomorrow based on my patterns?

## The Pipeline (Technical)

```
Microphone
  │
  ▼
RecordingService (foreground, 15-min AAC chunks)
  │
  ▼
AnalysisWorker (triggered on schedule, charging, or manually)
  │
  ├─ AudioDecoder (M4A → PCM)
  │
  ├─ WhisperKit (on-device STT, tiny/base model)
  │
  ├─ Claude Bridge (Termux, localhost:8735)
  │   ├─ /cleanup  → fix transcription artifacts, preserve hesitations
  │   ├─ /analyze  → full behavioral analysis:
  │   │     • Activity detection (eating, meeting, working, etc.)
  │   │     • Speaker identification (voice profiles)
  │   │     • Topic extraction
  │   │     • Sentiment & behavioral tags
  │   │     • Cross-reference with historical data
  │   └─ /predict  → pattern-based predictions & notifications
  │
  ├─ [Fallback] MobileBERT + rule-based extraction
  │
  ▼
Room Database
  ├─ Transcriptions (raw text, per chunk)
  ├─ Activities (detected activity per chunk)
  ├─ Voice Profiles (speaker embeddings + user-assigned names)
  ├─ Topics (extracted subjects, linked across conversations)
  ├─ Insights (sentiment, tags, summaries, predictions)
  └─ Linked Conversations (cross-references between chunks)
       │
       ▼
  UI Screens:
  ├─ Home (recording controls, quick status)
  ├─ Recordings (chunk list with activity/people/topic tags)
  ├─ Insights (full intelligence dashboard — daily/weekly/monthly)
  ├─ People (voice profiles, interaction history, relationship map)
  └─ Stats (trends, predictions, anomalies)
```

## Data Model (Room DB)

Everything stored locally. The key entities:

- **Chunk**: 15-min audio segment with transcription and analysis results
- **VoiceProfile**: A distinct speaker — embeddings for matching + user-assigned name/photo
- **PersonInteraction**: Links a chunk to a voice profile — duration, speaking time, role
- **Topic**: Extracted subject with keywords — linked across multiple chunks
- **Activity**: What was happening (eating, meeting, working, idle, commuting)
- **DailyInsight**: Aggregated daily summary — timeline, time breakdown, highlights
- **Prediction**: Pattern-based prediction with confidence — "likely to eat at 1:15 PM"
- **ConversationLink**: Cross-reference between chunks — same person, same topic, cause-effect

## Where We Are Now

**Fully implemented and compiling:**
- Background recording with chunking, interruption handling, reboot survival
- WhisperKit transcription (tiny model, ~45s per 15-min chunk)
- Claude Bridge (Termux, localhost:8735) with rich structured analysis
  - /analyze → activity, speakers, topics, sentiment, behavioral tags, key moments
  - /cleanup → transcript post-processing
  - /link → cross-chunk conversation linking
  - /predict → pattern-based predictions
  - /daily-insight → aggregated daily narrative
- On-device fallback: MobileBERT + KeywordExtractor + TranscriptPostProcessor
- **Activity detection**: eating, meeting, working, commuting, idle, phone_call, casual_chat, solo
- **Speaker identification**: distinct voices per chunk, voice profiles, user tagging
- **Topic extraction**: subjects linked across conversations with key points
- **Insights screen**: full intelligence dashboard (Daily/Weekly/Transcripts tabs)
- **People screen**: voice profiles with interaction history, relationship tracking
- **Linked conversations**: chunks connected by person, topic, time, cause-effect
- **Daily timeline**: visual reconstruction with activity blocks, speakers, topics
- **Predictions & notifications**: routine, anomaly, relationship, habit predictions
- **Stats trends**: 7-day activity trends, 30-day sentiment trends, top people/topics
- Room DB v3 with 12 entities, 11 DAOs, full migration path
- Battery-aware scheduling with safety guards
- 5-tab navigation: Home, Recordings, Insights, People, Stats

**What's Next:**
1. Deploy and test on OnePlus Nord CE 3
2. Real-world Claude Bridge testing (Termux + Claude CLI)
3. Voice embedding-based speaker matching (true diarization)
4. Long-duration battery drain profiling
5. OxygenOS kill resilience testing
6. Buddy integration for remote build/deploy

## The Vision

Murmur starts as a passive observer. It becomes a **complete life intelligence system**.

It doesn't just know how you *sound*. It knows how you *live* — who you spend time with, what you talk about, when you do things, how your days connect to each other, and what's likely to happen tomorrow.

Not by asking you to log anything. Not by tracking your screen. By listening to the one constant in your life — your voice and the voices around you — and building a complete picture from that single signal.

**Your phone already hears everything. Murmur makes it understand.**
