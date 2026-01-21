package com.edgemeeting.engine

import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BridgeResult 與錯誤碼測試
 *
 * 測試目標：
 * 1. BridgeResult 基本功能
 * 2. 錯誤碼常數定義正確
 * 3. 錯誤碼範圍合理
 */
class BridgeResultTest {

    @Test
    fun `BridgeResult Success should be a singleton`() {
        // 建立兩個 Success
        val result1 = BridgeResult.Success
        val result2 = BridgeResult.Success

        // 驗證：是同一個物件（data object）
        assertTrue(result1 === result2)
    }

    @Test
    fun `BridgeResult Failure should contain code and message`() {
        // 建立 Failure
        val failure = BridgeResult.Failure(1001, "Model not found")

        // 驗證：包含正確的資訊
        assertEquals(1001, failure.code)
        assertEquals("Model not found", failure.message)
    }

    @Test
    fun `ErrorCodes should define ERR_MODEL_NOT_FOUND`() {
        // 驗證：錯誤碼定義
        assertEquals(1001, ErrorCodes.ERR_MODEL_NOT_FOUND)
    }

    @Test
    fun `ErrorCodes should define ERR_MODEL_LOAD_FAILED`() {
        // 驗證：錯誤碼定義
        assertEquals(1002, ErrorCodes.ERR_MODEL_LOAD_FAILED)
    }

    @Test
    fun `ErrorCodes should define ERR_ASR_TYPE_UNSUPPORTED`() {
        // 驗證：錯誤碼定義
        assertEquals(1003, ErrorCodes.ERR_ASR_TYPE_UNSUPPORTED)
    }

    @Test
    fun `ErrorCodes should define ERR_AUDIO_FORMAT`() {
        // 驗證：錯誤碼定義
        assertEquals(2001, ErrorCodes.ERR_AUDIO_FORMAT)
    }

    @Test
    fun `ErrorCodes should define ERR_UNKNOWN`() {
        // 驗證：錯誤碼定義
        assertEquals(9999, ErrorCodes.ERR_UNKNOWN)
    }

    @Test
    fun `ErrorCodes should use non-overlapping ranges`() {
        // 驗證：錯誤碼範圍合理
        // 1xxx: 模型相關錯誤
        assertTrue("Model errors should be in 1xxx range",
            ErrorCodes.ERR_MODEL_NOT_FOUND in 1000..1999)
        assertTrue("Model errors should be in 1xxx range",
            ErrorCodes.ERR_MODEL_LOAD_FAILED in 1000..1999)

        // 2xxx: 音訊相關錯誤
        assertTrue("Audio errors should be in 2xxx range",
            ErrorCodes.ERR_AUDIO_FORMAT in 2000..2999)

        // 9xxx: 通用錯誤
        assertTrue("Generic errors should be in 9xxx range",
            ErrorCodes.ERR_UNKNOWN in 9000..9999)
    }

    @Test
    fun `BridgeResult Failure should accept ErrorCodes constants`() {
        // 使用錯誤碼常數建立 Failure
        val failure1 = BridgeResult.Failure(ErrorCodes.ERR_MODEL_NOT_FOUND, "Model not found")
        val failure2 = BridgeResult.Failure(ErrorCodes.ERR_AUDIO_FORMAT, "Invalid format")

        // 驗證：可正確使用
        assertEquals(1001, failure1.code)
        assertEquals(2001, failure2.code)
    }
}
