package com.github.sukieva.sqlanalyzer.util

import com.intellij.openapi.editor.Editor

object SqlSelectionExtractor {
    fun extract(editor: Editor): String {
        val selection = editor.selectionModel.selectedText?.trim()
        if (!selection.isNullOrBlank()) {
            return selection
        }

        val text = editor.document.text
        val caretOffset = editor.caretModel.offset.coerceIn(0, text.length)
        return extractStatement(text, caretOffset)
    }

    fun extractStatement(text: String, caretOffset: Int): String {
        if (text.isBlank()) return ""
        val safeCaret = caretOffset.coerceIn(0, text.length)
        val start = text.lastIndexOf(';', startIndex = (safeCaret - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val end = text.indexOf(';', startIndex = safeCaret).let { if (it == -1) text.length else it }
        return text.substring(start, end).trim()
    }
}
