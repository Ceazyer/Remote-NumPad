package com.remotenumpad.queue

data class InputCommand(val label: String, val command: String, val columnSpan: Int = 1)

object CommandCatalog {
    val keypad: List<InputCommand> = listOf(
        InputCommand("撤销", "UNDO"), InputCommand("上一格", "PREV_CELL"), InputCommand("编辑", "EDIT"), InputCommand("Del 删除", "DELETE"),
        InputCommand("7", "7"), InputCommand("8", "8"), InputCommand("9", "9"), InputCommand("退格", "BACKSPACE"),
        InputCommand("4", "4"), InputCommand("5", "5"), InputCommand("6", "6"), InputCommand("负号", "-"),
        InputCommand("1", "1"), InputCommand("2", "2"), InputCommand("3", "3"), InputCommand("下一格", "NEXT_CELL"),
        InputCommand("0", "0", columnSpan = 2), InputCommand(".", "."), InputCommand("Enter", "ENTER")
    )

    val navigation: List<InputCommand?> = listOf(
        InputCommand("向左移动一格", "LEFT"), InputCommand("向上移动一格", "UP"),
        InputCommand("向下移动一格", "DOWN"), InputCommand("向右移动一格", "RIGHT")
    )

    val editing: List<InputCommand?> = listOf(
        InputCommand("编辑", "EDIT"), InputCommand("撤销", "UNDO")
    )

    val fileControls: List<InputCommand> = listOf(
        InputCommand("复制", "COPY"), InputCommand("粘贴", "PASTE"),
        InputCommand("保存", "SAVE"), InputCommand("另存", "SAVE_AS")
    )

    val formulas: List<InputCommand?> = listOf(
        InputCommand("自动求和", "AUTO_SUM"), InputCommand("平均值", "FORMULA_AVERAGE"),
        InputCommand("最大值", "FORMULA_MAX"), InputCommand("最小值", "FORMULA_MIN"),
        InputCommand("四舍五入", "FORMULA_ROUND"), InputCommand("条件判断", "FORMULA_IF")
    )

    val allCommands: Set<String> = (keypad + navigation.filterNotNull() + editing.filterNotNull() + fileControls + formulas.filterNotNull())
        .mapTo(linkedSetOf()) { it.command }
}
