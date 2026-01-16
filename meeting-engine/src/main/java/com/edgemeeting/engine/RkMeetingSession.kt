package com.edgemeeting.engine

import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
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

// 建構子注入 EngineBridge，這是 TDD 可測試性的關鍵！
class RkMeetingSession(
    private val bridge: EngineBridge,
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

    init {
        // 註冊 Callback
        bridge.setCallback(object : AudioCallback {
            override fun onAudioData(data: FloatArray) {
                // 這裡會非常頻繁被呼叫 (每 160ms 一次)
                // 為了驗證 Phase 3 成功，我們印出陣列長度與第一個值
                if (data.isNotEmpty()) {
                    android.util.Log.d("JNI_CALLBACK", "Received ${data.size} frames. First: ${data[0]}")
                }
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

        // 2. 呼叫底層 C++ (這裡是同步呼叫，會卡住一下，之後我們再優化到背景執行緒)
        // 暫時寫死路徑，Phase 1 重點是架構跑通
        val defaultModelPath = "/data/local/tmp/models"
        val result = bridge.init(defaultModelPath)

        // 3. 根據底層回傳的結果，決定下一個狀態
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

        // 啟動字幕輸出（在 Listening 狀態下應該有輸出）
        startTranscriptLoop { segments ->
            val emitted = transcriptFlowInternal.tryEmit(segments)
            if (!emitted) {
                android.util.Log.w("TRANSCRIPT_FLOW", "Drop transcript emission")
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
            transcriptJob?.cancel()
            transcriptJob = null

            // 更新狀態 (回到 Ready)
            _state.value = MeetingState.Ready
        }
    }

    override fun release() {
        // 釋放前先停止字幕輸出，避免背景協程持續跑
        transcriptJob?.cancel()
        transcriptJob = null

        bridge.release()
        _state.value = MeetingState.Idle
    }
}