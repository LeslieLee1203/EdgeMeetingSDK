# EdgeMeetingSDK

**EdgeMeetingSDK** is an Android software development kit designed for real-time meeting communication, featuring high-performance on-device audio processing powered by RK3588 NPU.

## Features

- **Real-time ASR**: On-device speech-to-text using OpenAI Whisper (Base model) accelerated by RKNN.
- **Privacy First**: All processing happens locally on the RK3588 device; no audio is sent to the cloud.
- **Language Support**: Supports English (`en`) and Chinese (`zh`) with auto-detection or manual selection.
- **High Performance**: Optimized using C++ JNI, Oboe for low-latency audio, and Rockchip NPU for inference.
- **Easy Integration**: Simple Kotlin API with Coroutines/Flow support.

## Getting Started

### Prerequisites

- **Hardware**: Rockchip RK3588 based Android device (e.g., Orange Pi 5, Rock 5B, RK3588 EVB).
- **OS**: Android 12/13 (API Level 31+ recommended).
- **Dependency**: `librknnrt.so` (included in `meeting-engine`).

### Installation

Add the `meeting-engine` and `meeting-core` modules to your Android project.

### Usage

```kotlin
// 1. Initialize Bridge & Session
val session = RkMeetingSession(
    bridge = JniEngineBridge(),
    // Auto-copy models from assets to app-private storage
    modelProvider = { ModelAssetManager.ensureModels(context) },
    // Optional: Set specific language (default is Auto)
    languageSetting = LanguageSetting.Fixed("zh")
)

// 2. Prepare Engine (Load Models)
session.prepare()

// 3. Observe State (Error Handling)
lifecycleScope.launch {
    session.state.collect { state ->
        if (state is MeetingState.Error) {
            println("ASR error: ${state.code} ${state.message}")
        }
    }
}

// 4. Observe Transcripts
lifecycleScope.launch {
    session.transcriptFlow.collect { segments ->
        segments.forEach { segment ->
            println("[${segment.startTimeMs}ms] ${segment.text}")
        }
    }
}

// 5. Start Recording
session.start()

// 6. Stop Recording
session.stop()

// 7. Release Resources
session.release()
```

### Unified Engine Interface (EngineBridge + EngineConfig)

```kotlin
val bridge = JniEngineBridge()
bridge.setCallback(object : EngineCallback {
    override fun onAudioData(data: FloatArray) = Unit
    override fun onTranscript(segment: TranscriptSegment) = Unit
    override fun onError(code: Int, message: String) {
        println("Engine error: $code $message")
    }
})

val config = EngineConfig(
    asrConfig = AsrConfig.Whisper(
        modelsPath = "/data/data/com.edgemeeting.sdk/files/models",
        language = LanguageSetting.Auto
    )
)

when (val result = bridge.init(config)) {
    BridgeResult.Success -> bridge.startRecording()
    is BridgeResult.Failure -> println("Init failed: ${result.code} ${result.message}")
}
```

## Architecture

- **`meeting-core`**: Pure Kotlin API definitions and data models.
- **`meeting-engine`**: Android Library containing the JNI bridge and C++ implementation.
  - **JNI**: Bridges Kotlin and C++.
  - **Native**: Uses `Oboe` for audio capture and `RKNN Runtime` for Whisper inference.
- **`app`**: Demo application demonstrating SDK usage.

## Performance

- **Real Time Factor (RTF)**: < 0.3 (Processing 10s audio takes < 3s on RK3588 NPU).
- **Latency**: First token emitted within 2-3 seconds (Whisper segment buffer).

## Build

```bash
./gradlew build
```
