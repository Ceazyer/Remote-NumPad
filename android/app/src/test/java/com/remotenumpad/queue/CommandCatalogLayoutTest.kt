package com.remotenumpad.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandCatalogLayoutTest {
    @Test
    fun calculatorOrderMatchesTheApprovedFiveRowLayout() {
        assertEquals(
            listOf(
                "UNDO", "PREV_CELL", "EDIT", "DELETE",
                "7", "8", "9", "BACKSPACE",
                "4", "5", "6", "-",
                "1", "2", "3", "NEXT_CELL",
                "0", ".", "ENTER"
            ),
            CommandCatalog.keypad.map { it.command }
        )
    }

    @Test
    fun navigationStripKeepsAllFourDirectionsAboveTheKeypad() {
        assertEquals(
            listOf("LEFT", "UP", "DOWN", "RIGHT"),
            CommandCatalog.navigation.filterNotNull().map { it.command }
        )
    }

    @Test
    fun editingKeepsEditAndUndoWithoutDuplicatingFileControls() {
        assertEquals(
            listOf("EDIT", "UNDO"),
            CommandCatalog.editing.filterNotNull().map { it.command }
        )
    }
}
