# Murmur — Architecture Document

## System Architecture

```
┌──────────────────────────────────────────────────┐
│                   UI Layer                        │
│  ┌──────────┐  ┌────────────┐  ┌──────────────┐ │
│  │  Home     │  │ Recordings │  │    Stats     │ │
│  │  Screen   │  │   Screen   │  │   Screen     │ │
│  └────┬─────┘  └─────┬──────┘  └──────┬───────┘ │
│       │               │                │          │
│  ┌────┴─────┐  ┌─────┴──────┐  ┌──────┴───────┐ │
│  │  Home    │  │ Recordings │  │    Stats     │ │
│  │ViewModel │  │  ViewModel │  │  ViewModel   │ │
│  └────┬─────┘  └─────┬──────┘  └──────┬───────┘ │
├───────┼───────────────┼────────────────┼──────────┤
│       │          Data Layer            │          │
│  ┌────┴────────────────────────────────┴───────┐ │
│  │            Repositories                      │ │
│  │  ┌──────────────┐  ┌──────────────────────┐ │ │
│  │  │  Recording   │  │     Battery          │ │ │
│  │  │  Repository  │  │    Repository        │ │ │
│  │  └──────┬───────┘  └──────────┬───────────┘ │ │
│  │         │    ┌────────────────┐│             │ │
│  │         │    │   Settings     ││             │ │
│  │         │    │  Repository    ││             │ │
│  │         │    └───────┬────────┘│             │ │
│  └─────────┼────────────┼─────────┼─────────────┘ │
├────────────┼────────────┼─────────┼───────────────┤
│            │      Local Storage   │               │
│  ┌─────────┴────────────┴─────────┴────────────┐ │
│  │              Room Database                   │ │
│  │  ┌─────────────┐ ┌──────────┐ ┌───────────┐│ │
│  │  │  Chunk DAO  │ │Session   │ │Battery    ││ │
│  │  │             │ │  DAO     │ │Log DAO    ││ │
│  │  └─────────────┘ └──────────┘ └───────────┘│ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │           DataStore Preferences              │ │
│  └─────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────┤
│                Service Layer                      │
│  ┌──────────────────────────────────────────────┐│
│  │          RecordingService                     ││
│  │  (Foreground Service + MediaRecorder)         ││
│  │  ┌──────────────┐  ┌───────────────────────┐ ││
│  │  │ Chunk Timer  │  │ Audio Focus Manager   │ ││
│  │  └──────────────┘  └───────────────────────┘ ││
│  └──────────────────────────────────────────────┘│
│  ┌─────────────┐ ┌───────────┐ ┌──────────────┐ │
│  │CallState    │ │Boot       │ │Battery       │ │
│  │Receiver     │ │Receiver   │ │Worker        │ │
│  └─────────────┘ └───────────┘ └──────────────┘ │
└──────────────────────────────────────────────────┘
```

## Dependency Injection (Koin)

All dependencies are provided via Koin modules defined in `di/AppModule.kt`:

### Module Structure

```
databaseModule
  └─ MurmurDatabase (singleton)
  └─ RecordingChunkDao (singleton)
  └─ SessionDao (singleton)
  └─ BatteryLogDao (singleton)

utilModule
  └─ StorageUtil (singleton)
  └─ BatteryUtil (singleton)
  └─ NotificationUtil (singleton)

repositoryModule
  └─ RecordingRepository (singleton)
  └─ BatteryRepository (singleton)
  └─ SettingsRepository (singleton)

viewModelModule
  └─ HomeViewModel (viewModel factory)
  └─ RecordingsViewModel (viewModel factory)
  └─ StatsViewModel (viewModel factory)
```

### Accessing in Compose

```kotlin
// In Composable functions
val viewModel: HomeViewModel = koinViewModel()

// In Service (non-Compose)
val repo: RecordingRepository = get()  // via KoinComponent
```

## Recording Architecture

### Audio Pipeline

```
MIC → MediaRecorder → AAC Encoder → .m4a File → Room DB Entry
         │
         ├─ Sample Rate: 16 kHz
         ├─ Bit Rate: 32 kbps (configurable: 24/32/64)
         ├─ Channels: Mono
         ├─ Container: MPEG-4
         └─ Output: Documents/Murmur/recordings/YYYY-MM-DD/chunk_*.m4a
```

### Chunk Lifecycle

```
START
  │
  ├─ Create new MediaRecorder
  ├─ Log battery level (chunk_start)
  ├─ Start 15-min timer
  │
  │  ... recording ...
  │
  ├─ Timer fires OR interruption
  │
  ├─ Stop MediaRecorder
  ├─ Log battery level (chunk_end)
  ├─ Save RecordingChunkEntity to Room
  ├─ Calculate file size
  │
  ├─ Gap < 500ms
  │
  └─ Start next chunk (loop)
```

### Storage Estimates

| Quality | Bitrate | Per Hour | 16-hr Day | 7 Days | 30 Days |
|---------|---------|----------|-----------|--------|---------|
| Low     | 24 kbps | ~11 MB   | ~176 MB   | 1.2 GB | 5.2 GB  |
| Normal  | 32 kbps | ~15 MB   | ~240 MB   | 1.6 GB | 7.0 GB  |
| High    | 64 kbps | ~29 MB   | ~464 MB   | 3.2 GB | 13.5 GB |

## Call & Interruption Handling

### State Machine

```
                    RINGING/OFFHOOK
    RECORDING ──────────────────────► CALL_PAUSED
        ▲                                  │
        │                                  │
        │         IDLE (wait 2s)           │
        └──────────────────────────────────┘
               Save chunk with
           interrupted_by="phone_call"


                  AUDIOFOCUS_LOSS
    RECORDING ──────────────────────► FOCUS_PAUSED
        ▲                                  │
        │       AUDIOFOCUS_GAIN            │
        └──────────────────────────────────┘
               Save chunk with
          interrupted_by="audio_focus"
```

### Interruption Types

| Trigger | interrupted_by Value | Resume Delay |
|---------|---------------------|-------------|
| Incoming/outgoing call | `phone_call` | 2 seconds after IDLE |
| Another app takes audio | `audio_focus_loss` | Immediate on GAIN |
| User pause via notification | `user_paused` | Manual resume |
| Low storage (< 100 MB) | `low_storage` | No auto-resume |

## Battery Monitoring

### Logging Events

| Event | Trigger | Data Captured |
|-------|---------|---------------|
| `chunk_start` | New chunk begins | level, isCharging, temp |
| `chunk_end` | Chunk completes | level, isCharging, temp |
| `periodic` | WorkManager (30 min) | level, isCharging, temp |
| `charging_changed` | Plug/unplug detected | level, isCharging, temp |

### Expected Battery Impact (Snapdragon 782G)

- Recording only: ~2-3% per hour
- With periodic battery logging: negligible additional cost
- Analysis (Whisper-tiny): ~5-8% per hour (batch processing)

## Database Schema

### Version 1 (Phase 1)

**RecordingChunkEntity**
- id: Long (PK, auto)
- sessionId: String
- filePath: String
- fileName: String
- startTime: Long (epoch ms)
- endTime: Long (epoch ms)
- durationMs: Long
- fileSizeBytes: Long
- batteryLevelStart: Int
- batteryLevelEnd: Int
- interruptedBy: String? (nullable)
- date: String (YYYY-MM-DD, indexed)

**SessionEntity**
- id: String (PK, UUID)
- startTime: Long
- endTime: Long? (nullable, null if active)
- totalChunks: Int
- totalSizeBytes: Long
- totalDurationMs: Long
- batteryConsumed: Int

**BatteryLogEntity**
- id: Long (PK, auto)
- timestamp: Long
- batteryLevel: Int (0-100)
- isCharging: Boolean
- temperature: Float
- sessionId: String?
- event: String (chunk_start/chunk_end/periodic/charging_changed)

### Version 2 (Phase 2 — AI)

**TranscriptionEntity**
- id: Long (PK, auto)
- chunkId: Long (FK → RecordingChunkEntity)
- text: String
- language: String
- sentiment: String (positive/negative/neutral)
- sentimentScore: Float (0.0 - 1.0)
- keywords: String (JSON array)
- processedAt: Long
- modelUsed: String

## Navigation

```
NavHost (startDestination = "home")
  ├─ "home"       → HomeScreen
  ├─ "recordings" → RecordingsScreen
  └─ "stats"      → StatsScreen

Bottom Navigation: M3 NavigationBar with 3 items
  ├─ Home (mic icon)
  ├─ Recordings (headphones icon)
  └─ Stats (bar chart icon)
```

## Permissions Required

| Permission | Why | When Asked |
|-----------|-----|-----------|
| RECORD_AUDIO | Core recording | First launch |
| FOREGROUND_SERVICE | Background service | Auto (manifest) |
| FOREGROUND_SERVICE_MICROPHONE | Mic in foreground | Auto (manifest) |
| READ_PHONE_STATE | Detect calls | First launch |
| WAKE_LOCK | CPU during recording | Auto (manifest) |
| RECEIVE_BOOT_COMPLETED | Auto-restart | Auto (manifest) |
| POST_NOTIFICATIONS | Show notifications | First launch (Android 13+) |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Survive OxygenOS kills | First launch (dialog) |
| MANAGE_EXTERNAL_STORAGE | Save to Documents | First launch |
