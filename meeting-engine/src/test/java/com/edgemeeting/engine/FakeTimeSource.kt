package com.edgemeeting.engine

// 為了可預測地控制時間推進，避免使用真實時鐘造成測試不穩定。
class FakeTimeSource(
    startMs: Long = 0L
) : TimeSource {
    private var currentMs: Long = startMs

    override fun nowMs(): Long = currentMs

    fun advanceBy(deltaMs: Long) {
        require(deltaMs >= 0) { "deltaMs must be >= 0" }
        currentMs += deltaMs
    }

    fun setNowMs(valueMs: Long) {
        require(valueMs >= 0) { "valueMs must be >= 0" }
        currentMs = valueMs
    }
}
