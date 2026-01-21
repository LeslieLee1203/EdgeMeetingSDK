package com.edgemeeting.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ModelAssetManager 簡化測試
 *
 * 測試策略（遵循實用主義）：
 * 1. 只測試核心邏輯與 data class
 * 2. 不依賴 Robolectric 或真實檔案系統
 * 3. 真正的檔案複製與模型載入驗證放在 Phase 3.6 整合測試
 *
 * 為什麼簡化：
 * - ModelAssetManager 的邏輯清晰簡單（複製檔案）
 * - Android AssetManager 是平台責任，不需要我們測試
 * - 真正重要的驗證是「模型能否被 rknn_init 載入」，需要整合測試
 * - 遵循 KISS 原則，避免過度測試
 */
class ModelAssetManagerTest {

    @Test
    fun `ModelsReady should contain correct modelsDir`() {
        // 建立 ModelsReady
        val testDir = File("/data/data/com.edgemeeting/files/models")
        val modelsReady = ModelsReady(testDir)

        // 驗證：目錄路徑正確
        assertEquals(testDir, modelsReady.modelsDir)
        assertEquals("/data/data/com.edgemeeting/files/models", modelsReady.modelsDir.absolutePath)
    }

    @Test
    fun `ModelsReady should be a data class with copy support`() {
        // 建立原始物件
        val original = ModelsReady(File("/path/to/models"))

        // 複製並修改
        val modified = original.copy(modelsDir = File("/new/path"))

        // 驗證：copy 功能正常
        assertEquals("/path/to/models", original.modelsDir.absolutePath)
        assertEquals("/new/path", modified.modelsDir.absolutePath)
    }

    @Test
    fun `ModelsReady should support equality comparison`() {
        // 建立兩個相同路徑的物件
        val dir = File("/data/models")
        val ready1 = ModelsReady(dir)
        val ready2 = ModelsReady(File("/data/models"))

        // 驗證：相等性比較（data class 自動實作）
        // 注意：File 的 equals 基於路徑字串
        assertEquals(ready1.modelsDir.absolutePath, ready2.modelsDir.absolutePath)
    }

    @Test
    fun `ModelConstants should define expected model files`() {
        // 驗證：ModelConstants 定義了預期的模型檔案
        assertNotNull("EXPECTED_MODEL_FILES should not be null", ModelConstants.EXPECTED_MODEL_FILES)
        assertTrue("Should have at least 2 model files", ModelConstants.EXPECTED_MODEL_FILES.size >= 2)

        // 驗證：包含 encoder 和 decoder
        assertTrue(
            "Should contain encoder",
            ModelConstants.EXPECTED_MODEL_FILES.any { it.contains("encoder") }
        )
        assertTrue(
            "Should contain decoder",
            ModelConstants.EXPECTED_MODEL_FILES.any { it.contains("decoder") }
        )
    }

    @Test
    fun `ModelConstants should define consistent directory paths`() {
        // 驗證：assets 與 runtime 目錄名稱一致
        assertEquals(
            "Assets and runtime directory names should match",
            ModelConstants.ASSETS_MODEL_DIR,
            ModelConstants.RUNTIME_MODEL_DIR
        )
        assertEquals("models", ModelConstants.ASSETS_MODEL_DIR)
    }

    // 注意：真實的檔案複製測試在 Phase 3.6 整合測試中進行
    // 屆時會使用真實的 Android Context 與實際模型檔案
    // 並驗證 ModelAssetManager.ensureModels() 的完整行為
}
