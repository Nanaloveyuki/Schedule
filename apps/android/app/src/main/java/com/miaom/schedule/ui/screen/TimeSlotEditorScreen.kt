package com.miaom.schedule.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.miaom.schedule.ui.viewmodel.TimeSlotEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotEditorScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val viewModel: TimeSlotEditorViewModel = viewModel(
        factory = TimeSlotEditorViewModel.factory(appContainer.scheduleRepository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var label by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

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
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("新增时间段", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("节次名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("开始时间，例如 08:00") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("结束时间，例如 08:45") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (label.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank()) {
                                    viewModel.saveTimeSlot(label, startTime, endTime)
                                    label = ""
                                    startTime = ""
                                    endTime = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存时间段")
                        }
                    }
                }
            }

            item {
                Text("已保存时间段", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.slots, key = { it.id }) { slot ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(slot.label, style = MaterialTheme.typography.titleMedium)
                        Text("${slot.startTime} - ${slot.endTime}")
                        TextButton(onClick = { viewModel.deleteTimeSlot(slot.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
