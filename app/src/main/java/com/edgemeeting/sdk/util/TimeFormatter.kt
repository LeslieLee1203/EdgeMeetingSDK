package com.edgemeeting.sdk.util

/**
 * 時間格式化工具
 * 將毫秒時間戳轉換為 HH:MM:SS 格式
 */
object TimeFormatter {

    /**
     * 將毫秒轉換為 HH:MM:SS 格式
     *
     * @param millis 毫秒數
     * @return 格式化後的時間字串，例如 "00:01:23"
     */
    fun formatMillisToTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
