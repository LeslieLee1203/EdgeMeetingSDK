package com.edgemeeting.sdk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.sdk.R

/**
 * 語言選擇器 (Phase 4)
 *
 * 為什麼需要：允許使用者選擇指定語言（如英文、中文）
 * 設計考量：
 * - 預設為 English
 * - 只在 Idle 或 Error 狀態可切換（避免執行中切換導致狀態不一致）
 * - 使用 DropdownMenu 提供清晰的選項
 */
@Composable
fun LanguageSelector(
    selectedLanguage: LanguageSetting,
    onLanguageChanged: (LanguageSetting) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val languageOptions = supportedLanguageOptions()
    val currentLabelResId = languageLabelResId(selectedLanguage)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.language_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(stringResource(id = currentLabelResId))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languageOptions.forEach { setting ->
                val labelResId = languageLabelResId(setting)
                DropdownMenuItem(
                    text = { Text(stringResource(id = labelResId)) },
                    onClick = {
                        onLanguageChanged(setting)
                        expanded = false
                    }
                )
            }
        }
    }
}
