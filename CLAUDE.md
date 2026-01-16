# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

# Run instrumented tests (requires connected device/emulator)
./gradlew :app:connectedAndroidTest
```

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

### C++ Native Code

Located in `meeting-engine/src/main/cpp/`:
- `native-lib.cpp` - JNI entry points
- `AudioRecorder.cpp/h` - Oboe-based audio capture
- `AudioProcessor.cpp/h` - Audio processing with JNI callbacks
- `RingBuffer.h` - Circular buffer for PCM samples

Uses CMake 3.22.1 with C++17 and Oboe via Prefab.

### Key Interfaces

- `MeetingSession` (`meeting-core`) - Main SDK interface with `prepare()`, `start()`, `stop()`, `release()` lifecycle
- `EngineBridge` (`meeting-engine`) - JNI abstraction layer
- `AudioCallback` (`meeting-engine`) - Callback interface for audio data from C++

### Testing

- Unit tests use `FakeMeetingSession` and fake bridge implementations
- Coroutine testing uses `kotlinx-coroutines-test` with `runTest` and `TestScope`

## Tech Stack

- **Kotlin**: 2.3.0
- **Android**: API 33-36 (Android 13-15)
- **UI**: Jetpack Compose with Material 3
- **Async**: Kotlin Coroutines with StateFlow/Flow
- **Native Audio**: Google Oboe 1.10.0
- **Build**: Gradle with Kotlin DSL, CMake for C++
