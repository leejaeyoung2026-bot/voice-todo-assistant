package com.ultron.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultron.app.ui.UiState

@Composable
fun HomeScreen(
    uiState: UiState,
    pendingCount: Int,
    onToggleService: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when {
                uiState.isRecording -> "녹음 중..."
                uiState.isListening -> "\"울트론\" 대기 중"
                else -> "중지됨"
            },
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (uiState.budsConnected) "Buds 3 Pro 연결됨" else "Buds 미연결",
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.budsConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(48.dp))

        val buttonColor by animateColorAsState(
            if (uiState.isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            label = "button_color"
        )

        FilledIconButton(
            onClick = onToggleRecording,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = buttonColor),
            enabled = uiState.isListening
        ) {
            Icon(
                imageVector = if (uiState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "녹음 토글",
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(onClick = onToggleService) {
            Text(if (uiState.isListening) "서비스 중지" else "서비스 시작")
        }

        if (pendingCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "전송 대기: ${pendingCount}건",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
