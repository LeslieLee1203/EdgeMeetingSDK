package com.edgemeeting.sdk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.edgemeeting.sdk.R
import com.edgemeeting.sdk.translation.TranscriptWithTranslation
import com.edgemeeting.sdk.util.TimeFormatter

/**
 * 字幕項目顯示（每組兩行：英文原文 + 中文翻譯）
 *
 * @param item 帶翻譯的字幕資料
 */
@Composable
fun TranscriptItemView(item: TranscriptWithTranslation) {
    val segment = item.original
    val startTime = TimeFormatter.formatMillisToTime(segment.startTimeMs)
    val endTime = TimeFormatter.formatMillisToTime(segment.endTimeMs)
    val translatingText = stringResource(id = R.string.translation_in_progress)
    val emptyTranslationText = stringResource(id = R.string.translation_empty)

    // 根據 isFinal 標誌調整英文原文樣式
    val originalTextColor = if (segment.isFinal) {
        Color.Unspecified  // 正常顏色（黑色/白色，根據主題）
    } else {
        Color.Gray  // 淺灰色表示臨時結果
    }

    val originalTextStyle = if (segment.isFinal) {
        MaterialTheme.typography.bodyMedium  // 正常字體
    } else {
        MaterialTheme.typography.bodyMedium.copy(
            fontStyle = FontStyle.Italic  // 斜體表示臨時
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 第一行：時間戳 + 英文原文
        Text(
            text = stringResource(
                id = R.string.transcript_line_format,
                startTime,
                endTime,
                segment.text
            ),
            style = originalTextStyle,
            color = originalTextColor
        )

        // 第二行：中文翻譯
        Text(
            text = when {
                item.isTranslating -> translatingText
                item.translatedText != null -> item.translatedText
                else -> emptyTranslationText
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isTranslating) Color.Gray else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
