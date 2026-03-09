# Rust for Android Engineers: AI Inferencing Guide

## Why Rust for AI on Android?

As an Android engineer, you already deal with native code via NDK/JNI for performance-critical work. Rust replaces C/C++ with:

- **Memory safety without GC** — no null pointers, no use-after-free, no data races
- **C-level performance** — zero-cost abstractions, no runtime overhead
- **First-class Android NDK support** — official Rust tier-2 targets for ARM/x86 Android
- **Growing ML ecosystem** — Candle (Hugging Face), Burn, ONNX Runtime bindings, tract

The pitch: write your inference engine once in Rust, expose it to Kotlin via JNI, and get 2-10x speedup over Java/Kotlin for compute-heavy workloads.

---

## Part 1: Rust Fundamentals (Kotlin → Rust Mental Model)

### 1.1 Ownership — The One Big Idea

In Kotlin, the GC tracks references. In Rust, **the compiler tracks ownership at compile time**.

```kotlin
// Kotlin — GC handles this
val list = mutableListOf(1, 2, 3)
val alias = list  // both point to same object, GC will clean up
```

```rust
// Rust — ownership transfer (move)
let list = vec![1, 2, 3];
let alias = list;       // `list` is MOVED into `alias`
// println!("{:?}", list);  // COMPILE ERROR: `list` was moved
println!("{:?}", alias);    // works fine
```

**Key rules:**
1. Each value has exactly one owner
2. When the owner goes out of scope, the value is dropped (freed)
3. You can **borrow** (reference) without taking ownership

```rust
fn print_len(data: &Vec<i32>) {  // borrows immutably
    println!("len = {}", data.len());
}

let list = vec![1, 2, 3];
print_len(&list);        // borrow
println!("{:?}", list);  // still valid — we only lent it
```

### 1.2 Mapping Kotlin Concepts to Rust

| Kotlin | Rust | Notes |
|--------|------|-------|
| `val x: Int = 5` | `let x: i32 = 5;` | Immutable by default in both |
| `var x = 5` | `let mut x = 5;` | `mut` = mutable |
| `data class` | `struct` + `#[derive(...)]` | See below |
| `sealed class` | `enum` | Rust enums are algebraic types (way more powerful) |
| `interface` | `trait` | Very similar concept |
| `fun foo(x: Int): String` | `fn foo(x: i32) -> String` | Return type after `->` |
| `x?.let { }` | `if let Some(v) = x { }` | `Option<T>` instead of nullability |
| `try/catch` | `Result<T, E>` + `?` operator | No exceptions — errors are values |
| `null` | `None` (in `Option<T>`) | No null at all |
| `List<T>` | `Vec<T>` | Growable array |
| `Map<K, V>` | `HashMap<K, V>` | Same concept |
| `suspend fun` | `async fn` | Needs a runtime (tokio) |
| `companion object` | `impl` block (associated functions) | `MyStruct::new()` |

### 1.3 Structs, Enums, Traits

```rust
// Like a Kotlin data class
#[derive(Debug, Clone)]
struct TranscriptSegment {
    text: String,
    start_ms: u64,
    end_ms: u64,
    confidence: f32,
}

// Like a Kotlin sealed class — but each variant can hold different data
enum InferenceResult {
    Success(Vec<TranscriptSegment>),
    PartialResult { segments: Vec<TranscriptSegment>, error: String },
    Error(String),
}

// Like a Kotlin interface
trait Transcriber {
    fn transcribe(&self, audio: &[f32]) -> InferenceResult;
    fn model_name(&self) -> &str;
}
```

### 1.4 Error Handling (No Exceptions)

```rust
use std::io;

// Result<T, E> replaces try/catch
fn load_model(path: &str) -> Result<Model, ModelError> {
    let bytes = std::fs::read(path)?;  // `?` propagates errors (like Kotlin's throw)
    let model = Model::from_bytes(&bytes)?;
    Ok(model)
}

// Custom error type (like a sealed class of errors)
#[derive(Debug)]
enum ModelError {
    IoError(io::Error),
    InvalidFormat(String),
    OutOfMemory,
}

// Usage
match load_model("whisper.bin") {
    Ok(model) => println!("Loaded: {}", model.name()),
    Err(ModelError::OutOfMemory) => eprintln!("Not enough RAM"),
    Err(e) => eprintln!("Failed: {:?}", e),
}
```

### 1.5 Iterators and Closures (Very Similar to Kotlin)

```rust
let scores: Vec<f32> = vec![0.9, 0.3, 0.7, 0.1, 0.8];

// Filter + map + collect — same patterns as Kotlin sequences
let high_confidence: Vec<f32> = scores.iter()
    .filter(|&&s| s > 0.5)
    .map(|&s| s * 100.0)
    .collect();

// Equivalent Kotlin:
// val highConfidence = scores.filter { it > 0.5f }.map { it * 100f }
```

---

## Part 2: Setting Up Rust for Android

### 2.1 Install Rust and Android Targets

```bash
# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Add Android targets
rustup target add aarch64-linux-android    # ARM64 (most devices)
rustup target add armv7-linux-androideabi  # ARMv7 (older devices)
rustup target add x86_64-linux-android     # x86_64 (emulator)
rustup target add i686-linux-android       # x86 (older emulator)

# Install cargo-ndk (simplifies building for Android)
cargo install cargo-ndk
```

### 2.2 Android NDK Setup

```bash
# Point to your NDK (from Android Studio SDK Manager)
export ANDROID_NDK_HOME=$HOME/Library/Android/sdk/ndk/27.0.12077973

# Or set in ~/.cargo/config.toml
```

Create `~/.cargo/config.toml` (or per-project `.cargo/config.toml`):

```toml
[target.aarch64-linux-android]
linker = "/Users/deepak/Library/Android/sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang"

[target.armv7-linux-androideabi]
linker = "/Users/deepak/Library/Android/sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi24-clang"

[target.x86_64-linux-android]
linker = "/Users/deepak/Library/Android/sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android24-clang"

[target.i686-linux-android]
linker = "/Users/deepak/Library/Android/sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android24-clang"
```

### 2.3 Project Structure

```
my-android-app/
├── app/                          # Android app (Kotlin)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/dc/mylib/
│       │   └── RustBridge.kt     # Kotlin JNI bindings
│       └── jniLibs/              # Compiled .so files go here
│           ├── arm64-v8a/
│           ├── armeabi-v7a/
│           └── x86_64/
├── rust-inference/                # Rust library
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs                # JNI exports
│       ├── model.rs              # Model loading/inference
│       └── audio.rs              # Audio preprocessing
```

---

## Part 3: Rust ↔ Android JNI Bridge

### 3.1 The Rust Side — Using `jni` Crate

`rust-inference/Cargo.toml`:
```toml
[package]
name = "rust-inference"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]  # Produces .so for Android

[dependencies]
jni = "0.21"             # JNI bindings
candle-core = "0.8"      # Hugging Face tensor library
candle-nn = "0.8"        # Neural network layers
candle-transformers = "0.8"  # Pre-built transformer models
serde = { version = "1", features = ["derive"] }
serde_json = "1"
log = "0.4"
android_logger = "0.14"  # Route Rust logs to Android logcat
```

`rust-inference/src/lib.rs`:
```rust
use jni::JNIEnv;
use jni::objects::{JClass, JString, JFloatArray, JObject};
use jni::sys::{jlong, jstring, jfloatArray, jint};
use std::sync::Mutex;

mod model;
mod audio;

use model::WhisperModel;

// Store model across JNI calls
static MODEL: Mutex<Option<WhisperModel>> = Mutex::new(None);

/// Initialize logging (call once from Application.onCreate)
#[no_mangle]
pub extern "system" fn Java_com_dc_inference_RustBridge_initLogging(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("RustInference"),
    );
    log::info!("Rust inference engine initialized");
}

/// Load a model from the given path. Returns a status code.
#[no_mangle]
pub extern "system" fn Java_com_dc_inference_RustBridge_loadModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_path: JString<'local>,
) -> jint {
    let path: String = match env.get_string(&model_path) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    log::info!("Loading model from: {}", path);

    match WhisperModel::load(&path) {
        Ok(m) => {
            let mut lock = MODEL.lock().unwrap();
            *lock = Some(m);
            log::info!("Model loaded successfully");
            0  // success
        }
        Err(e) => {
            log::error!("Failed to load model: {:?}", e);
            -2
        }
    }
}

/// Run inference on audio samples. Returns JSON string with results.
#[no_mangle]
pub extern "system" fn Java_com_dc_inference_RustBridge_transcribe<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    audio_samples: JFloatArray<'local>,
    sample_rate: jint,
) -> jstring {
    // Copy audio data from Java array into Rust Vec
    let len = env.get_array_length(&audio_samples).unwrap_or(0) as usize;
    let mut samples = vec![0f32; len];
    let _ = env.get_float_array_region(&audio_samples, 0, &mut samples);

    // Resample if needed
    let processed = audio::resample_if_needed(&samples, sample_rate as u32, 16000);

    // Run inference
    let lock = MODEL.lock().unwrap();
    let result = match lock.as_ref() {
        Some(model) => model.transcribe(&processed),
        None => Err("Model not loaded".into()),
    };

    // Convert result to JSON string
    let json = match result {
        Ok(segments) => serde_json::to_string(&segments).unwrap_or_default(),
        Err(e) => format!("{{\"error\": \"{}\"}}", e),
    };

    env.new_string(&json).unwrap().into_raw()
}

/// Free model resources
#[no_mangle]
pub extern "system" fn Java_com_dc_inference_RustBridge_unloadModel(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut lock = MODEL.lock().unwrap();
    *lock = None;
    log::info!("Model unloaded");
}
```

### 3.2 The Kotlin Side

```kotlin
package com.dc.inference

object RustBridge {
    init {
        System.loadLibrary("rust_inference")
    }

    external fun initLogging()
    external fun loadModel(modelPath: String): Int
    external fun transcribe(audioSamples: FloatArray, sampleRate: Int): String
    external fun unloadModel()
}

// Usage in a ViewModel or Repository:
class TranscriptionRepository {

    init {
        RustBridge.initLogging()
    }

    suspend fun loadModel(context: Context) = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "whisper-tiny.bin")
        val result = RustBridge.loadModel(modelFile.absolutePath)
        if (result != 0) throw RuntimeException("Model load failed: $result")
    }

    suspend fun transcribe(pcmSamples: FloatArray, sampleRate: Int): List<Segment> {
        return withContext(Dispatchers.Default) {
            val json = RustBridge.transcribe(pcmSamples, sampleRate)
            // Parse JSON into Kotlin data classes
            Json.decodeFromString<List<Segment>>(json)
        }
    }
}
```

### 3.3 Build Script

`build-android.sh`:
```bash
#!/bin/bash
set -e

cd rust-inference

# Build for all Android targets
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release

echo "Built .so files:"
find ../app/src/main/jniLibs -name "*.so" | head -20
```

Or integrate into Gradle:

```kotlin
// app/build.gradle.kts
tasks.register<Exec>("buildRustLibrary") {
    workingDir = file("../rust-inference")
    commandLine("cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", "${projectDir}/src/main/jniLibs",
        "build", "--release"
    )
}

tasks.named("preBuild") {
    dependsOn("buildRustLibrary")
}
```

---

## Part 4: AI Inference in Rust — The Ecosystem

### 4.1 Framework Comparison

| Framework | Best For | Android Ready | Notes |
|-----------|----------|---------------|-------|
| **Candle** (HuggingFace) | Transformers, Whisper, LLMs | Yes | Pure Rust, no C++ deps, great for mobile |
| **Burn** | Custom models, training+inference | Yes | Backend-agnostic (CPU/GPU/WGPU) |
| **tract** | ONNX model inference | Yes | Mature, production-ready ONNX runtime |
| **ort** | ONNX Runtime bindings | Partial | Wraps C++ ONNX Runtime, harder to cross-compile |
| **tch-rs** | PyTorch models | No | Requires libtorch — too heavy for mobile |

**Recommendation for Android: Start with Candle or tract.**

### 4.2 Candle — Running Whisper in Rust

```rust
// model.rs — Whisper inference with Candle
use candle_core::{Device, Tensor};
use candle_nn::VarBuilder;
use candle_transformers::models::whisper;
use serde::Serialize;

#[derive(Debug, Serialize)]
pub struct Segment {
    pub text: String,
    pub start_ms: u64,
    pub end_ms: u64,
}

pub struct WhisperModel {
    model: whisper::model::Whisper,
    tokenizer: tokenizers::Tokenizer,
    device: Device,
}

impl WhisperModel {
    pub fn load(model_dir: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let device = Device::Cpu;  // Use CPU on Android (GPU via Metal/WGPU later)

        // Load model weights
        let weights_path = format!("{}/model.safetensors", model_dir);
        let vb = unsafe {
            VarBuilder::from_mmaped_safetensors(
                &[weights_path],
                candle_core::DType::F32,
                &device,
            )?
        };

        // Load config
        let config_path = format!("{}/config.json", model_dir);
        let config: whisper::Config = serde_json::from_str(
            &std::fs::read_to_string(config_path)?
        )?;

        let model = whisper::model::Whisper::load(&vb, config)?;

        // Load tokenizer
        let tokenizer_path = format!("{}/tokenizer.json", model_dir);
        let tokenizer = tokenizers::Tokenizer::from_file(tokenizer_path)
            .map_err(|e| format!("tokenizer: {}", e))?;

        Ok(Self { model, tokenizer, device })
    }

    pub fn transcribe(&self, audio: &[f32]) -> Result<Vec<Segment>, Box<dyn std::error::Error>> {
        // Convert audio to mel spectrogram
        let mel = whisper::audio::pcm_to_mel(&self.model.config(), audio, &[]);
        let mel_len = mel.len();
        let n_mels = self.model.config().num_mel_bins;
        let mel = Tensor::from_vec(mel, (1, n_mels, mel_len / n_mels), &self.device)?;

        // Run encoder
        let encoder_output = self.model.encoder.forward(&mel, true)?;

        // Decode (greedy for speed on mobile)
        let mut segments = Vec::new();
        // ... decoding loop produces segments ...

        Ok(segments)
    }
}
```

### 4.3 Tract — Running ONNX Models

If you have models exported as ONNX (common for mobile deployment):

```toml
[dependencies]
tract-onnx = "0.21"
```

```rust
use tract_onnx::prelude::*;

pub struct OnnxModel {
    model: SimplePlan<TypedFact, Box<dyn TypedOp>, Graph<TypedFact, Box<dyn TypedOp>>>,
}

impl OnnxModel {
    pub fn load(path: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let model = tract_onnx::onnx()
            .model_for_path(path)?
            .with_input_fact(0, f32::fact([1, 80, 3000]).into())?  // [batch, mel_bins, frames]
            .into_optimized()?
            .into_runnable()?;

        Ok(Self { model })
    }

    pub fn infer(&self, input: &[f32]) -> Result<Vec<f32>, Box<dyn std::error::Error>> {
        let input_tensor = tract_ndarray::Array3::from_shape_vec(
            (1, 80, 3000),
            input.to_vec(),
        )?;

        let result = self.model.run(tvec!(input_tensor.into()))?;
        let output = result[0].to_array_view::<f32>()?;

        Ok(output.iter().copied().collect())
    }
}
```

### 4.4 Burn — Training & Inference with Backend Flexibility

Burn is interesting because it supports multiple backends (CPU, WGPU for GPU, etc.):

```toml
[dependencies]
burn = "0.16"
burn-ndarray = "0.16"  # CPU backend for Android
# burn-wgpu = "0.16"   # GPU backend (experimental on Android)
```

```rust
use burn::prelude::*;
use burn::module::Module;
use burn_ndarray::NdArrayBackend;

type Backend = NdArrayBackend<f32>;

// Define a simple classifier
#[derive(Module, Debug)]
pub struct AudioClassifier<B: burn::tensor::backend::Backend> {
    linear1: burn::nn::Linear<B>,
    linear2: burn::nn::Linear<B>,
    activation: burn::nn::Relu,
}

impl<B: burn::tensor::backend::Backend> AudioClassifier<B> {
    pub fn forward(&self, input: Tensor<B, 2>) -> Tensor<B, 2> {
        let x = self.linear1.forward(input);
        let x = self.activation.forward(x);
        self.linear2.forward(x)
    }
}

// Load and run
fn classify(model_path: &str, features: &[f32]) -> Result<Vec<f32>, Box<dyn std::error::Error>> {
    let model: AudioClassifier<Backend> =
        AudioClassifier::load(model_path, &Default::default())?;

    let input = Tensor::<Backend, 2>::from_floats(
        burn::tensor::Data::from(features),
        &Default::default(),
    );

    let output = model.forward(input);
    Ok(output.to_data().value)
}
```

---

## Part 5: Advanced Patterns

### 5.1 UniFFI — Auto-Generate Kotlin Bindings (No Manual JNI)

Mozilla's [UniFFI](https://mozilla.github.io/uniffi-rs/) generates Kotlin bindings automatically from Rust:

```toml
[dependencies]
uniffi = "0.28"

[build-dependencies]
uniffi = { version = "0.28", features = ["build"] }
```

Define your interface in `src/inference.udl`:
```
namespace inference {
    void init_logging();
};

dictionary TranscriptSegment {
    string text;
    u64 start_ms;
    u64 end_ms;
    f32 confidence;
};

[Error]
enum InferenceError {
    "ModelNotLoaded",
    "InvalidAudio",
    "InferenceFailed",
};

interface InferenceEngine {
    [Throws=InferenceError]
    constructor(string model_path);

    [Throws=InferenceError]
    sequence<TranscriptSegment> transcribe(sequence<float> audio, u32 sample_rate);

    void close();
};
```

UniFFI generates Kotlin code automatically:
```kotlin
// Auto-generated — just use it
val engine = InferenceEngine("/data/local/tmp/whisper.bin")
val segments = engine.transcribe(audioSamples.toList(), 16000u)
segments.forEach { println("${it.startMs}-${it.endMs}: ${it.text}") }
engine.close()
```

**This is the recommended approach for production apps.** No manual JNI.

### 5.2 Async Inference with Channels

```rust
use std::sync::mpsc;
use std::thread;

pub struct InferenceEngine {
    sender: mpsc::Sender<InferenceRequest>,
}

struct InferenceRequest {
    audio: Vec<f32>,
    response: mpsc::Sender<InferenceResult>,
}

impl InferenceEngine {
    pub fn new(model_path: &str) -> Self {
        let (tx, rx) = mpsc::channel::<InferenceRequest>();
        let path = model_path.to_string();

        // Dedicated inference thread — keeps model loaded
        thread::spawn(move || {
            let model = WhisperModel::load(&path).expect("model load");
            for req in rx {
                let result = model.transcribe(&req.audio);
                let _ = req.response.send(result);
            }
        });

        Self { sender: tx }
    }

    pub fn transcribe(&self, audio: Vec<f32>) -> InferenceResult {
        let (tx, rx) = mpsc::channel();
        self.sender.send(InferenceRequest { audio, response: tx }).unwrap();
        rx.recv().unwrap()
    }
}
```

### 5.3 SIMD Optimization (ARM NEON on Android)

Rust can auto-vectorize, but you can also use explicit SIMD:

```rust
#[cfg(target_arch = "aarch64")]
use std::arch::aarch64::*;

/// Fast audio normalization using ARM NEON
#[cfg(target_arch = "aarch64")]
pub fn normalize_audio_neon(samples: &mut [f32]) {
    let len = samples.len();
    let chunks = len / 4;

    // Find max absolute value
    let mut max_val = 0f32;
    for &s in samples.iter() {
        max_val = max_val.max(s.abs());
    }

    if max_val < 1e-8 {
        return;
    }

    let scale = 1.0 / max_val;
    unsafe {
        let scale_vec = vdupq_n_f32(scale);
        for i in 0..chunks {
            let offset = i * 4;
            let v = vld1q_f32(samples.as_ptr().add(offset));
            let normalized = vmulq_f32(v, scale_vec);
            vst1q_f32(samples.as_mut_ptr().add(offset), normalized);
        }
    }

    // Handle remaining samples
    for i in (chunks * 4)..len {
        samples[i] *= scale;
    }
}

// Fallback for non-NEON targets
#[cfg(not(target_arch = "aarch64"))]
pub fn normalize_audio_neon(samples: &mut [f32]) {
    let max_val = samples.iter().map(|s| s.abs()).fold(0f32, f32::max);
    if max_val > 1e-8 {
        let scale = 1.0 / max_val;
        samples.iter_mut().for_each(|s| *s *= scale);
    }
}
```

### 5.4 Memory-Mapped Model Loading (Critical for Mobile)

Large models should be memory-mapped, not fully loaded into RAM:

```rust
use memmap2::MmapOptions;
use std::fs::File;

pub fn load_model_mmap(path: &str) -> Result<&[u8], std::io::Error> {
    let file = File::open(path)?;
    let mmap = unsafe { MmapOptions::new().map(&file)? };

    // mmap is backed by the file — OS pages it in on demand
    // On a 500MB model, RSS might only be 50MB during inference
    Ok(&mmap[..])
}
```

---

## Part 6: Complete Example — Murmur-Style Audio Classifier

Here's a complete mini-project: a Rust library that classifies audio segments (speech vs. music vs. silence), callable from Android.

### Cargo.toml
```toml
[package]
name = "audio-classifier"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
jni = "0.21"
tract-onnx = "0.21"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
log = "0.4"
android_logger = "0.14"
```

### src/lib.rs
```rust
use jni::JNIEnv;
use jni::objects::{JClass, JString, JFloatArray};
use jni::sys::{jstring, jint};
use std::sync::Mutex;

mod classifier;
use classifier::AudioClassifier;

static CLASSIFIER: Mutex<Option<AudioClassifier>> = Mutex::new(None);

#[no_mangle]
pub extern "system" fn Java_com_dc_audioclassifier_NativeLib_loadModel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    model_path: JString<'local>,
) -> jint {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("AudioClassifier"),
    );

    let path: String = env.get_string(&model_path).unwrap().into();

    match AudioClassifier::new(&path) {
        Ok(c) => {
            *CLASSIFIER.lock().unwrap() = Some(c);
            0
        }
        Err(e) => {
            log::error!("Load failed: {}", e);
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_dc_audioclassifier_NativeLib_classify<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    audio: JFloatArray<'local>,
) -> jstring {
    let len = env.get_array_length(&audio).unwrap_or(0) as usize;
    let mut samples = vec![0f32; len];
    let _ = env.get_float_array_region(&audio, 0, &mut samples);

    let lock = CLASSIFIER.lock().unwrap();
    let result = match lock.as_ref() {
        Some(c) => c.classify(&samples),
        None => "error: model not loaded".to_string(),
    };

    env.new_string(&result).unwrap().into_raw()
}
```

### src/classifier.rs
```rust
use tract_onnx::prelude::*;

pub struct AudioClassifier {
    model: SimplePlan<TypedFact, Box<dyn TypedOp>, Graph<TypedFact, Box<dyn TypedOp>>>,
    labels: Vec<String>,
}

impl AudioClassifier {
    pub fn new(model_path: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let model = tract_onnx::onnx()
            .model_for_path(model_path)?
            .into_optimized()?
            .into_runnable()?;

        let labels = vec![
            "speech".into(),
            "music".into(),
            "silence".into(),
            "noise".into(),
        ];

        Ok(Self { model, labels })
    }

    pub fn classify(&self, audio: &[f32]) -> String {
        // Extract features (simplified — real impl would compute MFCCs)
        let features = self.extract_features(audio);

        let input = tract_ndarray::Array2::from_shape_vec(
            (1, features.len()),
            features,
        ).unwrap();

        match self.model.run(tvec!(input.into())) {
            Ok(result) => {
                let output = result[0].to_array_view::<f32>().unwrap();
                let max_idx = output.iter()
                    .enumerate()
                    .max_by(|a, b| a.1.partial_cmp(b.1).unwrap())
                    .map(|(i, _)| i)
                    .unwrap_or(0);

                let confidence = output[[0, max_idx]];
                format!(
                    "{{\"label\":\"{}\",\"confidence\":{}}}",
                    self.labels[max_idx], confidence
                )
            }
            Err(e) => format!("{{\"error\":\"{}\"}}", e),
        }
    }

    fn extract_features(&self, audio: &[f32]) -> Vec<f32> {
        // Compute basic audio features
        let rms = (audio.iter().map(|s| s * s).sum::<f32>() / audio.len() as f32).sqrt();
        let zero_crossings = audio.windows(2)
            .filter(|w| (w[0] >= 0.0) != (w[1] >= 0.0))
            .count() as f32 / audio.len() as f32;

        vec![rms, zero_crossings]  // Real version: 40+ MFCCs
    }
}
```

### Kotlin Side
```kotlin
object NativeLib {
    init { System.loadLibrary("audio_classifier") }
    external fun loadModel(modelPath: String): Int
    external fun classify(audio: FloatArray): String
}

// In your ViewModel
viewModelScope.launch(Dispatchers.Default) {
    val result = NativeLib.classify(audioChunk)
    val classification = Json.decodeFromString<Classification>(result)
    _uiState.update { it.copy(currentLabel = classification.label) }
}
```

---

## Part 7: Learning Path & Resources

### Recommended Order

1. **Rust Basics** (1-2 weeks)
   - [The Rust Book](https://doc.rust-lang.org/book/) — chapters 1-10 are essential
   - [Rustlings](https://github.com/rust-lang/rustlings) — exercises to drill syntax

2. **Rust + Android** (1 week)
   - Build a hello-world `.so` with `jni` crate
   - Try UniFFI for a simple struct round-trip
   - [Mozilla's UniFFI tutorial](https://mozilla.github.io/uniffi-rs/)

3. **ML in Rust** (ongoing)
   - [Candle examples](https://github.com/huggingface/candle/tree/main/candle-examples)
   - [Burn book](https://burn.dev/book/)
   - [tract docs](https://github.com/sonos/tract)

4. **Production Integration** (when ready)
   - Integrate into Murmur replacing C++ dependencies
   - Benchmark against current WhisperKit/ONNX approach

### Key Differences That Will Trip You Up

| Gotcha | Why | Fix |
|--------|-----|-----|
| "Cannot move out of borrowed content" | Tried to take ownership of borrowed data | Clone it, or restructure to avoid the move |
| "Lifetime does not live long enough" | Reference outlives the data it points to | Ensure data lives at least as long as references to it |
| "Cannot borrow as mutable more than once" | Two `&mut` refs to same data = data race | Restructure to use one mutable ref at a time, or use `Mutex` |
| `.unwrap()` panics in production | Like force-unwrapping in Swift | Use `?` operator or `match` for proper error handling |
| Slow debug builds | Rust debug mode is unoptimized | Always benchmark with `--release`; use `opt-level = 1` in dev profile for ML code |

---

## Quick Reference Card

```bash
# Create new library
cargo new --lib my-inference && cd my-inference

# Build for Android
cargo ndk -t arm64-v8a -o ./jniLibs build --release

# Run tests
cargo test

# Check without building (fast feedback)
cargo check

# Format code
cargo fmt

# Lint
cargo clippy

# Benchmark
cargo bench

# Generate docs
cargo doc --open
```

```rust
// The 5 things you'll write most often:

// 1. Function
fn process(input: &[f32]) -> Result<Vec<f32>, MyError> { ... }

// 2. Struct + impl
struct Engine { model: Model }
impl Engine {
    fn new(path: &str) -> Self { ... }
    fn infer(&self, data: &[f32]) -> Vec<f32> { ... }
}

// 3. Enum for errors/results
enum MyError { NotFound, InvalidData(String) }

// 4. Trait (interface)
trait Inferencer { fn infer(&self, input: &[f32]) -> Vec<f32>; }

// 5. Pattern matching
match result {
    Ok(val) => use_val(val),
    Err(MyError::NotFound) => log::warn!("not found"),
    Err(e) => return Err(e),
}
```
