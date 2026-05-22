package com.miaom.schedule.data.state

class UndoManager {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()

    fun record(command: EditorCommand) {
        undoStack.addLast(command)
        redoStack.clear()
    }

    fun takeUndoCommand(): EditorCommand? {
        val command = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(command)
        return command
    }

    fun takeRedoCommand(): EditorCommand? {
        val command = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(command)
        return command
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun snapshot(): UndoState {
        return UndoState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoLabel = undoStack.lastOrNull()?.label,
            redoLabel = redoStack.lastOrNull()?.label,
            undoDepth = undoStack.size,
            redoDepth = redoStack.size
        )
    }
}
