package com.miaom.schedule.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.ui.component.EditorHistoryActions
import com.miaom.schedule.ui.component.EditorInlineNote
import com.miaom.schedule.ui.component.EditorSectionCard
import com.miaom.schedule.ui.component.TimeSlotFormFields
import com.miaom.schedule.ui.viewmodel.TimeSlotEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotEditorScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val viewModel: TimeSlotEditorViewModel = viewModel(
        factory = TimeSlotEditorViewModel.factory(
            repository = appContainer.scheduleRepository,
            scheduleStore = appContainer.scheduleStore
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var editingSlotId by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var slotExpanded by remember { mutableStateOf(false) }
    val canSave = label.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank()
    val editingSlot = uiState.slots.firstOrNull { it.id == editingSlotId }

    fun resetForm() {
        editingSlotId = null
        label = ""
        startTime = ""
        endTime = ""
        slotExpanded = false
    }

    LaunchedEffect(uiState.slots, editingSlotId) {
        if (editingSlotId != null && editingSlot == null) {
            resetForm()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时间段编辑") },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EditorHistoryActions(
                    canUndo = uiState.undoState.canUndo,
                    canRedo = uiState.undoState.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo
                )
            }

            item {
                EditorSectionCard(
                    title = if (editingSlotId == null) "时间段" else "编辑时间段"
                ) {
                    ExposedDropdownMenuBox(
                        expanded = slotExpanded && uiState.slots.isNotEmpty(),
                        onExpandedChange = { expanded ->
                            if (uiState.slots.isNotEmpty()) {
                                slotExpanded = expanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = editingSlot?.let { "${it.label} ${it.startTime}-${it.endTime}" }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("已有时间段") },
                            placeholder = { Text("不选则新建") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = slotExpanded && uiState.slots.isNotEmpty())
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = slotExpanded && uiState.slots.isNotEmpty(),
                            onDismissRequest = { slotExpanded = false }
                        ) {
                            uiState.slots.forEach { slot ->
                                DropdownMenuItem(
                                    text = { Text("${slot.label} ${slot.startTime}-${slot.endTime}") },
                                    onClick = {
                                        editingSlotId = slot.id
                                        label = slot.label
                                        startTime = slot.startTime
                                        endTime = slot.endTime
                                        slotExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    TextButton(onClick = ::resetForm) {
                        Text("新建时间段")
                    }
                    TimeSlotFormFields(
                        label = label,
                        onLabelChange = { label = it },
                        startTime = startTime,
                        onStartTimeChange = { startTime = it },
                        endTime = endTime,
                        onEndTimeChange = { endTime = it }
                    )
                    if (editingSlotId != null) {
                        EditorInlineNote("更新后会同步到使用该时间段的课程。")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveTimeSlot(
                                slotId = editingSlotId,
                                label = label,
                                startTime = startTime,
                                endTime = endTime
                            )
                            resetForm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSave
                    ) {
                        Text(if (editingSlotId == null) "保存模板" else "更新模板")
                    }
                    if (editingSlotId != null) {
                        TextButton(onClick = ::resetForm) {
                            Text("取消编辑")
                        }
                    }
                }
            }

            item {
                Text("已保存时间段", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.slots, key = { it.id }) { slot ->
                TimeSlotSummaryCard(
                    slot = slot,
                    onEdit = {
                        editingSlotId = slot.id
                        label = slot.label
                        startTime = slot.startTime
                        endTime = slot.endTime
                        slotExpanded = false
                    },
                    onDelete = { viewModel.deleteTimeSlot(slot.id) }
                )
            }
        }
    }
}

@Composable
private fun TimeSlotSummaryCard(
    slot: TimeSlot,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(slot.label, style = MaterialTheme.typography.titleMedium)
            Text("${slot.startTime} - ${slot.endTime}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}
