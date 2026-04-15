package com.ultron.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    wakeWordEnabled: Boolean,
    voskDownloaded: Boolean,
    autoStartOnBoot: Boolean,
    beepOnRecord: Boolean,
    onWakeWordToggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onBeepToggle: (Boolean) -> Unit,
    onDownloadVosk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // 웨이크워드 토글
        SettingsToggle(
            title = "웨이크워드 감지",
            subtitle = "\"울트론\"이라고 말하면 녹음 시작",
            checked = wakeWordEnabled,
            onCheckedChange = onWakeWordToggle,
            enabled = voskDownloaded
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 부팅 시 자동 시작
        SettingsToggle(
            title = "부팅 시 자동 시작",
            subtitle = "기기 재부팅 후 자동으로 서비스 시작",
            checked = autoStartOnBoot,
            onCheckedChange = onAutoStartToggle
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 녹음 시작 비프음
        SettingsToggle(
            title = "녹음 시작 비프음",
            subtitle = "녹음 시작/종료 시 비프음 재생",
            checked = beepOnRecord,
            onCheckedChange = onBeepToggle
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Vosk 모델 다운로드
        if (!voskDownloaded) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("한국어 음성 모델 필요", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "웨이크워드 감지를 위해 Vosk 한국어 모델(~50MB)을 다운로드해야 합니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDownloadVosk) {
                        Text("다운로드")
                    }
                }
            }
        } else {
            Text("한국어 음성 모델 설치됨", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
