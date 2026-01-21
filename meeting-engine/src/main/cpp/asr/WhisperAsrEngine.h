// meeting-engine/src/main/cpp/asr/WhisperAsrEngine.h
// Whisper ASR 引擎實作（使用 RKNN Runtime on RK3588）

#ifndef MEETING_ENGINE_WHISPER_ASR_ENGINE_H
#define MEETING_ENGINE_WHISPER_ASR_ENGINE_H

#include "AsrEngine.h"
#include <string>
#include <vector>
#include <cstdint>

// RKNN 前向宣告（避免在 header 中 include RKNN headers）
// 為什麼使用前向宣告：減少編譯依賴，加快編譯速度
typedef void* rknn_context;

/**
 * WhisperAsrEngine - 使用 RKNN 執行 Whisper 模型的 ASR 引擎
 *
 * 為什麼使用 RKNN：
 * 1. RK3588 NPU 硬體加速 Whisper 模型推論
 * 2. 降低 CPU 負載，提升即時性
 * 3. 降低功耗（NPU 比 CPU 省電）
 *
 * 架構設計：
 * - 載入兩個 RKNN 模型：encoder (mel-spectrogram → features) 與 decoder (features → text)
 * - 內部累積音訊樣本，達到推論門檻時觸發 ASR
 * - 靜音偵測：超過 700ms 靜音時切段輸出
 * - 執行緒安全：pushAudio 在音訊執行緒呼叫，需同步保護
 *
 * 生命週期：
 *   init(modelsPath, language)  ← 載入 RKNN 模型
 *      ↓
 *   start()                     ← 重置內部狀態（清空 buffer）
 *      ↓
 *   pushAudio(pcm, samples) ... ← 累積音訊，觸發推論與回調
 *      ↓
 *   stop()                      ← flush 剩餘音訊
 *      ↓
 *   release()                   ← 釋放 RKNN context
 */
class WhisperAsrEngine : public AsrEngine {
public:
    WhisperAsrEngine();
    ~WhisperAsrEngine() override;

    // 禁止拷貝與賦值（RKNN context 不可複製）
    WhisperAsrEngine(const WhisperAsrEngine&) = delete;
    WhisperAsrEngine& operator=(const WhisperAsrEngine&) = delete;

    /**
     * 初始化 Whisper ASR 引擎
     *
     * @param modelsPath 模型檔資料夾絕對路徑（包含 encoder 與 decoder .rknn 檔）
     * @param language 語言代碼（"auto", "zh", "en" 等）
     * @return true 成功載入模型，false 失敗（檔案不存在、RKNN 初始化失敗等）
     *
     * 為什麼需要兩個模型：
     * - whisper_encoder_base_20s.rknn: 音訊 mel-spectrogram → features
     * - whisper_decoder_base_20s.rknn: features → text tokens → text
     *
     * 檔案路徑範例：
     * - modelsPath = "/data/data/com.edgemeeting/files/models"
     * - encoder: "/data/data/com.edgemeeting/files/models/whisper_encoder_base_20s.rknn"
     * - decoder: "/data/data/com.edgemeeting/files/models/whisper_decoder_base_20s.rknn"
     */
    bool init(const std::string& modelsPath, const std::string& language) override;

    /**
     * 開始 ASR 處理
     *
     * 重置內部狀態：
     * - 清空音訊 buffer
     * - 重置靜音偵測計時器
     * - 重置段落計數器
     */
    void start() override;

    /**
     * 推送音訊資料至 ASR 引擎
     *
     * @param pcm PCM 音訊資料（int16_t 格式，16kHz 單聲道）
     * @param samples 樣本數（如 160 = 10ms @ 16kHz）
     *
     * 內部流程：
     * 1. 累積音訊至內部 buffer
     * 2. 達到推論門檻（如 3 秒）時，觸發 RKNN 推論
     * 3. 靜音偵測：超過 700ms 靜音時切段輸出
     * 4. 透過 JNI callback 回傳 TranscriptSegment
     *
     * 執行緒考量：
     * - 此方法在音訊執行緒呼叫（Oboe callback）
     * - 推論應在背景執行緒執行，避免阻塞音訊執行緒
     * - 內部使用 mutex 保護共享狀態
     */
    void pushAudio(const int16_t* pcm, size_t samples) override;

    /**
     * 停止 ASR 處理
     *
     * Flush 剩餘音訊：
     * - 將 buffer 中剩餘的音訊進行最後一次推論
     * - 輸出最後的 TranscriptSegment（如果有）
     */
    void stop() override;

    /**
     * 釋放 ASR 引擎資源
     *
     * 清理工作：
     * - rknn_destroy() 釋放 encoder context
     * - rknn_destroy() 釋放 decoder context
     * - 清空內部 buffer
     * - 重置所有狀態
     *
     * 為什麼需要明確 release：
     * - RKNN context 佔用大量記憶體（模型 + NPU 資源）
     * - 會議結束後應立即釋放，避免記憶體不足
     */
    void release() override;

private:
    // RKNN contexts（encoder + decoder）
    rknn_context encoderContext_;
    rknn_context decoderContext_;

    // 模型路徑
    std::string modelsPath_;
    std::string language_;

    // 初始化狀態
    bool initialized_;

    // 音訊 buffer（累積 PCM 樣本）
    std::vector<int16_t> audioBuffer_;

    // 靜音偵測狀態
    int silenceDurationMs_;
    const int SILENCE_THRESHOLD_MS = 700;  // 700ms 靜音切段

    // 段落計數（用於生成 TranscriptSegment.id）
    int segmentCounter_;

    // 輔助方法

    /**
     * 載入單個 RKNN 模型
     *
     * @param modelPath 模型檔絕對路徑
     * @param context 輸出參數：RKNN context
     * @return true 成功，false 失敗
     */
    bool loadRknnModel(const std::string& modelPath, rknn_context& context);

    /**
     * 檢查模型檔案是否存在
     *
     * @param modelsPath 模型資料夾路徑
     * @return true 兩個模型檔都存在，false 缺少模型檔
     */
    bool validateModelFiles(const std::string& modelsPath);

    /**
     * 執行 Whisper 推論
     *
     * @param pcmData PCM 音訊資料
     * @param samples 樣本數
     * @return 推論結果文字（UTF-8）
     *
     * 內部流程：
     * 1. PCM → mel-spectrogram
     * 2. encoder: mel-spectrogram → features
     * 3. decoder: features → text tokens
     * 4. tokens → text string
     */
    std::string runInference(const int16_t* pcmData, size_t samples);

    /**
     * 偵測靜音
     *
     * @param pcm PCM 音訊資料
     * @param samples 樣本數
     * @return true 為靜音，false 為有聲音
     *
     * 簡單實作：計算 RMS (Root Mean Square)，低於門檻視為靜音
     */
    bool detectSilence(const int16_t* pcm, size_t samples);

    /**
     * 輸出 TranscriptSegment
     *
     * 透過 JNI callback 回傳轉錄結果
     * 注意：此方法需要與 JNI 層整合（Phase 3.4）
     */
    void emitTranscript(const std::string& text, long startMs, long endMs);
};

#endif // MEETING_ENGINE_WHISPER_ASR_ENGINE_H
