package com.edgemeeting.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ModelConstants 常數測試
 *
 * 為什麼需要這些測試：
 * 1. 確保模型檔案名稱與實際 RKNN 模型檔一致，避免執行期找不到檔案
 * 2. 確保 Assets 與 Runtime 路徑常數正確，供 ModelAssetManager 使用
 * 3. 作為單一真相來源（Single Source of Truth），避免路徑字串散落各處
 */
class ModelConstantsTest {

    @Test
    fun `ASSETS_MODEL_DIR should be models`() {
        // 為什麼要測試這個：確保 assets 子目錄名稱正確，與實際目錄結構一致
        assertEquals("models", ModelConstants.ASSETS_MODEL_DIR)
    }

    @Test
    fun `RUNTIME_MODEL_DIR should be models`() {
        // 為什麼要測試這個：確保 runtime 複製目標目錄名稱正確
        assertEquals("models", ModelConstants.RUNTIME_MODEL_DIR)
    }

    @Test
    fun `EXPECTED_MODEL_FILES should contain encoder and decoder`() {
        // 為什麼要測試這個：確保預期的模型檔案清單完整，用於檔案完整性驗證
        val expectedFiles = ModelConstants.EXPECTED_MODEL_FILES

        // 應包含 encoder, decoder, vocab_en filters 共 4 個檔案
        assertEquals(4, expectedFiles.size)

        // 驗證檔案名稱正確
        assertTrue(
            "Should contain whisper_encoder_base_20s.rknn",
            expectedFiles.contains("whisper_encoder_base_20s.rknn")
        )
        assertTrue(
            "Should contain whisper_decoder_base_20s.rknn",
            expectedFiles.contains("whisper_decoder_base_20s.rknn")
        )
        assertTrue(
            "Should contain vocab_en.txt",
            expectedFiles.contains("vocab_en.txt")
        )
        assertTrue(
            "Should contain mel_80_filters.txt",
            expectedFiles.contains("mel_80_filters.txt")
        )
    }

    @Test
    fun `EXPECTED_MODEL_FILES should not contain duplicates`() {
        // 為什麼要測試這個：避免清單中有重複項目，確保資料品質
        val expectedFiles = ModelConstants.EXPECTED_MODEL_FILES
        val uniqueFiles = expectedFiles.toSet()

        assertEquals(
            "Model files list should not contain duplicates",
            expectedFiles.size,
            uniqueFiles.size
        )
    }
}
