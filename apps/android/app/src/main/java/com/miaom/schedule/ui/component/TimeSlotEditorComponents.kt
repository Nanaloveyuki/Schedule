package com.miaom.schedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TimeSlotFormFields(
    label: String,
    onLabelChange: (String) -> Unit,
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    endTime: String,
    onEndTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timePreview = when {
        startTime.isBlank() && endTime.isBlank() -> "未设置时间"
        else -> "${startTime.ifBlank { "--:--" }} - ${endTime.ifBlank { "--:--" }}"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text("节次名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        EditorPlaceholderRow(
            title = "时间",
            description = "设置开始和结束时间",
            badgeText = "编辑"
        )
        OutlinedTextField(
            value = startTime,
            onValueChange = onStartTimeChange,
            label = { Text("开始时间") },
            placeholder = { Text("08:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("HH:MM") }
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = onEndTimeChange,
            label = { Text("结束时间") },
            placeholder = { Text("08:45") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("HH:MM") }
        )
        EditorInlineNote(timePreview)
    }
}
