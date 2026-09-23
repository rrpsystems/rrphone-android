package com.rrpsystems.rrphone.ui.screens.dialer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DialFieldTest {
    private fun at(text: String, cursor: Int) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun insertsAtCursorAndReplacesSelection() {
        assertEquals(at("21530", 3), DialField.insert(at("2130", 2), "5"))
        assertEquals(at("2930", 2), DialField.insert(TextFieldValue("2130", TextRange(1, 2)), "9"))
    }

    @Test
    fun backspaceDeletesBeforeCursorOrSelection() {
        assertEquals(at("130", 0), DialField.backspace(at("2130", 1)))
        assertEquals(at("2130", 0), DialField.backspace(at("2130", 0)))
        assertEquals(at("20", 1), DialField.backspace(TextFieldValue("2130", TextRange(1, 3))))
    }

    @Test
    fun sanitizeKeepsDialableAndRemapsCursor() {
        assertEquals(at("1332191212", 10), DialField.sanitize(at("(13) 3219-1212", 14)))
        assertEquals(at("1332", 2), DialField.sanitize(at("(13) 32", 3)))
    }
}
