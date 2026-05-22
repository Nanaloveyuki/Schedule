package com.miaom.schedule.ui.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miaom.schedule.domain.model.Course

data class ScheduleCellTarget(
    val dayOfWeek: Int,
    val slotId: String
)

enum class CourseClipboardMode {
    Copy,
    Cut
}

data class CourseClipboardSnapshot(
    val sourceCourseId: String,
    val course: Course,
    val mode: CourseClipboardMode
)

class ScheduleCourseActionManager {
    var clipboard by mutableStateOf<CourseClipboardSnapshot?>(null)
        private set

    var selectedCourseId by mutableStateOf<String?>(null)
        private set

    var selectedCellTarget by mutableStateOf<ScheduleCellTarget?>(null)
        private set

    fun selectCourse(courseId: String, target: ScheduleCellTarget) {
        selectedCourseId = courseId
        selectedCellTarget = target
    }

    fun selectCell(target: ScheduleCellTarget) {
        selectedCellTarget = target
    }

    fun clearSelectedCourse() {
        selectedCourseId = null
    }

    fun copy(course: Course) {
        clipboard = CourseClipboardSnapshot(
            sourceCourseId = course.id,
            course = course,
            mode = CourseClipboardMode.Copy
        )
        selectCourse(course.id, ScheduleCellTarget(course.dayOfWeek, course.slotId))
    }

    fun cut(course: Course) {
        clipboard = CourseClipboardSnapshot(
            sourceCourseId = course.id,
            course = course,
            mode = CourseClipboardMode.Cut
        )
        selectCourse(course.id, ScheduleCellTarget(course.dayOfWeek, course.slotId))
    }

    fun clearClipboard() {
        clipboard = null
    }

    fun canPasteInto(target: ScheduleCellTarget): Boolean {
        val currentClipboard = clipboard ?: return false
        return currentClipboard.mode != CourseClipboardMode.Cut ||
            currentClipboard.course.dayOfWeek != target.dayOfWeek ||
            currentClipboard.course.slotId != target.slotId
    }

    fun syncSelection(existingCourseIds: Set<String>, existingSlotIds: Set<String>) {
        if (selectedCourseId != null && selectedCourseId !in existingCourseIds) {
            selectedCourseId = null
        }
        val currentTarget = selectedCellTarget
        if (currentTarget != null && currentTarget.slotId !in existingSlotIds) {
            selectedCellTarget = null
        }
    }

    fun onPasteCompleted(target: ScheduleCellTarget, pastedCourseId: String) {
        selectedCourseId = pastedCourseId
        selectedCellTarget = target
        if (clipboard?.mode == CourseClipboardMode.Cut) {
            clipboard = null
        }
    }
}
