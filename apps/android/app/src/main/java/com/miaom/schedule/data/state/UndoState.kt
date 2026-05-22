package com.miaom.schedule.data.state

data class UndoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val undoDepth: Int = 0,
    val redoDepth: Int = 0
)
