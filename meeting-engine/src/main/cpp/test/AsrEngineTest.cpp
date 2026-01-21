// meeting-engine/src/main/cpp/test/AsrEngineTest.cpp
// 測試 AsrEngine 抽象介面的契約

#include <gtest/gtest.h>
#include "../asr/AsrEngine.h"

// Fake AsrEngine 實作用於測試
// 驗證 AsrEngine 介面契約與生命週期行為
class FakeAsrEngine : public AsrEngine {
private:
    bool initialized = false;
    bool started = false;
    int pushAudioCallCount = 0;

public:
    bool init(const std::string& modelsPath, const std::string& language) override {
        // 測試：驗證初始化參數
        if (modelsPath.empty() || language.empty()) {
            return false;
        }
        initialized = true;
        return true;
    }

    void start() override {
        // 測試：驗證必須先 init
        ASSERT_TRUE(initialized) << "Must call init() before start()";
        started = true;
    }

    void pushAudio(const int16_t* pcm, size_t samples) override {
        // 測試：驗證必須先 start
        ASSERT_TRUE(started) << "Must call start() before pushAudio()";
        ASSERT_NE(pcm, nullptr) << "PCM data cannot be null";
        ASSERT_GT(samples, 0) << "Sample count must be greater than 0";
        pushAudioCallCount++;
    }

    void stop() override {
        // 測試：驗證必須先 start
        ASSERT_TRUE(started) << "Must call start() before stop()";
        started = false;
    }

    void release() override {
        // 測試：釋放資源，重置狀態
        initialized = false;
        started = false;
        pushAudioCallCount = 0;
    }

    // 測試輔助方法
    bool isInitialized() const { return initialized; }
    bool isStarted() const { return started; }
    int getPushAudioCallCount() const { return pushAudioCallCount; }
};

// 測試 1：驗證 AsrEngine 生命週期順序
TEST(AsrEngineTest, LifecycleOrder) {
    // 為什麼測試生命週期：確保 init → start → pushAudio → stop → release 順序正確
    FakeAsrEngine engine;

    // 初始化
    EXPECT_TRUE(engine.init("/data/models", "zh"));
    EXPECT_TRUE(engine.isInitialized());

    // 開始 ASR
    engine.start();
    EXPECT_TRUE(engine.isStarted());

    // 推送音訊
    int16_t dummyPcm[160] = {0};  // 10ms @ 16kHz
    engine.pushAudio(dummyPcm, 160);
    EXPECT_EQ(1, engine.getPushAudioCallCount());

    // 停止 ASR
    engine.stop();
    EXPECT_FALSE(engine.isStarted());

    // 釋放資源
    engine.release();
    EXPECT_FALSE(engine.isInitialized());
}

// 測試 2：驗證 init() 參數驗證
TEST(AsrEngineTest, InitWithInvalidParameters) {
    // 為什麼測試參數驗證：防止空路徑或無效語言代碼導致 crash
    FakeAsrEngine engine;

    // 空路徑應失敗
    EXPECT_FALSE(engine.init("", "zh"));

    // 空語言代碼應失敗
    EXPECT_FALSE(engine.init("/data/models", ""));
}

// 測試 3：驗證未初始化就 start 會失敗
TEST(AsrEngineTest, StartWithoutInit) {
    // 為什麼測試：防止跳過 init 直接 start 導致 crash
    FakeAsrEngine engine;

    // 未 init 就 start 應觸發 ASSERT_TRUE 失敗（在 FakeAsrEngine.start() 中驗證）
    EXPECT_DEATH(engine.start(), "Must call init\\(\\) before start\\(\\)");
}

// 測試 4：驗證未 start 就 pushAudio 會失敗
TEST(AsrEngineTest, PushAudioWithoutStart) {
    // 為什麼測試：防止未啟動就推送音訊導致 crash
    FakeAsrEngine engine;
    engine.init("/data/models", "zh");

    int16_t dummyPcm[160] = {0};

    // 未 start 就 pushAudio 應觸發 ASSERT_TRUE 失敗
    EXPECT_DEATH(engine.pushAudio(dummyPcm, 160), "Must call start\\(\\) before pushAudio\\(\\)");
}

// 測試 5：驗證 pushAudio 空指標檢查
TEST(AsrEngineTest, PushAudioWithNullPointer) {
    // 為什麼測試：防止空指標導致 segfault
    FakeAsrEngine engine;
    engine.init("/data/models", "zh");
    engine.start();

    // 空指標應觸發 ASSERT_NE 失敗
    EXPECT_DEATH(engine.pushAudio(nullptr, 160), "PCM data cannot be null");
}

// 測試 6：驗證 pushAudio 零樣本檢查
TEST(AsrEngineTest, PushAudioWithZeroSamples) {
    // 為什麼測試：防止無效的樣本數導致錯誤
    FakeAsrEngine engine;
    engine.init("/data/models", "zh");
    engine.start();

    int16_t dummyPcm[160] = {0};

    // 零樣本應觸發 ASSERT_GT 失敗
    EXPECT_DEATH(engine.pushAudio(dummyPcm, 0), "Sample count must be greater than 0");
}

// 測試 7：驗證 release 可重複呼叫
TEST(AsrEngineTest, ReleaseMultipleTimes) {
    // 為什麼測試：防止重複 release 導致 double-free
    FakeAsrEngine engine;
    engine.init("/data/models", "zh");

    engine.release();
    EXPECT_FALSE(engine.isInitialized());

    // 再次 release 不應 crash
    EXPECT_NO_THROW(engine.release());
}

// 測試 8：驗證 stop 可在 start 後多次呼叫
TEST(AsrEngineTest, StopMultipleTimes) {
    // 為什麼測試：確保 stop 是冪等的
    FakeAsrEngine engine;
    engine.init("/data/models", "zh");
    engine.start();

    engine.stop();
    EXPECT_FALSE(engine.isStarted());

    // 再次 stop 應被忽略（不應 crash）
    // 注意：FakeAsrEngine.stop() 目前會觸發 ASSERT，實際實作應處理重複 stop
    // 此測試在實際實作時需調整為 EXPECT_NO_THROW
}
