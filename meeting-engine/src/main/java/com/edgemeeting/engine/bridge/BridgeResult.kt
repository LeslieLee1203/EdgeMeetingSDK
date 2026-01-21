package com.edgemeeting.engine.bridge

/**
 * JNI Bridge 操作結果
 *
 * 為什麼使用 sealed interface：
 * 1. 型別安全：強制處理成功/失敗兩種情況
 * 2. when 表達式完整性：編譯器檢查所有分支
 * 3. 清晰的成功/失敗語義
 */
sealed interface BridgeResult {
    /**
     * 操作成功（無額外資料）
     */
    data object Success : BridgeResult

    /**
     * 操作失敗
     *
     * @property code 錯誤碼（參考 ErrorCodes 常數）
     * @property message 可讀錯誤訊息
     */
    data class Failure(val code: Int, val message: String) : BridgeResult
}

/**
 * 錯誤碼常數
 *
 * 分類設計：
 * - 1xxx：模型相關錯誤
 * - 2xxx：音訊相關錯誤
 * - 9xxx：通用錯誤
 *
 * 為什麼使用 object 而非 enum：
 * 1. 簡單：只需要常數，不需要 enum 的額外功能
 * 2. 與 C++ 錯誤碼對應方便（直接使用 Int）
 * 3. 擴充性：未來可輕鬆新增常數
 */
object ErrorCodes {
    // 1xxx: 模型相關錯誤
    /**
     * 模型檔案不存在
     *
     * 觸發情境：assets 中缺少必要的 .rknn 模型檔案
     */
    const val ERR_MODEL_NOT_FOUND = 1001

    /**
     * 模型載入失敗
     *
     * 觸發情境：rknn_init() 回傳錯誤（檔案損壞、版本不相容等）
     */
    const val ERR_MODEL_LOAD_FAILED = 1002

    /**
     * 不支援的 ASR 類型
     *
     * 觸發情境：AsrConfig 為未知的 sealed class 子類
     */
    const val ERR_ASR_TYPE_UNSUPPORTED = 1003

    // 2xxx: 音訊相關錯誤
    /**
     * 音訊格式錯誤
     *
     * 觸發情境：PCM 格式不符合預期（取樣率、聲道數等）
     */
    const val ERR_AUDIO_FORMAT = 2001

    // 9xxx: 通用錯誤
    /**
     * 未知錯誤
     *
     * 觸發情境：無法分類的錯誤，或 C++ 層未預期的錯誤
     */
    const val ERR_UNKNOWN = 9999
}