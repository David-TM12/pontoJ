package br.com.pontopj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.formatMoneyInput
import br.com.pontopj.data.parseMoney
import br.com.pontopj.data.timeOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Criar um lançamento manual ou editar/excluir um registro existente. */
@Composable
fun SessionDialog(
    initial: WorkSession?,
    defaultRate: Double,
    onDismiss: () -> Unit,
    onSave: (WorkSession) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/uuuu") }
    val timeFormat = remember { DateTimeFormatter.ofPattern("H:mm") }
    val zone = remember { ZoneId.systemDefault() }

    var date by remember {
        mutableStateOf(dateOf(initial?.startMillis ?: System.currentTimeMillis()).format(dateFormat))
    }
    var start by remember { mutableStateOf(initial?.let { timeOf(it.startMillis) } ?: "") }
    var end by remember { mutableStateOf(initial?.endMillis?.let { timeOf(it) } ?: "") }
    var rate by remember { mutableStateOf(formatMoneyInput(initial?.rate ?: defaultRate)) }
    var error by remember { mutableStateOf<String?>(null) }

    // Um registro em andamento pode ficar sem saída; os demais precisam dela.
    val endRequired = initial?.isActive != true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Text,
        title = { Text(if (initial == null) "Novo lançamento" else "Editar registro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data (dd/mm/aaaa)") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Entrada") },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text(if (endRequired) "Saída" else "Saída (opcional)") },
                        placeholder = { Text("17:00") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Valor da hora (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                error?.let { Text(it, color = Palette.Danger) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = runCatching { LocalDate.parse(date.trim(), dateFormat) }.getOrNull()
                    val st = runCatching { LocalTime.parse(start.trim(), timeFormat) }.getOrNull()
                    val en = if (end.isBlank()) null else runCatching { LocalTime.parse(end.trim(), timeFormat) }.getOrNull()
                    val r = parseMoney(rate)
                    val message: String? = when {
                        d == null -> "Data inválida (use dd/mm/aaaa)"
                        st == null -> "Entrada inválida (use hh:mm)"
                        end.isNotBlank() && en == null -> "Saída inválida (use hh:mm)"
                        endRequired && en == null -> "Informe o horário de saída"
                        en != null && !en.isAfter(st) -> "A saída deve ser depois da entrada"
                        r == null -> "Valor da hora inválido"
                        else -> null
                    }
                    if (message != null) {
                        error = message
                    } else if (d != null && st != null && r != null) {
                        val startMs = LocalDateTime.of(d, st).atZone(zone).toInstant().toEpochMilli()
                        val endMs = en?.let { LocalDateTime.of(d, it).atZone(zone).toInstant().toEpochMilli() }
                        val base = initial ?: WorkSession(
                            id = System.currentTimeMillis(),
                            startMillis = startMs,
                            rate = r,
                            manual = true,
                        )
                        onSave(base.copy(startMillis = startMs, endMillis = endMs, rate = r))
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
}
