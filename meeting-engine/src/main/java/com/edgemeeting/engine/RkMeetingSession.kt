package com.edgemeeting.engine

import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.ErrorCodes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RK3588 實作的會議 Session
 *
 * 可測試性設計：
 * - bridge: EngineBridge 注入，測試時用 FakeAsrEngine
 * - modelProvider: 模型準備函數注入，測試時用 lambda 模擬成功/失敗
 * - timeSource: 時間來源注入，測試時用 FakeTimeSource
 * - transcriptGenerator: 字幕產生器注入，測試時用假產生器
 * - transcriptDispatcher: 協程調度器注入，測試時用 TestDispatcher
 */
class RkMeetingSession(
    private val bridge: EngineBridge,
    /**
     * 語言設定 (Phase 4 新增)
     *
     * 預設為 Auto (自動偵測)。
     * 可傳入 LanguageSetting.Fixed("zh") 指定語言。
     */
    private val languageSetting: LanguageSetting = LanguageSetting.Auto,
    /**
     * 模型準備函數
     *
     * 為什麼使用 lambda 而非直接依賴 ModelAssetManager：
     * 1. 可測試性：測試時無需 Android Context，直接注入 fake 結果
     * 2. 彈性：未來可支援其他模型來源（網路下載、SD 卡等）
     * 3. 關注分離：RkMeetingSession 不需知道模型如何準備
     *
     * 使用範例（生產環境）：
     * ```kotlin
     * val session = RkMeetingSession(
     *     bridge = JniEngineBridge(),
     *     modelProvider = { ModelAssetManager.ensureModels(context) }
     * )
     * ```
     *
     * 回傳 null 表示純錄音模式（無 ASR）。
     */
    private val modelProvider: (() -> Result<ModelsReady>)? = null,
    // 為了可測試地控制時間，預設用系統時間。
    private val timeSource: TimeSource = object : TimeSource {
        override fun nowMs(): Long = System.currentTimeMillis()
    },
    // 為了可替換字幕來源並在測試中可控，預設產生 Unknown 的段落。
    private val transcriptGenerator: TranscriptGenerator = object : TranscriptGenerator {
        override fun doGenerate(startTimeMs: Long, endTimeMs: Long): TranscriptSegment {
            return TranscriptSegment(
                id = "${startTimeMs}-${endTimeMs}",
                text = "Transcript at ${startTimeMs}ms",
                speakerId = "Unknown",
                isFinal = true,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        }
    },
    // 為了在測試中使用 TestDispatcher 控制時間。
    private val transcriptDispatcher: CoroutineDispatcher = Dispatchers.Default
) : MeetingSession {
    private val transcriptScope = CoroutineScope(SupervisorJob() + transcriptDispatcher)
    private var transcriptJob: Job? = null
    private fun stopTranscriptLoop() {
        // 統一停止與清理，避免重複邏輯分散
        transcriptJob?.cancel()
        transcriptJob = null
    }

    init {
        // 註冊 Callback
        bridge.setCallback(object : EngineCallback {
            override fun onAudioData(data: FloatArray) {
                // 這裡會非常頻繁被呼叫 (每 160ms 一次)
                // 為了驗證 Phase 3 成功，我們印出陣列長度與第一個值
//                if (data.isNotEmpty()) {
//                    android.util.Log.d("JNI_CALLBACK", "Received ${data.size} frames. First: ${data[0]}")
//                }
            }

            override fun onTranscript(segment: TranscriptSegment) {
                // 將 native 層的轉錄結果發射到 transcriptFlow
                val emitted = transcriptFlowInternal.tryEmit(listOf(segment))
                if (!emitted) {
                    android.util.Log.w("JNI_CALLBACK", "Drop transcript: ${segment.id}")
                }
            }

            override fun onError(code: Int, message: String) {
                // TODO: Phase 3 將實作錯誤處理
                android.util.Log.e("JNI_CALLBACK", "Error $code: $message")
            }
        })
    }

    // 狀態管理
    private val _state = MutableStateFlow<MeetingState>(MeetingState.Idle)
    override val state: StateFlow<MeetingState> = _state.asStateFlow()

    // 資料流：用緩衝避免產出端被 UI 訂閱速度拖慢
    private val transcriptFlowInternal = MutableSharedFlow<List<TranscriptSegment>>(
        extraBufferCapacity = 1
    )
    override val transcriptFlow: Flow<List<TranscriptSegment>> = transcriptFlowInternal.asSharedFlow()

    private fun startTranscriptLoop(emit: suspend (List<TranscriptSegment>) -> Unit) {
        if (transcriptJob?.isActive == true) {
            return
        }

        transcriptJob = transcriptScope.launch {
            val baseMs = timeSource.nowMs()
            var lastEmitMs = baseMs

            while (isActive) {
                val nowMs = timeSource.nowMs()
                val relativeStart = lastEmitMs - baseMs
                val relativeEnd = nowMs - baseMs

                val segment = try {
                    transcriptGenerator.generate(relativeStart, relativeEnd)
                } catch (error: Throwable) {
                    android.util.Log.e("TRANSCRIPT_LOOP", "Generate failed", error)
                    lastEmitMs = nowMs
                    delay(1_000)
                    continue
                }

                emit(listOf(segment))
                lastEmitMs = nowMs
                delay(1_000)
            }
        }
    }

    override fun prepare() {
        // 1. 先通知 UI 我們正在忙 (顯示轉圈圈)
        _state.value = MeetingState.Preparing

        // 2. 準備 ASR 配置
        // 若有 modelProvider，則取得模型路徑並建立 AsrConfig
        // 若無 modelProvider 或為 null，則使用純錄音模式（向後相容）
        val asrConfig: AsrConfig? = if (modelProvider != null) {
            val modelsResult = modelProvider.invoke()
            when {
                modelsResult.isSuccess -> {
                    val modelsReady = modelsResult.getOrThrow()
                    AsrConfig.Whisper(
                        modelsPath = modelsReady.modelsDir.absolutePath,
                        language = languageSetting
                    )
                }
                else -> {
                    // 模型準備失敗，直接進入 Error 狀態
                    val exception = modelsResult.exceptionOrNull()
                    _state.value = MeetingState.Error(
                        code = ErrorCodes.ERR_MODEL_NOT_FOUND,
                        message = exception?.message ?: "Model preparation failed"
                    )
                    return  // 提前結束，不繼續初始化引擎
                }
            }
        } else {
            null  // 純錄音模式
        }

        // 3. 呼叫底層 C++ 初始化
        val config = EngineConfig(asrConfig = asrConfig)
        val result = bridge.init(config)

        // 4. 根據底層回傳的結果，決定下一個狀態
        when (result) {
            is BridgeResult.Success -> {
                // 成功 -> 變成 Ready (綠燈)
                _state.value = MeetingState.Ready
            }
            is BridgeResult.Failure -> {
                // 失敗 -> 變成 Error (顯示錯誤訊息)
                _state.value = MeetingState.Error(result.code, result.message)
            }
        }
    }

    override fun start() {
        // 先做狀態檢查，只有 Ready 才能開始，避免直接進入錄音與輸出流程
        val currentState = _state.value
        if (currentState !is MeetingState.Ready) {
            _state.value = MeetingState.Error(
                code = 400,
                message = "Start called before Ready"
            )
            return
        }

        // 呼叫 JNI
        bridge.startRecording()

        // 僅在純錄音模式（無 modelProvider）時啟動模擬字幕
        // 在 ASR 模式下，字幕由 bridge.onTranscript 透過 setCallback 提供
        if (modelProvider == null) {
            startTranscriptLoop { segments ->
                val emitted = transcriptFlowInternal.tryEmit(segments)
                if (!emitted) {
                    android.util.Log.w("TRANSCRIPT_FLOW", "Drop transcript emission")
                }
            }
        }

        // 更新狀態
        _state.value = MeetingState.Listening
    }

    override fun stop() {
        val currentState = _state.value
        if (currentState is MeetingState.Listening) {

            bridge.stopRecording()

            // 停止字幕輸出
            stopTranscriptLoop()

            // 更新狀態 (回到 Ready)
            _state.value = MeetingState.Ready
        }
    }

    override fun release() {
        // 如果還在錄音，先停止錄音（確保 native 層正確釋放）
        if (_state.value is MeetingState.Listening) {
            bridge.stopRecording()
        }

        // 釋放前先停止字幕輸出，避免背景協程持續跑
        stopTranscriptLoop()

        bridge.release()
        _state.value = MeetingState.Idle
    }
}