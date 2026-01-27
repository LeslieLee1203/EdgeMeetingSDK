# Gemini Project Context

## Project Overview
**EdgeMeetingSDK** is an Android software development kit designed for real-time meeting communication. It features high-performance on-device audio processing, specifically **Speech-to-Text (ASR)** using the **OpenAI Whisper (Base model)**, accelerated by the **RK3588 NPU** (Rockchip Neural Processing Unit).

The project is structured as a multi-module Android Gradle project:
*   **`meeting-core`**: Pure Kotlin interfaces and data models (no Android dependencies).
*   **`meeting-engine`**: The core logic, containing the JNI bridge and C++ implementation for audio capture (Oboe) and ASR (RKNN Runtime).
*   **`app`**: A demo Android application (Jetpack Compose) showcasing the SDK usage.

## Architecture & patterns
*   **Bridge Pattern**: `EngineBridge` serves as the abstraction layer between Kotlin and C++ JNI code (`JniEngineBridge`).
*   **State Machine**: Meeting sessions follow a strict state flow: `Idle` -> `Preparing` -> `Ready` -> `Listening` -> `Error`.
*   **Data Flow**:
    1.  **Audio Capture**: C++ `AudioRecorder` (Oboe) captures PCM data.
    2.  **Processing**: `AudioProcessor` handles buffering and passes data to `AsrEngine`.
    3.  **Inference**: `WhisperAsrEngine` (C++) pre-processes audio (FFT/Mel Spectrogram) and runs inference on the NPU via RKNN.
    4.  **Events**: Transcripts and audio levels are sent back to Kotlin via JNI callbacks (`EngineCallback`) and exposed as Kotlin `Flow`s.
*   **Hardware Dependency**: The SDK strictly targets **arm64-v8a** architecture and requires an RK3588-based device for NPU acceleration.

## Development Conventions

### Language & Style
*   **Primary Language**: Kotlin (Android), C++17 (Native).
*   **Documentation/Comments**: **Traditional Chinese (Taiwan, zh_TW)** is preferred for all descriptions and commit messages.
*   **Concurrency**: Kotlin Coroutines and `Flow` are used for all asynchronous operations.
*   **UI**: Jetpack Compose with Material 3 principles.

### Build & Run
*   **Build Project**: `./gradlew build`
*   **Clean**: `./gradlew clean`
*   **Build Debug APK**: `./gradlew :app:assembleDebug`
*   **Native Build**: C++ code is built via CMake, triggered automatically by the Gradle build.

### Testing
*   **Unit Tests (Kotlin)**: `./gradlew test` (Runs tests for all modules).
*   **Module Specific**:
    *   `./gradlew :meeting-core:test`
    *   `./gradlew :meeting-engine:test`
*   **Native Tests (C++)**:
    *   C++ tests are compiled into a binary `asr_tests` located in `meeting-engine/.cxx/Debug/.../arm64-v8a/`.
    *   **Execution**: Must be pushed to an Android device to run:
        ```bash
        adb push meeting-engine/.cxx/Debug/*/arm64-v8a/asr_tests /data/local/tmp/
        adb shell "cd /data/local/tmp && chmod +x asr_tests && ./asr_tests"
        ```

## Key Files & Directories
*   `meeting-engine/src/main/cpp/`: Contains all native C++ source code.
    *   `AudioRecorder.cpp`: Oboe implementation.
    *   `asr/WhisperAsrEngine.cpp`: Whisper model integration via RKNN.
*   `meeting-engine/src/main/java/com/edgemeeting/engine/`: Kotlin implementation of the SDK.
*   `specs/`: Contains detailed functional specifications and research notes (e.g., `002-whisper-asr`).
*   `CLAUDE.md`: Contains detailed project guidelines and technical notes (reference this for deep dives).
