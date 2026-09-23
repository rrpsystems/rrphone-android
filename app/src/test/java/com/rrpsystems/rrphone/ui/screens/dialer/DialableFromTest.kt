package com.rrpsystems.rrphone.ui.screens.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class DialableFromTest {
    @Test
    fun cleansPastedNumbers() {
        assertEquals("1332191212", dialableFrom("(13) 3219-1212"))
        assertEquals("+551332191212", dialableFrom(" +55 13 3219 1212 "))
        assertEquals("2130", dialableFrom("Ramal 2130"))
        assertEquals("*21#", dialableFrom("*21#"))
        assertEquals("", dialableFrom("sem número aqui"))
        assertEquals("", dialableFrom("*#"))
    }
}
