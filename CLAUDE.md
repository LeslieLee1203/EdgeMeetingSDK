# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Preference

**Communication Language**: Use Traditional Chinese (Taiwan, zh_TW) for all responses and documentation. Code comments, commit messages, and technical documentation should be written in Traditional Chinese.

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
├── app/              # Demo application (Jetpack Compose UI)
├── meeting-core/     # Kotlin interfaces and models (pure Kotlin, no Android dependencies)
└── meeting-engine/   # JNI bridge + C++ native code (Oboe audio)
```

### Key Patterns

**Bridge Pattern for JNI**: The `EngineBridge` interface (`meeting-engine/.../bridge/EngineBridge.kt`) provides a testable boundary between Kotlin and C++. `JniEngineBridge` is the real implementation; tests use fakes.

**State Machine**: `MeetingState` is a sealed interface with states: `Idle` → `Preparing` → `Ready` → `Listening` → `Error`. State transitions are exposed via `StateFlow`.

**Data Flow**:
```
MeetingSession (interface, meeting-core)
    ↓
RkMeetingSession (implementation, meeting-engine)
    ↓
EngineBridge → JniEngineBridge → JNI
    ↓
C++ (AudioRecorder → RingBuffer → AudioProcessor)
    ↓
AudioCallback.onAudioData() back to Kotlin
```

### ASR/Whisper Integration

**On-Device Speech Recognition**: The SDK supports real-time ASR using OpenAI Whisper Base model accelerated by RK3588 NPU via RKNN Runtime.

**Strategy Pattern for ASR**: The `AsrEngine` abstract class (`meeting-engine/src/main/cpp/asr/AsrEngine.h`) defines a unified interface for ASR engines. `WhisperAsrEngine` implements this interface using RKNN-optimized Whisper models.

**ASR Lifecycle**:
```
init(modelsPath, language) → Load RKNN models (.rknn files)
    ↓
start() → Reset internal state, prepare for new audio stream
    ↓
pushAudio(pcm, samples) → Accumulate audio, detect silence, trigger inference
    ↓
stop() → Flush remaining audio, emit final transcript
    ↓
release() → Free RKNN context and model memory
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
- Required files: `whisper_encoder_base_20s.rknn`, `whisper_decoder_base_20s.rknn`, `vocab_en.txt`, `vocab_zh.txt`, `mel_80_filters.txt`

**Language Support**:
- `LanguageSetting.Auto` - Whisper auto-detects language from audio
- `LanguageSetting.Fixed("zh")` - Forces specific language for better accuracy
- Passed through: `AsrConfig.Whisper` → `EngineConfig` → JNI → `WhisperAsrEngine.init()`

**Audio Preprocessing** (`WhisperUtils.cpp`):
- FFT with Hann window (N_FFT=400, HOP_LENGTH=160)
- Mel filterbank (80 bands, 16kHz sample rate)
- Log scale conversion
- Output: Flattened 2D array (time × 80 mels) for RKNN input

**Dual Mode Operation**:
- Pure audio mode: `modelProvider = null` → Audio recording only, simulated transcripts
- ASR mode: `modelProvider = { ModelAssetManager.ensureModels(context) }` → Full Whisper ASR pipeline

### C++ Native Code

Located in `meeting-engine/src/main/cpp/`:
- `native-lib.cpp` - JNI entry points
- `AudioRecorder.cpp/h` - Oboe-based audio capture
- `AudioProcessor.cpp/h` - Audio processing with JNI callbacks
- `RingBuffer.h` - Circular buffer for PCM samples
- `asr/AsrEngine.h` - Abstract ASR engine interface (strategy pattern)
- `asr/WhisperAsrEngine.cpp/h` - Whisper implementation using RKNN Runtime
- `asr/WhisperUtils.cpp/h` - Audio preprocessing (FFT, Mel Spectrogram)
- `3rdparty/fftw/` - FFTW3 (Fast Fourier Transform) static library for audio preprocessing
- `test/` - Google Test unit tests for C++ components

Uses CMake 3.22.1 with C++17, Oboe via Prefab, RKNN Runtime (`librknnrt.so` in `jniLibs/arm64-v8a/`), and FFTW3 static library (`libfftw3f.a`).

### Key Interfaces

- `MeetingSession` (`meeting-core`) - Main SDK interface with `prepare()`, `start()`, `stop()`, `release()` lifecycle
- `EngineBridge` (`meeting-engine`) - JNI abstraction layer
- `EngineCallback` (`meeting-engine`) - Callback interface for audio data and transcripts from C++
- `AsrEngine` (`meeting-engine/cpp`) - Abstract C++ interface for ASR engines (strategy pattern)
- `AsrConfig` (`meeting-core`) - Sealed class for ASR configuration (Whisper, future Zipformer)
- `LanguageSetting` (`meeting-core`) - Sealed class for language modes (Auto/Fixed)

### Testing

- Unit tests use `FakeMeetingSession` and fake bridge implementations
- Coroutine testing uses `kotlinx-coroutines-test` with `runTest` and `TestScope`

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
