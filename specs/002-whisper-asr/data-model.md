# 資料模型

## AsrConfig (sealed class)

ASR 引擎配置，採用 sealed class 確保型別安全與可擴充性。

### AsrConfig.Whisper

- **modelsPath**: 模型檔資料夾絕對路徑（必填）
- **language**: LanguageSetting（必填）

### AsrConfig.Zipformer (未來)

- **modelsPath**: 模型檔資料夾絕對路徑（必填）
- **beamSize**: 搜尋寬度（預設 4）

## EngineConfig

引擎統一配置。

- **asrConfig**: AsrConfig?（可選，null = 純錄音模式）

> **Note**: 音訊錄製（Oboe）不需要模型檔案，ASR 模型路徑已在 `AsrConfig` 中定義。

## TranscriptSegment

擴充現有欄位，新增 `languageCode`：

- **id**: 唯一識別碼，用於 DiffUtil（必填）
- **text**: 逐段文字內容（必填）
- **speakerId**: 說話者識別（必填）
- **isFinal**: 是否為確認結果（必填）
- **startTimeMs**: 段落開始時間（毫秒，必填）
- **endTimeMs**: 段落結束時間（毫秒，必填）
- **languageCode**: 偵測到的語言代碼（可選，如 "zh", "en"）

### 驗證規則

- `startTimeMs >= 0`
- `endTimeMs >= startTimeMs`
- `text` 不得為空白

## LanguageSetting (sealed class)

語言設定，採用 sealed class 確保型別安全。

```kotlin
sealed class LanguageSetting {
    data object Auto : LanguageSetting()
    data class Fixed(val languageCode: String) : LanguageSetting()
}
```

### LanguageSetting.Auto

自動偵測語言，無額外參數。

### LanguageSetting.Fixed

指定語言以提升準確度：

- **languageCode**: 語言代碼（必填，如 `"zh"`, `"en"`, `"ja"`）

## TranscriptionSession

> **實作澄清**：此資料模型由既有 `MeetingState` sealed interface 涵蓋，
> 不另建新類別。`lastErrorCode`/`lastErrorMessage` 對應 `MeetingState.Error` 的屬性。

- **state**: `idle` | `preparing` | `ready` | `listening` | `error`
- **startTimeMs**: 會議開始時間（毫秒）
- **endTimeMs**: 會議結束時間（毫秒）
- **lastErrorCode**: 錯誤碼（可選）
- **lastErrorMessage**: 可讀錯誤訊息（可選）

## ModelConstants

模型相關常數，統一定義於 `meeting-engine`。

```kotlin
object ModelConstants {
    const val ASSETS_MODEL_DIR = "models"
    const val RUNTIME_MODEL_DIR = "models"
    val EXPECTED_MODEL_FILES = listOf(
        "whisper_encoder_base_20s.rknn",
        "whisper_decoder_base_20s.rknn"
    )
}
```

> **設計原則**：避免在多處重複定義路徑常數，確保單一真相來源（Single Source of Truth）。

## ModelsReady

`ModelAssetManager.ensureModels()` 成功時的回傳值。

```kotlin
data class ModelsReady(
    val modelsDir: File  // runtime 模型目錄絕對路徑
)
```

- **modelsDir**: File（runtime 模型目錄，如 `context.filesDir.absolutePath + "/models/"`）

> **設計考量**：
> - 路徑由 `ModelAssetManager` 統一管理，使用 `ModelConstants` 常數
> - Caller 只需使用回傳的 `modelsDir.absolutePath` 建立 `AsrConfig.Whisper`
> - `modelsDir.absolutePath` 將傳遞給 C++ `rknn_init()` 作為模型載入路徑

## BridgeResult (sealed interface)

JNI 操作結果，沿用現有設計。

### BridgeResult.Success

`data object`，操作成功，無額外資料。

### BridgeResult.Failure

`data class`，操作失敗：

- **code**: Int（錯誤碼）
- **message**: String（可讀錯誤訊息）

### 錯誤碼定義

| Code | 常數名 | 意義 |
|------|--------|------|
| 1001 | ERR_MODEL_NOT_FOUND | 模型檔案不存在 |
| 1002 | ERR_MODEL_LOAD_FAILED | 模型載入失敗 |
| 1003 | ERR_ASR_TYPE_UNSUPPORTED | 不支援的 ASR 類型 |
| 2001 | ERR_AUDIO_FORMAT | 音訊格式錯誤 |
| 9999 | ERR_UNKNOWN | 未知錯誤 |
