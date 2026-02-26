# Murmur — Analysis Scheduler Design

## Overview

The user decides when Murmur processes their recorded audio. Analysis is NOT limited to nighttime — the user picks any time, or triggers it manually.

## Three Scheduling Modes

### 1. Fixed Time (⏰)

User picks a specific time of day. Analysis runs daily at that time.

**Settings:**
- Time picker (hour + minute + AM/PM)
- Day selector (Mon–Sun toggle chips)

**Implementation:**
```kotlin
// WorkManager PeriodicWorkRequest
val dailyWork = PeriodicWorkRequestBuilder<AnalysisWorker>(24, TimeUnit.HOURS)
    .setInitialDelay(calculateDelayUntil(userHour, userMinute), TimeUnit.MILLISECONDS)
    .addTag(AppConstants.ANALYSIS_SCHEDULED_WORKER_TAG)
    .setConstraints(constraints)
    .build()
```

**When user changes time:**
- Cancel existing scheduled work
- Recalculate initial delay to next occurrence
- Enqueue new PeriodicWorkRequest

### 2. When Charging (🔌)

Analysis starts automatically whenever the phone is plugged in AND unprocessed chunks exist.

**Implementation:**
```kotlin
// BroadcastReceiver for ACTION_POWER_CONNECTED
// OR WorkManager with Constraints.Builder().setRequiresCharging(true)
val chargingWork = OneTimeWorkRequestBuilder<AnalysisWorker>()
    .setConstraints(
        Constraints.Builder()
            .setRequiresCharging(true)
            .build()
    )
    .addTag(AppConstants.ANALYSIS_WORKER_TAG)
    .build()
```

### 3. Manual Only (👆)

User taps "Analyze Now" button in the app. No automatic scheduling.

**Implementation:**
```kotlin
// Direct OneTimeWorkRequest from UI
val manualWork = OneTimeWorkRequestBuilder<AnalysisWorker>()
    .addTag(AppConstants.ANALYSIS_WORKER_TAG)
    .build()
WorkManager.getInstance(context).enqueueUniqueWork(
    "manual_analysis",
    ExistingWorkPolicy.KEEP,
    manualWork
)
```

## Safety Conditions (Apply to ALL modes)

These checks run BEFORE analysis starts:

| Condition | Setting | Default | Behavior |
|-----------|---------|---------|----------|
| Require charging | Toggle | ON | Skip analysis if not plugged in |
| Minimum battery | 15% / 20% / 30% | 20% | Skip analysis if below threshold |
| Unprocessed chunks exist | Auto | — | Skip if nothing to process |

```kotlin
// In AnalysisWorker.doWork()
fun shouldRunAnalysis(): Boolean {
    val battery = batteryUtil.getBatteryLevel()
    val isCharging = batteryUtil.isCharging()
    val settings = settingsRepo.getAnalysisSettings()

    if (settings.requireCharging && !isCharging) return false
    if (battery < settings.minBattery) return false
    if (recordingRepo.getUnprocessedChunkCount() == 0) return false

    return true
}
```

**If conditions not met:**
- Log the skip reason
- For fixed_time: retry next scheduled time
- For on_charging: retry next time phone is plugged in
- Show a subtle notification: "Analysis skipped — battery too low"

## Analysis Worker Flow

```
AnalysisWorker.doWork()
    │
    ├─ Check safety conditions → skip if not met
    │
    ├─ Show progress notification: "Analyzing... 0/N chunks"
    │
    ├─ Get all unprocessed chunks from Room
    │
    ├─ For each chunk:
    │   ├─ Update notification: "Analyzing... X/N chunks"
    │   ├─ [Phase 2] Whisper-tiny → transcribe
    │   ├─ [Phase 2] MobileBERT → sentiment
    │   ├─ [Phase 2] Rule-based → keywords
    │   ├─ Save TranscriptionEntity to Room
    │   └─ Mark chunk as processed
    │
    ├─ [Phase 2] DailySummaryGenerator → create summary
    │
    ├─ Show insights notification: "Your daily insights are ready 🎯"
    │
    └─ Return Result.success()
```

## DataStore Preferences

```kotlin
// Analysis settings stored in DataStore
data class AnalysisSettings(
    val enabled: Boolean = true,
    val mode: AnalysisMode = AnalysisMode.FIXED_TIME,
    val hour: Int = 22,           // 10 PM default
    val minute: Int = 0,
    val days: Set<String> = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val requireCharging: Boolean = true,
    val minBattery: Int = 20
)

enum class AnalysisMode {
    FIXED_TIME,
    ON_CHARGING,
    MANUAL
}
```

## Settings UI Components

### Analysis Section (in Stats/Settings screen)

1. **Auto Analysis toggle** — Master on/off switch
2. **Trigger Mode** — Radio group: Fixed Time / When Charging / Manual
3. **Time Picker** — M3 TimePicker (visible only for Fixed Time mode)
4. **Day Selector** — Circular day chips Mon–Sun (visible only for Fixed Time mode)
5. **Safety Conditions** — Require charging toggle + Min battery selector (15/20/30%)
6. **Analyze Now** button — Always visible, shows unprocessed count
7. **Analysis Progress** — Inline progress card when analysis is running

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Phone turned off at scheduled time | WorkManager will run when phone turns on (approximate) |
| Analysis running + phone unplugged | Continue if battery > minBattery, else pause |
| User starts recording during analysis | Both can run simultaneously (different threads) |
| User changes schedule while analysis running | Current analysis continues, new schedule takes effect next time |
| Midnight crosses during analysis | Analysis continues, chunks are dated by their original recording date |
| No unprocessed chunks | Skip silently, no notification |
| Analysis fails mid-way | Mark failed chunk, continue with next, report partial results |
