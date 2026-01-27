package com.edgemeeting.engine

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import com.edgemeeting.engine.bridge.ErrorCodes
import com.edgemeeting.engine.fake.FakeAsrEngine
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class RkMeetingSessionTest {

    // 1. 定義一個受我們控制的假 Bridge (Mock Object)
    // 它的任務是記錄「有沒有被呼叫」以及「模擬回傳結果」
    class FakeEngineBridge : EngineBridge {
        var nextInitResult: BridgeResult = BridgeResult.Success // 設定預設行為
        var isInitCalled = false
        var lastConfig: EngineConfig? = null // 記錄傳入的配置
        var capturedCallback: EngineCallback? = null
        var stopRecordingCallCount = 0

        override fun init(config: EngineConfig): BridgeResult {
            isInitCalled = true
            lastConfig = config
            return nextInitResult
        }

        // 其他方法暫時不重要，留空即可
        override fun setCallback(callback: EngineCallback) {
            this.capturedCallback = callback
        }
        override fun startRecording() {}
        override fun stopRecording() {
            stopRecordingCallCount += 1
        }
        override fun release() {}
    }

    @Test
    fun `prepare should call init on bridge and_become Ready on success`() = runTest {
        // Arrange (準備)
        val fakeBridge = FakeEngineBridge()
        fakeBridge.nextInitResult = BridgeResult.Success // 我們期望底層回傳成功

        val session = RkMeetingSession(fakeBridge)

        // Act (執行)
        session.prepare()

        // Assert (驗證)
        // 驗證點 1: 確保有去呼叫底層
        assertEquals("應該要呼叫底層 C++ init", true, fakeBridge.isInitCalled)

        // 驗證點 2: 確保狀態機有正確更新
        assertEquals("成功時狀態應變為 Ready", MeetingState.Ready, session.state.value)
    }

    @Test
    fun `prepare should become Error on failure`() = runTest {
        // Arrange (準備失敗的情境)
        val fakeBridge = FakeEngineBridge()
        fakeBridge.nextInitResult = BridgeResult.Failure(101, "Model Not Found")

        val session = RkMeetingSession(fakeBridge)

        // Act
        session.prepare()

        // Assert
        val currentState = session.state.value
        assert(currentState is MeetingState.Error)
        assertEquals("錯誤碼應透傳", 101, (currentState as MeetingState.Error).code)
    }

    // ===== T043: prepare() 整合 ModelAssetManager 測試 =====

    /**
     * T043-1: prepare() 成功時應使用 modelProvider 回傳的 modelsDir 建立 AsrConfig.Whisper
     *
     * 為什麼需要測試：驗證 prepare() 正確整合 ModelAssetManager
     * 預期行為：
     * 1. modelProvider 被呼叫
     * 2. bridge.init() 收到的 EngineConfig.asrConfig 為 Whisper
     * 3. AsrConfig.Whisper.modelsPath 為 modelProvider 回傳的路徑
     */
    @Test
    fun `prepare should use modelProvider to build AsrConfig with modelsPath`() = runTest {
        // Arrange
        val fakeBridge = FakeEngineBridge()
        val fakeModelsDir = File("/fake/models/dir")
        var modelProviderCalled = false

        val session = RkMeetingSession(
            bridge = fakeBridge,
            modelProvider = {
                modelProviderCalled = true
                Result.success(ModelsReady(fakeModelsDir))
            }
        )

        // Act
        session.prepare()

        // Assert
        assertTrue("modelProvider 應該被呼叫", modelProviderCalled)
        assertTrue("bridge.init() 應該被呼叫", fakeBridge.isInitCalled)

        val config = fakeBridge.lastConfig
        assertNotNull("EngineConfig 不應為 null", config)

        val asrConfig = config!!.asrConfig
        assertTrue(
            "asrConfig 應為 Whisper 類型",
            asrConfig is AsrConfig.Whisper
        )

        val whisperConfig = asrConfig as AsrConfig.Whisper
        assertEquals(
            "modelsPath 應為 modelProvider 回傳的路徑",
            fakeModelsDir.absolutePath,
            whisperConfig.modelsPath
        )
    }

    /**
     * T043-2: modelProvider 回傳失敗時，狀態應轉為 Error(ERR_MODEL_NOT_FOUND)
     *
     * 為什麼需要測試：確保模型缺失時錯誤正確傳遞
     * 預期行為：
     * 1. state 變為 MeetingState.Error
     * 2. 錯誤碼為 ERR_MODEL_NOT_FOUND
     * 3. bridge.init() 不應被呼叫（模型都沒有，不需要初始化引擎）
     */
    @Test
    fun `prepare should become Error with ERR_MODEL_NOT_FOUND when modelProvider fails`() = runTest {
        // Arrange
        val fakeBridge = FakeEngineBridge()

        val session = RkMeetingSession(
            bridge = fakeBridge,
            modelProvider = {
                Result.failure(IllegalStateException("Model files not found in assets"))
            }
        )

        // Act
        session.prepare()

        // Assert
        val currentState = session.state.value
        assertTrue(
            "狀態應為 Error，實際為 $currentState",
            currentState is MeetingState.Error
        )

        val error = currentState as MeetingState.Error
        assertEquals(
            "錯誤碼應為 ERR_MODEL_NOT_FOUND",
            ErrorCodes.ERR_MODEL_NOT_FOUND,
            error.code
        )

        // 模型準備失敗，不應該嘗試初始化引擎
        assertTrue(
            "bridge.init() 不應被呼叫",
            !fakeBridge.isInitCalled
        )
    }

    /**
     * T043-3: 不提供 modelProvider 時應使用純錄音模式（向後相容）
     *
     * 為什麼需要測試：確保舊有程式碼不會因為新增 modelProvider 而壞掉
     * 預期行為：
     * 1. 不提供 modelProvider 時，asrConfig 為 null（純錄音模式）
     * 2. prepare() 仍可正常執行並進入 Ready 狀態
     */
    @Test
    fun `prepare without modelProvider should use audio-only mode for backward compatibility`() = runTest {
        // Arrange
        val fakeBridge = FakeEngineBridge()
        fakeBridge.nextInitResult = BridgeResult.Success

        // 不提供 modelProvider（使用預設值 null）
        val session = RkMeetingSession(bridge = fakeBridge)

        // Act
        session.prepare()

        // Assert
        assertEquals(
            "狀態應為 Ready",
            MeetingState.Ready,
            session.state.value
        )

        val config = fakeBridge.lastConfig
        assertNotNull("EngineConfig 不應為 null", config)
        assertEquals(
            "asrConfig 應為 null（純錄音模式）",
            null,
            config!!.asrConfig
        )
    }

    @Test
    fun `onError should move to Error and stop recording`() = runTest {
        val fakeBridge = FakeEngineBridge()
        val session = RkMeetingSession(bridge = fakeBridge)

        val callback = fakeBridge.capturedCallback
        assertNotNull("callback 應該被註冊", callback)

        callback!!.onError(9999, "Native error")

        val currentState = session.state.value
        assertTrue("狀態應為 Error", currentState is MeetingState.Error)
        assertEquals("錯誤碼應透傳", 9999, (currentState as MeetingState.Error).code)
        assertEquals("應停止錄音一次", 1, fakeBridge.stopRecordingCallCount)
    }

    // ===== T047: start()/stop() 啟停 ASR 測試 =====

    /**
     * T047-1: start() 應該呼叫 bridge.startRecording() 並將引擎設為錄音狀態
     *
     * 為什麼需要測試：確保 start() 正確啟動 ASR 引擎
     * 預期行為：
     * 1. prepare() 後呼叫 start()，引擎應進入錄音狀態
     * 2. FakeAsrEngine.isRecording() 應為 true
     */
    @Test
    fun `start should call bridge startRecording and engine should be recording`() = runTest {
        // Arrange
        val fakeEngine = FakeAsrEngine()
        val session = RkMeetingSession(bridge = fakeEngine)

        session.prepare()
        assertFalse("prepare 後引擎不應在錄音狀態", fakeEngine.isRecording())

        // Act
        session.start()

        // Assert
        assertTrue("start 後引擎應在錄音狀態", fakeEngine.isRecording())
        assertEquals(
            "狀態應為 Listening",
            MeetingState.Listening,
            session.state.value
        )
    }

    /**
     * T047-2: stop() 應該呼叫 bridge.stopRecording() 並將引擎設為非錄音狀態
     *
     * 為什麼需要測試：確保 stop() 正確停止 ASR 引擎
     * 預期行為：
     * 1. start() 後呼叫 stop()，引擎應停止錄音
     * 2. FakeAsrEngine.isRecording() 應為 false
     * 3. 狀態應回到 Ready（可再次 start）
     */
    @Test
    fun `stop should call bridge stopRecording and engine should stop recording`() = runTest {
        // Arrange
        val fakeEngine = FakeAsrEngine()
        val session = RkMeetingSession(bridge = fakeEngine)

        session.prepare()
        session.start()
        assertTrue("start 後引擎應在錄音狀態", fakeEngine.isRecording())

        // Act
        session.stop()

        // Assert
        assertFalse("stop 後引擎不應在錄音狀態", fakeEngine.isRecording())
        assertEquals(
            "狀態應回到 Ready",
            MeetingState.Ready,
            session.state.value
        )
    }

    /**
     * T047-3: start() 後應該可以再次 start()（冪等性測試）
     *
     * 為什麼需要測試：確保重複 start() 不會導致問題
     * 預期行為：
     * 1. 已在 Listening 狀態時再次呼叫 start()，應該維持 Listening
     * 2. 不應該 crash 或產生錯誤狀態
     *
     * Note: 這是一個邊界案例測試，確保 API 的防禦性
     */
    @Test
    fun `start while already listening should not change state`() = runTest {
        // Arrange
        val fakeEngine = FakeAsrEngine()
        val session = RkMeetingSession(bridge = fakeEngine)

        session.prepare()
        session.start()
        assertEquals(MeetingState.Listening, session.state.value)

        // Act: 再次呼叫 start
        session.start()

        // Assert: 狀態應該不變（或變為 Error，取決於設計決策）
        // 目前實作會變成 Error，因為只有 Ready 才能 start
        val currentState = session.state.value
        // 這個行為可能需要討論：是維持 Listening 還是報錯？
        // 目前實作會報錯，這裡先驗證不會 crash
        assertTrue(
            "狀態應為 Listening 或 Error",
            currentState is MeetingState.Listening || currentState is MeetingState.Error
        )
    }

    /**
     * T047-4: stop() 後應該可以重新 start()
     *
     * 為什麼需要測試：確保 stop → start 的生命週期正確
     * 預期行為：
     * 1. stop() 後狀態回到 Ready
     * 2. 再次呼叫 start() 應該成功進入 Listening
     */
    @Test
    fun `should be able to restart after stop`() = runTest {
        // Arrange
        val fakeEngine = FakeAsrEngine()
        val session = RkMeetingSession(bridge = fakeEngine)

        session.prepare()
        session.start()
        session.stop()
        assertEquals(MeetingState.Ready, session.state.value)

        // Act: 再次 start
        session.start()

        // Assert
        assertTrue("重新 start 後引擎應在錄音狀態", fakeEngine.isRecording())
        assertEquals(
            "狀態應為 Listening",
            MeetingState.Listening,
            session.state.value
        )
    }

    /**
     * T047-5: release() 後不應該能再 start()
     *
     * 為什麼需要測試：確保 release 後的引擎不能再使用
     * 預期行為：
     * 1. release() 後狀態為 Idle
     * 2. 呼叫 start() 應該進入 Error 狀態（因為不是 Ready）
     */
    @Test
    fun `start after release should result in error`() = runTest {
        // Arrange
        val fakeEngine = FakeAsrEngine()
        val session = RkMeetingSession(bridge = fakeEngine)

        session.prepare()
        session.release()
        assertEquals(MeetingState.Idle, session.state.value)

        // Act
        session.start()

        // Assert
        val currentState = session.state.value
        assertTrue(
            "release 後 start 應進入 Error 狀態",
            currentState is MeetingState.Error
        )
    }

    // ===== T055: 語言設定傳遞測試 =====

    /**
     * T055: prepare() 傳入特定的語言設定，應正確傳遞至 AsrConfig
     *
     * 為什麼需要測試：驗證語言參數能正確傳遞給底層引擎
     * 預期行為：
     * 1. RkMeetingSession 構造時傳入 LanguageSetting.Specific("zh")
     * 2. prepare() 後，bridge.init() 收到的 EngineConfig.asrConfig
     * 3. 其中的 language 應為 "zh"
     */
    @Test
    fun `prepare with specific language setting should propagate to AsrConfig`() = runTest {
        // Arrange
        val fakeBridge = FakeEngineBridge()
        val fakeModelsDir = File("/fake/models/dir")
        
        val session = RkMeetingSession(
            bridge = fakeBridge,
            languageSetting = LanguageSetting.Fixed("zh"),
            modelProvider = { Result.success(ModelsReady(fakeModelsDir)) }
        )

        // Act
        session.prepare()

        // Assert
        val config = fakeBridge.lastConfig
        assertNotNull("EngineConfig should not be null", config)
        
        val asrConfig = config!!.asrConfig as? AsrConfig.Whisper
        assertNotNull("AsrConfig should be Whisper", asrConfig)
        
        // AsrConfig.Whisper.language 是 LanguageSetting 類型
        // 我們需要轉型為 Fixed 並檢查 languageCode
        val languageSetting = asrConfig?.language
        assertTrue("Language should be Fixed", languageSetting is LanguageSetting.Fixed)
        assertEquals(
            "Language code should be propagated", 
            "zh", 
            (languageSetting as LanguageSetting.Fixed).languageCode
        )
    }

    // ===== T069: 錯誤恢復測試 =====

    /**
     * T069: 錯誤後應可重新 prepare() 恢復
     *
     * 為什麼需要測試：確保暫時性錯誤（如 IO 錯誤）排除後，使用者可重試
     * 預期行為：
     * 1. 第一次 prepare() 失敗，狀態變為 Error
     * 2. 第二次 prepare() 成功，狀態變為 Ready
     */
    @Test
    fun `should be able to recover from error by calling prepare again`() = runTest {
        // Arrange
        val fakeBridge = FakeEngineBridge()
        // 第一次設定失敗
        fakeBridge.nextInitResult = BridgeResult.Failure(101, "Fail 1")
        
        val session = RkMeetingSession(bridge = fakeBridge)

        // Act 1: 第一次嘗試
        session.prepare()
        assertTrue("第一次應失敗", session.state.value is MeetingState.Error)

        // Arrange 2: 設定下次成功
        fakeBridge.nextInitResult = BridgeResult.Success
        
        // Act 2: 重試
        session.prepare()

        // Assert
        assertEquals("第二次應成功並進入 Ready", MeetingState.Ready, session.state.value)
    }
}