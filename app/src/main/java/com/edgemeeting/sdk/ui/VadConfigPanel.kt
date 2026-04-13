package com.edgemeeting.sdk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.model.VadConfig
import kotlin.math.roundToInt

// ── 進入點：展開/收合切換按鈕 ───────────────────────────────────────────────

@Composable
fun VadDebugToggle(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onToggle, modifier = modifier) {
        Text(
            text = if (isExpanded) "▲ VAD 參數調整" else "▼ VAD 參數調整",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── VAD 參數調整面板 ─────────────────────────────────────────────────────────

@Composable
fun VadConfigPanel(
    config: VadConfig,
    onConfigChanged: (VadConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        // 快速靜音偵測（detectSilence）
        SectionHeader("快速靜音偵測（detectSilence）")
        FloatSlider(
            label = "fastSilenceRms",
            value = config.fastSilenceRmsThreshold,
            range = 100f..5000f,
            onValueChange = { onConfigChanged(config.copy(fastSilenceRmsThreshold = it)) }
        )
        FloatSlider(
            label = "fastSilenceZcr",
            value = config.fastSilenceZcrThreshold,
            range = 0.01f..0.30f,
            onValueChange = { onConfigChanged(config.copy(fastSilenceZcrThreshold = it)) }
        )

        SectionDivider()

        // 精確 VAD 分類（detectVadState）
        SectionHeader("精確 VAD 分類（detectVadState）")
        FloatSlider(
            label = "silenceRms",
            value = config.silenceRmsThreshold,
            range = 5f..300f,
            onValueChange = { onConfigChanged(config.copy(silenceRmsThreshold = it)) }
        )
        FloatSlider(
            label = "pauseRms",
            value = config.pauseRmsThreshold,
            range = 20f..800f,
            onValueChange = { onConfigChanged(config.copy(pauseRmsThreshold = it)) }
        )
        FloatSlider(
            label = "pauseZcr",
            value = config.pauseZcrThreshold,
            range = 0.05f..0.60f,
            onValueChange = { onConfigChanged(config.copy(pauseZcrThreshold = it)) }
        )

        SectionDivider()

        // 推論觸發時間閾值（pushAudio）
        SectionHeader("推論觸發時間閾值（ms）")
        IntSlider(
            label = "silenceThresholdMs",
            value = config.silenceThresholdMs,
            range = 200..3000,
            step = 100,
            onValueChange = { onConfigChanged(config.copy(silenceThresholdMs = it)) }
        )
        IntSlider(
            label = "pauseThresholdMs",
            value = config.pauseThresholdMs,
            range = 500..5000,
            step = 100,
            onValueChange = { onConfigChanged(config.copy(pauseThresholdMs = it)) }
        )
        IntSlider(
            label = "minSpeechDurationMs",
            value = config.minSpeechDurationMs,
            range = 500..5000,
            step = 100,
            onValueChange = { onConfigChanged(config.copy(minSpeechDurationMs = it)) }
        )
        IntSlider(
            label = "intermediateIntervalMs",
            value = config.intermediateIntervalMs,
            range = 1000..15000,
            step = 500,
            onValueChange = { onConfigChanged(config.copy(intermediateIntervalMs = it)) }
        )

        SectionDivider()

        // 樣本大小限制（pushAudio）
        SectionHeader("樣本大小（samples = 秒 × 16000）")
        IntSlider(
            label = "minSamples",
            value = config.minSamples,
            range = 16000..96000,
            step = 8000,
            onValueChange = { onConfigChanged(config.copy(minSamples = it)) }
        )
        IntSlider(
            label = "maxSamples",
            value = config.maxSamples,
            range = 96000..320000,
            step = 16000,
            onValueChange = { onConfigChanged(config.copy(maxSamples = it)) }
        )

        SectionDivider()

        // 能量預檢（shouldSkipInference）
        SectionHeader("能量預檢（shouldSkipInference）")
        FloatSlider(
            label = "energyThreshold",
            value = config.energyThreshold,
            range = 5f..150f,
            onValueChange = { onConfigChanged(config.copy(energyThreshold = it)) }
        )
        FloatSlider(
            label = "silenceRatioThreshold",
            value = config.silenceRatioThreshold,
            range = 0.50f..0.99f,
            onValueChange = { onConfigChanged(config.copy(silenceRatioThreshold = it)) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onConfigChanged(VadConfig.Default) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置為預設值")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── 內部元件 ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider()
}

@Composable
private fun FloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(2f)
        )
        Text(
            text = "%.3f".format(value),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(0.8f)
                .padding(start = 4.dp)
        )
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    val steps = ((range.last - range.first) / step) - 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / step).roundToInt() * step
                onValueChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.weight(2f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(0.8f)
                .padding(start = 4.dp)
        )
    }
}
