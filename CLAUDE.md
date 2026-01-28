# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Guidelines

This project follows strict coding standards and testing requirements:
- **Coding Style**: See `.claude/rules/coding-style.md` for immutability patterns, Compose best practices, and JNI safety
- **Testing Requirements**: See `.claude/rules/testing.md` for TDD workflow and 80% coverage requirements

## Language Preference

**Communication Language**: Use Traditional Chinese (Taiwan, zh_TW) for all responses and documentation. Code comments, commit messages, and technical documentation should be written in Traditional Chinese.

## Python Environment

**IMPORTANT**: All Python scripts MUST be executed using `uv` virtual environment to isolate from system Python.

```bash
# Run Python scripts with uv
uv run python script_name.py

# Install Python dependencies with uv
uv pip install package_name

# Example: Running vocab generation script
uv run python scripts/fix_vocab_with_transformers.py

# Example: Running verification script
uv run python scripts/verify_bpe_decode.py
```

**Rationale**:
- Isolates project dependencies from system Python packages
- Ensures reproducible builds across different machines
- Prevents conflicts with system-wide Python installations
- The project already has a `.python-version` file and `pyproject.toml` configured for uv

**Never use**:
- ❌ `python script.py` (uses system Python)
- ❌ `python3 script.py` (uses system Python3)
- ❌ `pip install` (installs to system Python)

**Always use**:
- ✅ `uv run python script.py` (uses isolated environment)
- ✅ `uv pip install package` (installs to uv environment)
- ✅ `uv sync` (syncs dependencies from pyproject.toml)

## Build Commands

```bash
# Build all modules (includes C++ compilation for meeting-engine)
./gradlew build

# Build debug APK
./gradlew :app:assembleDebug

# Clean build
./gradlew clean
```

## Test Commands

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :meeting-core:test
./gradlew :meeting-engine:test

# Run a single test class
./gradlew :meeting-core:test --tests "com.edgemeeting.core.MeetingSessionStateTest"

# Run specific test in meeting-engine (useful for transcript tests)
./gradlew :meeting-engine:test --tests "com.edgemeeting.engine.RkMeetingSessionTranscriptTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew :app:connectedAndroidTest
```

### Important: Verify JAVA_HOME before running Gradle

JDK install paths vary across machines, and tool environments may not inherit your shell settings.
Verify a valid `JAVA_HOME` first, then run Gradle with the correct path:

```bash
echo "$JAVA_HOME"
JAVA_HOME="$("/usr/libexec/java_home")"
JAVA_HOME="$JAVA_HOME" ./gradlew :meeting-engine:test
```

## C++ Native Tests

C++ tests are compiled with Google Test but require execution on an Android device:

```bash
# C++ test binary is generated during build at:
# meeting-engine/.cxx/Debug/.../arm64-v8a/asr_tests

# Push and run C++ tests on connected device
adb push meeting-engine/.cxx/Debug/*/arm64-v8a/asr_tests /data/local/tmp/
adb shell "cd /data/local/tmp && chmod +x asr_tests && ./asr_tests"
```

Note: C++ tests are defined in `meeting-engine/src/main/cpp/test/` and test ASR/audio processing logic without JVM dependencies.

## Architecture

This is an Android SDK for meeting/communication with C++ audio processing via JNI.

### Module Structure

```
EdgeMeetingSDK/
├── app/                    # Demo application (Jetpack Compose UI)
│   └── Depends on: meeting-core, meeting-engine
├── meeting-core/           # Pure Kotlin interfaces and models
│   ├── No Android dependencies (can test on local JVM)
│   ├── Defines: MeetingSession, MeetingState, TranscriptSegment
│   └── Depends on: (none - pure Kotlin)
└── meeting-engine/         # Android Library with JNI bridge + C++ native code
    ├── Kotlin layer: RkMeetingSession, EngineBridge, ModelAssetManager
    ├── C++ layer: AudioRecorder, AudioProcessor, WhisperAsrEngine
    ├── Dependencies: Oboe (audio), RKNN Runtime (NPU), FFTW3 (DSP)
    └── Depends on: meeting-core
```

**Key Design Principles**:
- `meeting-core` is platform-agnostic and defines contracts only
- `meeting-engine` implements contracts and bridges Kotlin ↔ C++
- `app` is the integration point and should not contain business logic

### Key Patterns

**Bridge Pattern for JNI**: The `EngineBridge` interface (`meeting-engine/.../bridge/EngineBridge.kt`) provides a testable boundary between Kotlin and C++. `JniEngineBridge` is the real implementation; tests use fakes.

**State Machine**: `MeetingState` is a sealed interface with states: `Idle` → `Preparing` → `Ready` → `Listening` → `Error`. State transitions are exposed via `StateFlow`.

**Data Flow**:
```
MeetingSession (interface, meeting-core)
    ↓
RkMeetingSession (implementation, meeting-engine)
    ├─ Manages StateFlow<MeetingState>
    ├─ Manages SharedFlow<List<TranscriptSegment>>
    └─ Delegates to EngineBridge
        ↓
EngineBridge → JniEngineBridge → JNI (native-lib.cpp)
    ↓
C++ Native Layer (3 threads):
    ├─ [Thread 1] Oboe Audio Callback → RingBuffer.write()
    ├─ [Thread 2] AudioProcessor.workerLoop() → RingBuffer.read() → JNI callbacks
    └─ [Thread 3] WhisperAsrEngine.inferenceThread() → RKNN → JNI callbacks
        ↓
JNI Callbacks (back to Kotlin):
    ├─ onNativeAudioData(float[]) → UI waveform display
    ├─ onNativeTranscript(segment) → transcriptFlow.emit()
    └─ onNativeError(code, message) → state = Error
```

**Thread Safety**:
- RingBuffer uses `std::mutex` for thread-safe read/write
- JNI callbacks require `AttachCurrentThread()` when called from native threads
- `GlobalRef` is used for Kotlin objects passed to C++ (prevents GC, requires manual cleanup)
- Kotlin StateFlow/SharedFlow are thread-safe by design

### ASR/Whisper Integration

**On-Device Speech Recognition**: The SDK supports real-time ASR using OpenAI Whisper Base model accelerated by RK3588 NPU via RKNN Runtime.

**Strategy Pattern for ASR**: The `AsrEngine` abstract class (`meeting-engine/src/main/cpp/asr/AsrEngine.h`) defines a unified interface for ASR engines. `WhisperAsrEngine` implements this interface using RKNN-optimized Whisper models.

**ASR Lifecycle**:
```
init(modelsPath, language) → Load RKNN models (.rknn files)
    ├─ rknn_init() for encoder and decoder
    ├─ Load mel_filters (80×128 matrix)
    ├─ Load vocabulary (50257 tokens)
    └─ Set task_code based on language ("zh"→50260, "en"→50259)
    ↓
start() → Reset internal state, prepare for new audio stream
    ├─ Clear audioBuffer
    ├─ Reset silence detector state
    └─ Start background inference thread
    ↓
pushAudio(pcm, samples) → Accumulate audio, detect silence, trigger inference
    ├─ Append samples to audioBuffer (thread-safe with mutex)
    ├─ Calculate RMS energy for silence detection
    ├─ Classify: SILENCE / PAUSE / SPEECH
    └─ When buffer ≥ 3s or silence detected → queue inference request
        ↓ (Background thread processes queue)
        runInference() on separate thread
        ├─ audio_preprocess() → Mel Spectrogram
        ├─ RKNN encoder inference (~200-500ms)
        ├─ RKNN decoder inference (autoregressive, ~300-800ms)
        └─ JNI callback with TranscriptSegment
    ↓
stop() → Flush remaining audio, emit final transcript
    ├─ Process any remaining audio in buffer
    └─ Emit final segment with isFinal=true
    ↓
release() → Free RKNN context and model memory
    ├─ Stop background inference thread
    ├─ rknn_destroy() for both contexts
    └─ Free mel_filters and vocabulary
```

**ASR Data Flow with Whisper**:
```
AudioRecorder (Oboe) → PCM int16 @ 16kHz
    ↓
AudioProcessor → Forward to AsrEngine
    ↓
WhisperAsrEngine.pushAudio() → Accumulate 3-20s audio chunks
    ↓
WhisperUtils.audio_preprocess() → PCM → Mel Spectrogram (80 bands)
    ↓
RKNN Encoder → Mel → Hidden States (512-dim per frame)
    ↓
RKNN Decoder → Hidden States → Token IDs (autoregressive)
    ↓
Vocab Lookup → Token IDs → Text (with Base64 decode for Chinese)
    ↓
TranscriptCallback → JNI → Kotlin TranscriptSegment
    ↓
RkMeetingSession.transcriptFlow → UI
```

**Model Management**: `ModelAssetManager` handles automatic model deployment:
- Models packaged in `meeting-engine/src/main/assets/models/`
- On first run, copies to app-private storage (`context.filesDir/models/`)
- RKNN requires filesystem paths, not assets streams
- Required files: `whisper_encoder_base_20s.rknn`, `whisper_decoder_base_20s.rknn`, `vocab_en.txt`, `mel_80_filters.txt`
- **Important**: `vocab_en.txt` is Whisper's unified multilingual BPE vocabulary (51,864 tokens) shared across all languages (English, Chinese, Japanese, Korean, etc.)

**Language Support**:
- `LanguageSetting.Fixed("en")` - 英文（預設）- task_code=50259
- `LanguageSetting.Fixed("zh")` - 中文 - task_code=50260
- `LanguageSetting.Fixed("ja")` - 日文 - task_code=50266
- `LanguageSetting.Fixed("ko")` - 韓文 - task_code=50264
- Passed through: `AsrConfig.Whisper` → `EngineConfig` → JNI → `WhisperAsrEngine.init()`

**Audio Preprocessing** (`WhisperUtils.cpp`):
- FFT with Hann window (N_FFT=400, HOP_LENGTH=160)
- Mel filterbank (80 bands, 16kHz sample rate)
- Log scale conversion
- Output: Flattened 2D array (time × 80 mels) for RKNN input

**Dual Mode Operation**:
- Pure audio mode: `modelProvider = null` → Audio recording only, simulated transcripts
- ASR mode: `modelProvider = { ModelAssetManager.ensureModels(context) }` → Full Whisper ASR pipeline

**Silence Detection & Voice Activity Detection (VAD)**:
- Simplified energy-based VAD in `WhisperAsrEngine::pushAudio()`
- Calculate RMS (Root Mean Square) energy for each audio chunk
- Three states: `SILENCE` (very low energy), `PAUSE` (medium energy), `SPEECH` (high energy)
- Segment boundaries detected when transitioning from SPEECH → SILENCE/PAUSE
- Min segment length: 3 seconds (Whisper minimum input)
- Max segment length: 20 seconds (Whisper maximum input for this model)
- Energy thresholds are adaptive based on recent audio history

### C++ Native Code

Located in `meeting-engine/src/main/cpp/`:
- `native-lib.cpp` - JNI entry points and global resource management
  - Global variables: `gRecorder`, `gProcessor`, `gAsrEngine`, `gJavaVM`, `gCallbackObj`
  - JNI methods: `nativeInit()`, `nativeSetCallback()`, `nativeStart()`, `nativeStop()`, `nativeRelease()`
  - `JNI_OnLoad()` - Automatically called when library is loaded, caches JavaVM pointer
- `AudioRecorder.cpp/h` - Oboe-based audio capture (16kHz, PCM int16, mono)
  - Implements Oboe's `AudioStreamDataCallback`
  - Real-time audio callback on high-priority thread
- `AudioProcessor.cpp/h` - Worker thread with JNI callbacks
  - Reads from RingBuffer every 10ms
  - Converts float[-1,1] → int16[-32768,32767]
  - Forwards audio to AsrEngine and emits to Kotlin
- `RingBuffer.h` - Template-based circular buffer (thread-safe)
  - Used for producer (Oboe) → consumer (AudioProcessor) communication
  - No memory allocation during audio callback (pre-allocated buffer)
- `asr/AsrEngine.h` - Abstract ASR engine interface (strategy pattern)
  - Pure virtual methods: `init()`, `start()`, `pushAudio()`, `stop()`, `release()`
  - Future engines (Zipformer, etc.) can implement this interface
- `asr/WhisperAsrEngine.cpp/h` - Whisper implementation using RKNN Runtime
  - Uses PImpl idiom to hide RKNN implementation details
  - Background inference thread to avoid blocking audio recording
  - Manages RKNN contexts for encoder and decoder separately
- `asr/WhisperUtils.cpp/h` - Audio preprocessing (FFT, Mel Spectrogram)
  - FFT using FFTW3 library (N_FFT=400, HOP_LENGTH=160)
  - Hann window, Mel filterbank (80 bands), log scale
- `3rdparty/fftw/` - FFTW3 (Fast Fourier Transform) static library for audio preprocessing
  - Pre-compiled for arm64-v8a
- `test/` - Google Test unit tests for C++ components
  - `SampleTest.cpp`, `AsrEngineTest.cpp`, `WhisperAsrEngineTest.cpp`

**Build System**: CMake 3.22.1 with C++17
- Oboe linked via Prefab (find_package)
- RKNN Runtime: `librknnrt.so` in `jniLibs/arm64-v8a/` (SHARED IMPORTED)
- FFTW3: `libfftw3f.a` static library (linked directly)
- Google Test: FetchContent from GitHub (v1.14.0)

**Critical Memory Management**:
- **Global References**: Kotlin callback objects must be promoted to `GlobalRef` in JNI
  - Created: `env->NewGlobalRef(callback)` in `nativeSetCallback()`
  - Deleted: `env->DeleteGlobalRef(gCallbackObj)` in destructor or `nativeRelease()`
  - Failure to delete causes memory leaks
- **Thread Attachment**: Native threads must attach to JVM before JNI calls
  - `gJavaVM->AttachCurrentThread(&env, nullptr)`
  - `gJavaVM->DetachCurrentThread()` when thread exits
- **RKNN Resources**: Must call `rknn_destroy()` for each `rknn_init()`, otherwise NPU memory leaks

### Key Interfaces

**Kotlin Layer**:
- `MeetingSession` (`meeting-core/MeetingSession.kt`)
  - Main SDK interface with 4 lifecycle methods: `prepare()`, `start()`, `stop()`, `release()`
  - Exposes: `StateFlow<MeetingState>` and `Flow<List<TranscriptSegment>>`
  - Implementation: `RkMeetingSession` in `meeting-engine`

- `MeetingState` (`meeting-core/MeetingState.kt`) - Sealed interface
  - `Idle` - Initial state, no resources allocated
  - `Preparing` - Loading models (show progress UI)
  - `Ready` - Models loaded, ready to record
  - `Listening` - Actively recording and transcribing
  - `Error(code: Int, message: String)` - Error state with details

- `EngineBridge` (`meeting-engine/bridge/EngineBridge.kt`)
  - Abstract interface for JNI operations
  - Methods: `init(config)`, `setCallback()`, `startRecording()`, `stopRecording()`, `release()`
  - Returns: `BridgeResult` (Success or Failure with error code)
  - Real impl: `JniEngineBridge`, Test impl: `FakeEngineBridge`

- `EngineCallback` (`meeting-engine/bridge/EngineCallback.kt`)
  - Callback interface for C++ → Kotlin communication
  - Methods: `onAudioData(data: FloatArray)`, `onTranscript(segment: TranscriptSegment)`, `onError(code: Int, message: String)`

- `EngineConfig` (`meeting-engine/bridge/EngineConfig.kt`)
  - Data class wrapping `AsrConfig?` for passing to JNI
  - Used to configure the native engine

- `AsrConfig` (`meeting-core/AsrConfig.kt`) - Sealed class
  - `Whisper(modelsPath: String, language: LanguageSetting)` - Whisper configuration
  - Future: `Zipformer(...)`, `RemoteApi(...)`, etc.

- `LanguageSetting` (`meeting-core/LanguageSetting.kt`) - Sealed class
  - `Fixed(languageCode: String)` - Force specific language (e.g., "zh", "en")

- `BridgeResult` (`meeting-engine/bridge/BridgeResult.kt`) - Sealed class
  - `Success` - Operation succeeded
  - `Failure(code: Int, message: String)` - Operation failed with error details

- `ErrorCodes` (`meeting-engine/bridge/ErrorCodes.kt`) - Object with constants
  - `ERR_MODEL_NOT_FOUND = 1001` - Model files missing from assets
  - `ERR_MODEL_LOAD_FAILED = 1002` - RKNN initialization failed
  - `ERR_ASR_TYPE_UNSUPPORTED = 1003` - Unknown AsrConfig type
  - `ERR_AUDIO_FORMAT = 2001` - PCM format error
  - `ERR_UNKNOWN = 9999` - Generic error

**C++ Layer**:
- `AsrEngine` (`meeting-engine/src/main/cpp/asr/AsrEngine.h`)
  - Abstract base class defining ASR engine contract
  - Pure virtual methods: `init()`, `start()`, `pushAudio()`, `stop()`, `release()`
  - Callback typedef: `TranscriptCallback` for emitting results
  - Implementations: `WhisperAsrEngine`, future: `ZipformerAsrEngine`

### Testing Strategy

**Kotlin Unit Tests** (`meeting-core/src/test`, `meeting-engine/src/test`):
- **Test Environment**: Local JVM (no Android emulator needed for most tests)
- **Key Tools**:
  - `kotlinx-coroutines-test` - `runTest`, `StandardTestDispatcher`, `advanceTimeBy()`
  - `FakeEngineBridge` - Mock JNI layer, no C++ dependency
  - `FakeAsrEngine` (if testing) - Simulated ASR results

**Example Test Pattern**:
```kotlin
@Test
fun `prepare should transition from Idle to Ready`() = runTest {
    val bridge = FakeEngineBridge() // Mock C++ layer
    val session = RkMeetingSession(bridge, modelProvider = { "/fake/path" })

    // Observe state changes
    val states = mutableListOf<MeetingState>()
    backgroundScope.launch {
        session.state.collect { states.add(it) }
    }

    session.prepare()
    advanceUntilIdle() // Process all coroutines

    assertThat(states).containsExactly(
        MeetingState.Idle,
        MeetingState.Preparing,
        MeetingState.Ready
    ).inOrder()
}
```

**Transcript Flow Testing**:
- Use `TestScope` to control virtual time
- `RkMeetingSessionTranscriptTest` verifies transcript emission timing
- Pure audio mode (no ASR) uses `startTranscriptLoop()` for deterministic testing

**C++ Unit Tests** (`meeting-engine/src/main/cpp/test/`):
- **Framework**: Google Test (gtest, gtest_main)
- **Execution**: Must run on Android device (requires RKNN Runtime)
- **Test Files**:
  - `SampleTest.cpp` - Basic GTest examples
  - `AsrEngineTest.cpp` - Abstract interface tests
  - `WhisperAsrEngineTest.cpp` - Whisper-specific tests (requires models on device)

**Test Matrix**:
```
┌─────────────────┬─────────────┬──────────────┬──────────────┐
│ Layer           │ Test Type   │ Environment  │ Fake/Mock    │
├─────────────────┼─────────────┼──────────────┼──────────────┤
│ meeting-core    │ Unit        │ Local JVM    │ -            │
│ meeting-engine/ │ Unit        │ Local JVM    │ FakeEngine   │
│  Kotlin         │             │              │ Bridge       │
├─────────────────┼─────────────┼──────────────┼──────────────┤
│ meeting-engine/ │ Integration │ Android Dev  │ Spy (logging)│
│  C++            │             │ Device       │              │
├─────────────────┼─────────────┼──────────────┼──────────────┤
│ app/            │ E2E         │ Android Dev  │ Real models  │
│                 │             │ Device       │              │
└─────────────────┴─────────────┴──────────────┴──────────────┘
```

**Key Testing Files**:
- `MeetingSessionStateTest.kt` - State machine transitions
- `RkMeetingSessionStateTest.kt` - RK implementation with fake bridge
- `RkMeetingSessionTranscriptTest.kt` - Transcript flow timing (15s test with 10+ segments)
- `ModelAssetManagerTest.kt` - Asset copying and idempotency

## Tech Stack

- **Kotlin**: 2.3.0
- **Android**: API 33-36 (Android 13-15), target ABI: arm64-v8a only (RK3588 requirement)
- **UI**: Jetpack Compose with Material 3
- **Async**: Kotlin Coroutines with StateFlow/Flow
- **Native Audio**: Google Oboe 1.10.0
- **ASR Engine**: OpenAI Whisper Base (20s chunks) via RKNN Runtime
- **NPU Acceleration**: Rockchip RKNN Toolkit 2 (librknnrt.so for RK3588)
- **DSP**: FFTW 3 (Fast Fourier Transform for audio preprocessing)
- **Build**: Gradle with Kotlin DSL, CMake 3.22.1 for C++
- **Testing**: JUnit 4, Google Test (C++), kotlinx-coroutines-test

## Debugging

### Native Code Debugging

To debug C++ code with Android Studio:

1. Set breakpoints in C++ files
2. Run app in Debug mode (will attach LLDB debugger)
3. Native logs use `__android_log_print(ANDROID_LOG_DEBUG, "TAG", "message")`

### Common Issues

**Build Failures with RKNN**: Ensure `librknnrt.so` exists in `meeting-engine/src/main/jniLibs/arm64-v8a/`. Download from [RKNN Toolkit 2 releases](https://github.com/airockchip/rknn-toolkit2/releases) if missing.

**Model Not Found Errors**: Models are automatically copied from `assets/models/` to app-private storage on first `prepare()`. Check `ModelAssetManager` logs if issues occur.

**Memory Leaks**: Use LeakCanary (already included in app module) to detect memory leaks. Native memory leaks require manual tracking via RKNN/Oboe lifecycle methods.

## Build Configuration

### Gradle Configuration (`meeting-engine/build.gradle.kts`)

**Critical Settings**:
```kotlin
android {
    // Only build for arm64-v8a (RK3588 architecture)
    // RKNN Runtime only supports arm64-v8a
    ndk {
        abiFilters += "arm64-v8a"
    }

    // Enable Prefab for native dependencies (Oboe)
    buildFeatures {
        prefab = true
    }

    // CMake integration
    externalNativeBuild {
        cmake {
            path = "src/main/cpp/CMakeLists.txt"
            version = "3.22.1"
            // Required for Oboe: use shared libc++
            arguments("-DANDROID_STL=c++_shared")
        }
    }

    // Ensure librknnrt.so is included in APK
    sourceSets {
        main {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // Resolve libc++_shared.so conflicts (Oboe + RKNN both use it)
    packaging {
        jniLibs {
            pickFirsts.add("**/libc++_shared.so")
        }
    }
}
```

### CMake Configuration (`meeting-engine/src/main/cpp/CMakeLists.txt`)

**Key Dependencies**:
```cmake
# 1. Oboe (Google's low-latency audio library)
find_package(oboe REQUIRED CONFIG)
target_link_libraries(meeting-engine oboe::oboe)

# 2. RKNN Runtime (Rockchip NPU acceleration)
set(RKNN_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(RKNN_RUNTIME_LIB "${RKNN_LIB_DIR}/librknnrt.so")
if(EXISTS ${RKNN_RUNTIME_LIB})
    add_library(rknnrt SHARED IMPORTED)
    set_target_properties(rknnrt PROPERTIES IMPORTED_LOCATION ${RKNN_RUNTIME_LIB})
    target_link_libraries(meeting-engine rknnrt)
endif()

# 3. FFTW3 (Fast Fourier Transform for audio preprocessing)
set(FFTW_STATIC_LIB "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty/fftw/lib/${ANDROID_ABI}/libfftw3f.a")
target_link_libraries(meeting-engine ${FFTW_STATIC_LIB})

# 4. Google Test (for C++ unit tests)
include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.14.0
)
FetchContent_MakeAvailable(googletest)
enable_testing()
```

**Important Notes**:
- `librknnrt.so` must exist in `meeting-engine/src/main/jniLibs/arm64-v8a/`
- Download from [RKNN Toolkit 2 releases](https://github.com/airockchip/rknn-toolkit2/releases) if missing
- FFTW3 is statically linked (no separate .so file needed in APK)
- Oboe is automatically handled by Prefab (AGP downloads it from Maven)

## Performance Characteristics

**Real-Time Factor (RTF)**: < 0.3 on RK3588 NPU
- Processing 10s audio takes < 3s (encoder + decoder inference)
- First transcript appears within 2-3 seconds of speech start

**Audio Processing Latency**:
- Oboe callback: ~10-20ms (configurable buffer size)
- RingBuffer latency: <50ms
- Total audio path latency: <100ms from microphone to Kotlin callback

**Memory Usage**:
- Models in memory: ~40-60 MB (encoder + decoder RKNN contexts)
- Audio buffers: ~2-5 MB (ring buffer + ASR accumulation buffer)
- Total SDK overhead: ~50-70 MB

**Segment Timing**:
- Minimum segment: 3 seconds (Whisper requirement)
- Maximum segment: 20 seconds (model input limit)
- Typical segment: 5-10 seconds (based on natural speech pauses)

## Language Setting Flow

```
UI Layer (MainActivity)
    ↓
val languageSetting = LanguageSetting.Fixed("en")  // "zh", "ja", "ko" 等
    ↓
RkMeetingSession(bridge, modelProvider, languageSetting)
    ↓
AsrConfig.Whisper(modelsPath, language = languageSetting)
    ↓
EngineConfig(asrConfig)
    ↓
JniEngineBridge.init(config)
    ├─ Extract language string: "zh", "en", "ja", or "ko"
    └─ nativeInit(modelsPath, languageString)  [JNI call]
        ↓
WhisperAsrEngine::init(modelsPath, language) [C++]
    ├─ Load unified multilingual vocabulary: vocab_en.txt (所有語言共享)
    ├─ Set task_code based on language:
    │  ├─ if (language == "zh") → task_code = 50260  // 中文
    │  ├─ else if (language == "ja") → task_code = 50266  // 日文
    │  ├─ else if (language == "ko") → task_code = 50264  // 韓文
    │  └─ else → task_code = 50259  // 英文（預設）
    └─ task_code 控制 decoder 的語言輸出，BPE 詞彙表包含所有語言的 tokens
        ↓
WhisperAsrEngine::runInference()
    └─ Decoder uses task_code as initial prompt token
```
