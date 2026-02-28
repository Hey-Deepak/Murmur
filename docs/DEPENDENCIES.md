# Murmur — Dependencies

## Phase 1 Dependencies (Current)

### Core Android
| Library | Version | Purpose |
|---------|---------|---------|
| androidx.core:core-ktx | 1.15.0 | Kotlin extensions for Android |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | Lifecycle-aware coroutines |
| androidx.lifecycle:lifecycle-service | 2.8.7 | LifecycleService base class |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | ViewModel in Compose |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 | collectAsState extensions |

### Jetpack Compose + Material 3
| Library | Version | Purpose |
|---------|---------|---------|
| androidx.compose:compose-bom | 2024.12.01 | BOM for Compose versioning |
| androidx.compose.material3:material3 | (BOM) | Material Design 3 components |
| androidx.compose.material:material-icons-extended | (BOM) | Full icon set |
| androidx.activity:activity-compose | 1.9.3 | ComponentActivity + Compose |
| androidx.navigation:navigation-compose | 2.8.5 | Navigation with Compose |

### Room Database
| Library | Version | Purpose |
|---------|---------|---------|
| androidx.room:room-runtime | 2.6.1 | Room runtime |
| androidx.room:room-ktx | 2.6.1 | Coroutine Flow support |
| androidx.room:room-compiler | 2.6.1 | KSP annotation processor |

### Koin Dependency Injection
| Library | Version | Purpose |
|---------|---------|---------|
| io.insert-koin:koin-android | 3.5.6 | Android DI |
| io.insert-koin:koin-androidx-compose | 3.5.6 | koinViewModel() in Compose |

### Background Processing
| Library | Version | Purpose |
|---------|---------|---------|
| androidx.work:work-runtime-ktx | 2.10.0 | WorkManager for scheduled tasks |
| kotlinx-coroutines-android | 1.8.1 | Coroutine dispatchers |

### Data & UI
| Library | Version | Purpose |
|---------|---------|---------|
| androidx.datastore:datastore-preferences | 1.1.1 | Settings storage |
| com.patrykandpatrick.vico:compose | 1.13.1 | Charts in Compose |
| com.patrykandpatrick.vico:compose-m3 | 1.13.1 | M3-themed charts |

### Build Plugins
| Plugin | Version | Purpose |
|--------|---------|---------|
| com.android.application | 9.0.1 (AGP) | Android build |
| org.jetbrains.kotlin.plugin.compose | 2.0.21 | Compose compiler |
| com.google.devtools.ksp | 2.0.21-1.0.28 | Room annotation processing |

---

## Phase 2+ Dependencies (AI & Bridge)

### On-Device AI
| Library | Version | Purpose |
|---------|---------|---------|
| com.whisperkit:whisperkit-android | (bundled) | On-device WhisperKit STT |
| org.tensorflow:tensorflow-lite | 2.14.0 | TFLite runtime |
| org.tensorflow:tensorflow-lite-support | 0.4.4 | TFLite model loading |
| org.tensorflow:tensorflow-lite-task-text | 0.4.4 | MobileBERT sentiment |

### Claude Bridge (claude-bridge module)
| Library | Version | Purpose |
|---------|---------|---------|
| io.ktor:ktor-server-core | 2.3.7 | HTTP server framework |
| io.ktor:ktor-server-netty | 2.3.7 | Netty engine |
| io.ktor:ktor-server-content-negotiation | 2.3.7 | Content negotiation |
| io.ktor:ktor-serialization-kotlinx-json | 2.3.7 | JSON serialization |

### Network (App → Bridge)
| Library | Version | Purpose |
|---------|---------|---------|
| io.ktor:ktor-client-android | 2.3.7 | HTTP client for bridge calls |
| io.ktor:ktor-client-content-negotiation | 2.3.7 | Client content negotiation |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.6.2 | JSON parsing |

---

## Why These Choices

### Koin over Hilt
- No annotation processing → faster builds
- Pure Kotlin DSL → no generated code, easier to debug
- Lighter weight → smaller APK
- No kapt needed → KSP only (for Room)

### KSP over KAPT
- 2x faster compilation
- Better Kotlin support
- Room 2.6+ fully supports KSP
- Compatible with Kotlin 2.0+

### Vico over MPAndroidChart
- Compose-native (no AndroidView wrapping)
- Built-in Material 3 theme support
- Declarative API matches Compose philosophy
- Actively maintained

### DataStore over SharedPreferences
- Coroutine-based (non-blocking)
- Type safety with Preferences keys
- No apply/commit confusion
- Modern replacement recommended by Google

### Room over raw SQLite
- Compile-time SQL verification
- Flow/coroutine support built-in
- Migration support
- Less boilerplate
