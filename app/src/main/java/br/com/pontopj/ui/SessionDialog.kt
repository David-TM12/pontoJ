@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.pontopj.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brl
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.timeOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun digitsOf(text: String): String = text.filter { it.isDigit() }

private fun timeFromDigits(digits: String): LocalTime? =
    if (digits.length == 4) {
        runCatching { LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt()) }.getOrNull()
    } else {
        null
    }

/** Criar um lançamento manual ou editar/excluir um registro existente. */
@Composable
fun SessionDialog(
    initial: WorkSession?,
    defaultRate: Double,
    onDismiss: () -> Unit,
    onSave: (WorkSession) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = remember { ZoneId.systemDefault() }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val focus = LocalFocusManager.current

    var date by remember { mutableStateOf<LocalDate>(dateOf(initial?.startMillis ?: System.currentTimeMillis())) }
    var start by remember { mutableStateOf(initial?.let { digitsOf(timeOf(it.startMillis)) } ?: "") }
    var end by remember { mutableStateOf(initial?.endMillis?.let { digitsOf(timeOf(it)) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    // O valor da hora vem dos Ajustes (ou do próprio registro, ao editar).
    val rate = initial?.rate ?: defaultRate
    // Um registro em andamento pode ficar sem saída; os demais precisam dela.
    val endRequired = initial?.isActive != true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Text,
        title = { Text(if (initial == null) "Novo lançamento" else "Editar registro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedTextField(
                        value = date.format(dateFormat),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data") },
                        trailingIcon = { Icon(Icons.Rounded.DateRange, contentDescription = null, tint = Palette.Gold) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Camada transparente: toque em qualquer parte do campo abre o calendário.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { showPicker = true },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        value = start,
                        onValueChange = { start = it },
                        label = "Entrada",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Next,
                        onComplete = { focus.moveFocus(FocusDirection.Next) },
                    )
                    TimeField(
                        value = end,
                        onValueChange = { end = it },
                        label = if (endRequired) "Saída" else "Saída (opcional)",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Done,
                        onComplete = { focus.clearFocus() },
                    )
                }
                Text(
                    if (rate > 0.0) {
                        "Valor da hora: ${brl(rate)}" + if (initial == null) " (definido em Ajustes)" else ""
                    } else {
                        "Defina o valor da hora em Ajustes para calcular os ganhos."
                    },
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val st = timeFromDigits(start)
                    val en = if (end.isEmpty()) null else timeFromDigits(end)
                    val message: String? = when {
                        st == null -> "Informe a entrada (hh:mm)"
                        end.isNotEmpty() && en == null -> "Saída incompleta (hh:mm)"
                        endRequired && en == null -> "Informe o horário de saída"
                        en != null && !en.isAfter(st) -> "A saída deve ser depois da entrada"
                        else -> null
                    }
                    if (message != null) {
                        error = message
                    } else if (st != null) {
                        val startMs = LocalDateTime.of(date, st).atZone(zone).toInstant().toEpochMilli()
                        val endMs = en?.let { LocalDateTime.of(date, it).atZone(zone).toInstant().toEpochMilli() }
                        val base = initial ?: WorkSession(
                            id = System.currentTimeMillis(),
                            startMillis = startMs,
                            rate = rate,
                            manual = true,
                        )
                        onSave(base.copy(startMillis = startMs, endMillis = endMs, rate = rate))
                    }
                },
            ) { Text("Salvar", color = Palette.Gold) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Excluir", color = Palette.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.Muted) }
            }
        },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showPicker = false
                    },
                ) { Text("OK", color = Palette.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = Palette.Muted) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
