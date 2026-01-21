package com.edgemeeting.engine

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import com.edgemeeting.engine.bridge.ErrorCodes
import junit.framework.TestCase.assertEquals
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

        override fun init(config: EngineConfig): BridgeResult {
            isInitCalled = true
            lastConfig = config
            return nextInitResult
        }

        // 其他方法暫時不重要，留空即可
        override fun setCallback(callback: EngineCallback) = Unit
        override fun startRecording() {}
        override fun stopRecording() {}
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
}