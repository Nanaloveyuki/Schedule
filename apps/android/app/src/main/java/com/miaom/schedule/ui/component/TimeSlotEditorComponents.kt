package com.miaom.schedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
        startTime.isBlank() && endTime.isBlank() -> "时间预览会显示在这里，例如 08:00 - 08:45。"
        else -> "时间预览：${startTime.ifBlank { "--:--" }} - ${endTime.ifBlank { "--:--" }}"
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
            title = "点击选择时间",
            description = "开始时间和结束时间会显示在这里。",
            badgeText = "时间"
        )
        Text(
            text = "手动输入时间",
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = startTime,
            onValueChange = onStartTimeChange,
            label = { Text("开始时间") },
            placeholder = { Text("例如 08:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("按 HH:MM 格式填写。") }
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = onEndTimeChange,
            label = { Text("结束时间") },
            placeholder = { Text("例如 08:45") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("按 HH:MM 格式填写。") }
        )
        EditorInlineNote(timePreview)
    }
}
