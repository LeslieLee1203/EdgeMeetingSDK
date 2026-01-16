package com.edgemeeting.engine

// 為了讓時間可控以便測試節奏與停止行為，抽出時間來源介面。
interface TimeSource {
    fun nowMs(): Long
}
