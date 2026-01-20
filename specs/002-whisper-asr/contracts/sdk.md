# SDK Contract

## 設計原則

採用**策略模式**統一 ASR 介面，避免為每種 ASR 引擎（Whisper、Zipformer 等）新增獨立 API。

## 統一配置

### EngineConfig

```kotlin
data class EngineConfig(
    val asrConfig: AsrConfig? = null  // null = 純錄音模式，無 ASR
)
```

> **Note**: 音訊錄製（Oboe）不需要模型檔案，ASR 模型路徑已在 `AsrConfig` 中定義。

### AsrConfig (sealed class)

```kotlin
sealed class AsrConfig {
    data class Whisper(
        val modelsPath: String,  // 如 /data/data/com.edgemeeting.engine/files/models
        val language: LanguageSetting
    ) : AsrConfig()
    
    // 未來擴充
    // data class Zipformer(val modelsPath: String, val beamSize: Int = 4) : AsrConfig()
}
```

## 統一回調

### EngineCallback

```kotlin
interface EngineCallback {
    fun onAudioData(data: FloatArray)
    fun onTranscript(segment: TranscriptSegment)
    fun onError(code: Int, message: String)
}
```

## EngineBridge 介面

```kotlin
interface EngineBridge {
    fun init(config: EngineConfig): BridgeResult
    fun setCallback(callback: EngineCallback)
    fun startRecording()
    fun stopRecording()
    fun release()
}
```

## Native 端策略介面

### AsrEngine (C++ 抽象類)

```cpp
class AsrEngine {
public:
    virtual ~AsrEngine() = default;
    virtual bool init(const std::string& modelsPath, const std::string& language) = 0;
    virtual void start() = 0;    // 開始 ASR 處理（重置內部狀態）
    virtual void pushAudio(const int16_t* pcm, size_t samples) = 0;
    virtual void stop() = 0;     // 停止 ASR（flush 剩餘音訊）
    virtual void release() = 0;  // 釋放模型資源
};
```

**生命週期**：`init()` → `start()` → `pushAudio()` ... → `stop()` → `release()`

### WhisperAsrEngine

實作 `AsrEngine`，內部使用 RKNN Whisper 模型。轉錄結果透過 JNI callback 回傳 `onTranscript`。

## 回傳型別

### BridgeResult

沿用現有 `sealed interface` 設計：

```kotlin
sealed interface BridgeResult {
    data object Success : BridgeResult
    data class Failure(val code: Int, val message: String) : BridgeResult
}
```

### TranscriptSegment

擴充現有 `TranscriptSegment`，新增 `languageCode` 欄位：

```kotlin
data class TranscriptSegment(
    val id: String,
    val text: String,
    val speakerId: String,
    val isFinal: Boolean,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val languageCode: String? = null  // 新增：偵測到的語言代碼（如 "zh", "en"）
)
```

## 錯誤碼

| Code | 意義 |
|------|------|
| 0 | 成功 |
| 1001 | 模型檔案不存在 |
| 1002 | 模型載入失敗 |
| 1003 | 不支援的 ASR 類型 |
| 2001 | 音訊格式錯誤 |
| 9999 | 未知錯誤 |
