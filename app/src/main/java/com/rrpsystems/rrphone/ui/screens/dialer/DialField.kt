package com.rrpsystems.rrphone.ui.screens.dialer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Edição do número no visor do teclado, com cursor: os dígitos entram onde o
 * cursor está (ou substituem o trecho selecionado) e o apagar remove o
 * caractere antes dele — o mesmo que o campo do desktop faz. Funções puras,
 * para poderem ser testadas sem UI.
 */
object DialField {
    private fun allowed(c: Char) = c.isDigit() || c == '*' || c == '#' || c == '+'

    fun of(text: String) = TextFieldValue(text, TextRange(text.length))

    /** Insere [s] no cursor, substituindo a seleção se houver. */
    fun insert(value: TextFieldValue, s: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text.substring(0, start) + s + value.text.substring(end)
        return TextFieldValue(text, TextRange(start + s.length))
    }

    /** Apaga a seleção, ou o caractere antes do cursor. */
    fun backspace(value: TextFieldValue): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        if (start != end) {
            return TextFieldValue(value.text.substring(0, start) + value.text.substring(end), TextRange(start))
        }
        if (start == 0) return value
        return TextFieldValue(value.text.substring(0, start - 1) + value.text.substring(start), TextRange(start - 1))
    }

    /**
     * Filtra o que chega do campo (colar pela barra do Android, por exemplo):
     * fica só o que dá para discar, e o cursor é reposicionado contando apenas
     * os caracteres mantidos antes dele.
     */
    fun sanitize(value: TextFieldValue): TextFieldValue {
        if (value.text.all(::allowed)) return value
        val text = value.text.filter(::allowed)
        fun map(offset: Int) = value.text.take(offset).count(::allowed)
        return TextFieldValue(text, TextRange(map(value.selection.start), map(value.selection.end)))
    }
}
