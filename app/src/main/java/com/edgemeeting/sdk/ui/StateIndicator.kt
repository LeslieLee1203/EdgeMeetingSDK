package com.edgemeeting.sdk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.edgemeeting.sdk.R
import com.edgemeeting.core.model.MeetingState

@Composable
fun StateIndicator(state: MeetingState) {
    val (color, text) = when (state) {
        is MeetingState.Idle -> Color.Gray to stringResource(id = R.string.state_idle)
        is MeetingState.Preparing -> Color.Blue to stringResource(id = R.string.state_preparing)
        is MeetingState.Ready -> Color(0xFF4CAF50) to stringResource(id = R.string.state_ready) // Green
        is MeetingState.Listening -> Color.Red to stringResource(id = R.string.state_listening)
        is MeetingState.Error -> Color.Red to stringResource(id = R.string.state_error, state.message)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(id = R.string.state_current), style = MaterialTheme.typography.labelLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier.padding(8.dp)
        )
    }
}
