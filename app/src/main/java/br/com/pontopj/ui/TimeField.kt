package br.com.pontopj.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Campo de hora (hh:mm). Guarda só os 4 dígitos ("0830") e mostra "08:30".
 * Só aceita horas de 00 a 23 e minutos de 00 a 59; ao completar os 4 dígitos chama [onComplete].
 */
@Composable
fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onComplete: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val next = sanitizeTimeDigits(raw)
            val completed = next.length == 4 && value.length < 4
            onValueChange(next)
            if (completed) onComplete()
        },
        label = { Text(label) },
        placeholder = { Text("00:00") },
        singleLine = true,
        visualTransformation = TimeTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        colors = fieldColors(),
        modifier = modifier,
    )
}

/** Mantém só dígitos válidos para uma hora. Um primeiro dígito de 3 a 9 vira "03".."09". */
fun sanitizeTimeDigits(raw: String): String {
    val out = StringBuilder()
    for (c in raw) {
        if (!c.isDigit()) continue
        when (out.length) {
            0 -> {
                if (c > '2') {
                    out.append('0')
                }
                out.append(c)
            }
            1 -> {
                if (!(out[0] == '2' && c > '3')) {
                    out.append(c)
                }
            }
            2 -> {
                if (c <= '5') {
                    out.append(c)
                }
            }
            3 -> out.append(c)
        }
        if (out.length >= 4) break
    }
    return out.toString()
}

private object TimeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { index, c ->
                if (index == 2) append(':')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int = if (offset <= 2) offset else offset - 1
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
