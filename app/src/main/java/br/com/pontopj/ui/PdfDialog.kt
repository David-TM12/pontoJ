@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.pontopj.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.pontopj.data.PdfExporter
import br.com.pontopj.data.SavedFile
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brl
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.totalEarnings
import br.com.pontopj.data.totalMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val PRESETS = listOf("Mês exibido", "30 dias", "3 meses", "Este ano", "Personalizado")

private fun presetRange(index: Int, shownMonth: YearMonth, today: LocalDate): Pair<LocalDate, LocalDate> =
    when (index) {
        0 -> shownMonth.atDay(1) to shownMonth.atEndOfMonth()
        1 -> today.minusDays(29) to today
        2 -> today.minusMonths(3).plusDays(1) to today
        3 -> LocalDate.of(today.year, 1, 1) to today
        else -> today to today
    }

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Escolha do período e geração do PDF. */
@Composable
fun PdfDialog(
    sessions: List<WorkSession>,
    shownMonth: YearMonth,
    now: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = dateOf(now)
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    var preset by remember { mutableIntStateOf(0) }
    var customFrom by remember { mutableStateOf(shownMonth.atDay(1)) }
    var customTo by remember { mutableStateOf(shownMonth.atEndOfMonth()) }
    var showRangePicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<SavedFile?>(null) }

    val (from, to) = if (preset == 4) customFrom to customTo else presetRange(preset, shownMonth, today)
    val inRange = sessions.filter { dateOf(it.startMillis) in from..to }

    val result = saved
    if (result != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Palette.Surface,
            titleContentColor = Palette.Text,
            textContentColor = Palette.Muted,
            title = { Text("PDF pronto") },
            text = { Text("Salvo na pasta Downloads:\n${result.name}") },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, result.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Compartilhar PDF"))
                        },
                    ) { Text("Compartilhar", color = Palette.Gold) }
                    TextButton(
                        onClick = {
                            val view = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(result.uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(view)
                            } catch (e: ActivityNotFoundException) {
                                // sem leitor de PDF instalado
                            }
                        },
                    ) { Text("Abrir", color = Palette.Gold) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Palette.Muted) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Palette.Surface,
        titleContentColor = Palette.Text,
        textContentColor = Palette.Text,
        title = { Text("Relatório em PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Período", color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRESETS.forEachIndexed { index, label ->
                        RangeChip(
                            label = label,
                            selected = preset == index,
                            onClick = {
                                preset = index
                                failed = false
                            },
                        )
                    }
                }
                if (preset == 4) {
                    OutlinedButton(
                        onClick = { showRangePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Escolher datas no calendário", color = Palette.Gold) }
                }
                Column {
                    Text(
                        "${from.format(dateFormat)}  a  ${to.format(dateFormat)}",
                        color = Palette.Gold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (inRange.isEmpty()) {
                            "Nenhum registro neste período"
                        } else {
                            "${inRange.size} registros · ${formatHm(inRange.totalMillis(now))} · ${brl(inRange.totalEarnings(now))}"
                        },
                        color = Palette.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Gold)
                        Spacer(Modifier.width(10.dp))
                        Text("Gerando PDF…", color = Palette.Muted)
                    }
                }
                if (failed) {
                    Text("Não foi possível salvar o PDF.", color = Palette.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    failed = false
                    val snapshot = sessions
                    scope.launch {
                        val file = withContext(Dispatchers.Default) {
                            runCatching { PdfExporter.generate(context, snapshot, from, to, now) }.getOrNull()
                        }
                        busy = false
                        if (file != null) saved = file else failed = true
                    }
                },
            ) { Text("Gerar PDF", color = Palette.Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar", color = Palette.Muted) }
        },
    )

    if (showRangePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customFrom.toUtcMillis(),
            initialSelectedEndDateMillis = customTo.toUtcMillis(),
        )
        val start = state.selectedStartDateMillis
        val end = state.selectedEndDateMillis
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = start != null && end != null,
                    onClick = {
                        if (start != null && end != null) {
                            customFrom = start.toUtcDate()
                            customTo = end.toUtcDate()
                        }
                        showRangePicker = false
                    },
                ) { Text("OK", color = Palette.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancelar", color = Palette.Muted) }
            },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp))
        }
    }
}
