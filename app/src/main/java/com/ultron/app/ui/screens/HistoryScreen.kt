package com.ultron.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultron.app.data.local.Category
import com.ultron.app.data.local.SendStatus
import com.ultron.app.data.local.VoiceEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(entries: List<VoiceEntry>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            VoiceEntryCard(entry)
        }
    }
}

@Composable
private fun VoiceEntryCard(entry: VoiceEntry) {
    val categoryLabel = when (entry.categoryHint) {
        Category.TODO -> "할일"
        Category.SCHEDULE -> "일정"
        Category.ELN -> "실험"
        Category.CHAT -> "대화"
        null -> "미분류"
    }
    val statusIcon = when (entry.sendStatus) {
        SendStatus.SENT -> "✅"
        SendStatus.PENDING -> "⏳"
        SendStatus.FAILED -> "❌"
    }
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("[$categoryLabel] $statusIcon", style = MaterialTheme.typography.labelMedium)
                Text(dateFormat.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(entry.transcript, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
