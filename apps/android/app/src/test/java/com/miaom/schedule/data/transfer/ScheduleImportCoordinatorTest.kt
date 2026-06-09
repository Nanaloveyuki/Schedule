package com.miaom.schedule.data.transfer

import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.state.EditorCommand
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.platform.network.RemoteScheduleFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleImportCoordinatorTest {
    @Test
    fun `import html schedule bytes even when source mime claims legacy excel`() = runBlocking {
        val store = InMemoryScheduleStore()
        val coordinator = ScheduleImportCoordinator(store, RemoteScheduleFetcher())
        val html = """
            <table>
              <tr><th>周几</th><th>节次</th><th>课程</th><th>教师</th><th>地点</th><th>周次</th></tr>
              <tr><td>周一</td><td>1-2节</td><td>高等数学</td><td>张老师</td><td>A101</td><td>1-8周</td></tr>
            </table>
        """.trimIndent()

        val result = coordinator.importBytes(
            bytes = html.encodeToByteArray(),
            contentType = "application/vnd.ms-excel",
            fromFile = true
        )

        assertTrue(result.statusMessage.contains("HTML 表格"))
        assertEquals(1, result.document.courseEntries.size)
        assertEquals("高等数学", result.document.courseEntries.first().name)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals("1-2节", result.document.timeSlotTemplates.first().label)
    }

    private class InMemoryScheduleStore : ScheduleStore {
        private val documentState = MutableStateFlow(ScheduleDocument())
        private val pendingCreatedTimeSlotIdState = MutableStateFlow<String?>(null)
        private val undoStateState = MutableStateFlow(UndoState())

        override val document: StateFlow<ScheduleDocument> = documentState
        override val pendingCreatedTimeSlotId: StateFlow<String?> = pendingCreatedTimeSlotIdState
        override val undoState: StateFlow<UndoState> = undoStateState

        override suspend fun edit(transform: (ScheduleDocument) -> ScheduleDocument): ScheduleDocument {
            val updated = transform(documentState.value)
            documentState.value = updated
            return updated
        }

        override suspend fun apply(command: EditorCommand): ScheduleDocument {
            return documentState.value
        }

        override suspend fun undo(): ScheduleDocument = documentState.value

        override suspend fun redo(): ScheduleDocument = documentState.value

        override fun clearHistory() {
            undoStateState.value = UndoState()
        }

        override suspend fun createTimeSlotAndReturnId(
            label: String,
            startTime: String,
            endTime: String
        ): String {
            return pendingCreatedTimeSlotIdState.value.orEmpty()
        }

        override fun markCreatedTimeSlotHandled(timeSlotId: String) {
            if (pendingCreatedTimeSlotIdState.value == timeSlotId) {
                pendingCreatedTimeSlotIdState.value = null
            }
        }
    }
}
