# EdgeMeetingSDK

**EdgeMeetingSDK** is an Android software development kit designed for real-time meeting communication, featuring high-performance on-device audio processing.

## Project Overview

This project implements a hybrid architecture combining high-level Kotlin APIs with low-level C++ audio processing.

*   **Core Functionality**: Real-time audio capture and processing for meetings.
*   **Current Focus**: Phase 0 MVP Transcript Flow (providing a stable demo of transcript stream).
*   **Key Technologies**:
    *   **Language**: Kotlin (Android), C++17 (Native).
    *   **Audio**: Google Oboe (High-performance audio).
    *   **UI**: Jetpack Compose (Demo App).
    *   **Architecture**: Multi-module Gradle project with JNI bridging.

## Architecture

The project follows a clean separation of concerns across three main modules:

1.  **`meeting-core`**: Pure Kotlin module defining the public API, interfaces (e.g., `MeetingSession`), and domain models. No Android dependencies.
2.  **`meeting-engine`**: The implementation module containing:
    *   **JNI Bridge**: `EngineBridge` connecting Kotlin to C++.
    *   **Native Code** (`src/main/cpp`): C++ audio processing using Oboe and a Ring Buffer.
    *   **Android Dependencies**: Required for JNI and Android audio APIs.
3.  **`app`**: A demonstration Android application using Jetpack Compose to showcase the SDK's capabilities.

### Key Patterns

*   **Bridge Pattern**: `EngineBridge` isolates the complex JNI implementation, allowing for easier testing via `FakeEngineBridge`.
*   **State Machine**: The `MeetingSession` operates on a strict state machine (`Idle` → `Preparing` → `Ready` → `Listening` → `Error`), exposed via Kotlin `StateFlow`.
*   **Unidirectional Data Flow**: Audio data flows from C++ -> JNI -> Kotlin Callback.

## Building and Running

The project uses the standard Gradle wrapper.

### Build

```bash
# Build all modules (including C++ native code)
./gradlew build

# Build the Demo App APK (Debug)
./gradlew :app:assembleDebug

# Clean build artifacts
./gradlew clean
```

### Test

```bash
# Run all unit tests
./gradlew test

# Run tests for specific modules
./gradlew :meeting-core:test
./gradlew :meeting-engine:test

# Run instrumented tests (on connected device)
./gradlew :app:connectedAndroidTest
```

## Development Conventions

*   **Code Style**: Kotlin official guidelines.
*   **Testing**:
    *   Prefer unit tests for logic, using Fakes for native components.
    *   Use `kotlinx-coroutines-test` for async flows.
*   **Native Code**:
    *   Located in `meeting-engine/src/main/cpp`.
    *   Uses CMake 3.22.1+.
    *   Adhere to JNI best practices (minimize JNI boundary crossings).
*   **Documentation**:
    *   Feature specifications are stored in `specs/`.
    *   Agent/AI context is stored in `.specify/` and `CLAUDE.md`.

## Directory Structure

*   `app/`: Android demo application.
*   `meeting-core/`: Public API and domain models.
*   `meeting-engine/`: SDK implementation and native C++ code.
*   `specs/`: Detailed feature specifications and planning documents.
*   `gradle/`: Gradle wrapper and version catalog (`libs.versions.toml`).
