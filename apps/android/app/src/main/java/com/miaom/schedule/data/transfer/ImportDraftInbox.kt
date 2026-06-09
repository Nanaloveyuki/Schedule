package com.miaom.schedule.data.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PendingImportDraft(
    val id: String = UUID.randomUUID().toString(),
    val sourceLabel: String,
    val statusMessage: String,
    val stagedImportText: String,
    val rawRecognizedText: String = "",
    val displayRecognizedText: String = "",
    val parsedText: String = "",
    val warnings: List<String> = emptyList()
)

class ImportDraftInbox {
    private val draftState = MutableStateFlow<PendingImportDraft?>(null)

    val draft: StateFlow<PendingImportDraft?> = draftState.asStateFlow()

    fun stage(draft: PendingImportDraft) {
        draftState.value = draft
    }

    fun clearDraft(draftId: String? = null) {
        val current = draftState.value ?: return
        if (draftId == null || current.id == draftId) {
            draftState.value = null
        }
    }
}
