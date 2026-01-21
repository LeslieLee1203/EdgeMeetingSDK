// meeting-engine/src/main/cpp/asr/AsrEngine.h
// ASR 引擎抽象介面，策略模式基底類別

#ifndef MEETING_ENGINE_ASR_ENGINE_H
#define MEETING_ENGINE_ASR_ENGINE_H

#include <string>
#include <cstdint>
#include <cstddef>

/**
 * AsrEngine 抽象類別
 *
 * 為什麼使用抽象類別：
 * 1. 策略模式設計：支援 Whisper、Zipformer 等多種 ASR 引擎
 * 2. 統一生命週期管理：init → start → pushAudio → stop → release
 * 3. 便於測試：可使用 Mock/Fake 實作進行單元測試
 * 4. 未來擴充：新增 ASR 引擎只需繼承此介面，無需修改上層邏輯
 *
 * 生命週期：
 *   init(modelsPath, language)  ← 載入模型、初始化 ASR 引擎
 *      ↓
 *   start()                     ← 開始 ASR 處理（重置內部狀態）
 *      ↓
 *   pushAudio(pcm, samples) ... ← 持續推送音訊樣本
 *      ↓
 *   stop()                      ← 停止 ASR（flush 剩餘音訊）
 *      ↓
 *   release()                   ← 釋放模型資源
 *
 * 執行緒安全：
 * - init/release 應在主執行緒呼叫
 * - start/stop/pushAudio 可能在音訊執行緒呼叫
 * - 實作類別需自行處理執行緒同步
 */
class AsrEngine {
public:
    virtual ~AsrEngine() = default;

    /**
     * 初始化 ASR 引擎
     *
     * @param modelsPath 模型檔資料夾絕對路徑（如 /data/data/com.edgemeeting.engine/files/models）
     * @param language 語言代碼（如 "zh", "en", "auto"）
     * @return true 初始化成功，false 初始化失敗（模型載入錯誤、檔案不存在等）
     *
     * 為什麼需要 modelsPath：
     * - Android 應用需從 assets 複製模型至 app-specific storage
     * - RKNN rknn_init 需要檔案絕對路徑
     *
     * 為什麼需要 language：
     * - Whisper 支援 auto（自動偵測）與指定語言（提升準確度）
     * - 未來 Zipformer 可能需要切換不同語言模型
     */
    virtual bool init(const std::string& modelsPath, const std::string& language) = 0;

    /**
     * 開始 ASR 處理
     *
     * 重置內部狀態（清空音訊緩衝、重置分段計時器等）
     * 呼叫時機：MeetingSession.start() 時觸發
     *
     * 為什麼需要 start：
     * - 支援多次 start/stop 循環（如會議中途暫停後恢復）
     * - 重置內部狀態，避免前次殘留影響
     */
    virtual void start() = 0;

    /**
     * 推送音訊資料至 ASR 引擎
     *
     * @param pcm PCM 音訊資料（int16_t 格式，16kHz 單聲道）
     * @param samples 樣本數（如 160 = 10ms @ 16kHz）
     *
     * 呼叫頻率：每 10ms 推送一次（由 AudioRecorder callback 觸發）
     *
     * 為什麼使用 int16_t：
     * - Oboe 音訊格式為 int16_t
     * - Whisper 輸入為 float32，內部需轉換
     *
     * 為什麼需要累積音訊：
     * - Whisper 需要至少 3 秒音訊進行推論
     * - 靜音偵測後切段輸出 TranscriptSegment
     */
    virtual void pushAudio(const int16_t* pcm, size_t samples) = 0;

    /**
     * 停止 ASR 處理
     *
     * Flush 剩餘音訊（輸出最後未完成的段落）
     * 呼叫時機：MeetingSession.stop() 時觸發
     *
     * 為什麼需要 stop：
     * - 確保所有音訊都被處理輸出
     * - 支援會議中途暫停
     *
     * 注意：stop 後可再次 start，無需重新 init
     */
    virtual void stop() = 0;

    /**
     * 釋放 ASR 引擎資源
     *
     * 釋放模型記憶體、清理 RKNN context
     * 呼叫時機：MeetingSession.release() 時觸發
     *
     * 為什麼需要 release：
     * - RKNN context 佔用大量記憶體（模型 + NPU 資源）
     * - 會議結束後應立即釋放
     *
     * 注意：release 後必須重新 init 才能再次使用
     */
    virtual void release() = 0;
};

#endif // MEETING_ENGINE_ASR_ENGINE_H
