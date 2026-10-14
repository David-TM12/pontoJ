package br.com.pontopj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.ColumnMapping
import br.com.pontopj.data.ImportedSheet
import br.com.pontopj.data.autoMap
import br.com.pontopj.data.brl
import br.com.pontopj.data.buildTable
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.parseImport
import br.com.pontopj.data.timeOf
import java.time.format.DateTimeFormatter

/** Tela de importação: escolher a aba, conferir as colunas, ver uma prévia e confirmar. */
@Composable
fun ImportDialog(
    vm: AppViewModel,
    sheet: ImportedSheet,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var sheetIndex by remember { mutableIntStateOf(0) }
    val table = remember(sheetIndex) { buildTable(sheet.loadSheet(sheetIndex)) }
    var mapping by remember(table) { mutableStateOf(autoMap(table.headers)) }
    val result = remember(table, mapping, vm.hourlyRate) { parseImport(table, mapping, vm.hourlyRate) }
    val previewFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Palette.Black) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ScreenTitle("Importar horas", sheet.fileName)

                    if (sheet.sheetNames.size > 1) {
                        AppCard {
                            SectionTitle("Planilha")
                            Spacer(Modifier.height(8.dp))
                            ColumnPicker(
                                label = "Aba",
                                options = sheet.sheetNames,
                                selected = sheetIndex,
                                allowNone = false,
                                onSelect = { sheetIndex = it },
                            )
                        }
                    }

                    AppCard {
                        SectionTitle("Colunas")
                        Text(
                            "Confira se cada informação está ligada à coluna certa.",
                            color = Palette.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        ColumnPicker("Data *", table.headers, mapping.date) { mapping = mapping.copy(date = it) }
                        ColumnPicker("Entrada", table.headers, mapping.start) { mapping = mapping.copy(start = it) }
                        ColumnPicker("Saída", table.headers, mapping.end) { mapping = mapping.copy(end = it) }
                        ColumnPicker("Entrada 2", table.headers, mapping.start2) { mapping = mapping.copy(start2 = it) }
                        ColumnPicker("Saída 2", table.headers, mapping.end2) { mapping = mapping.copy(end2 = it) }
                        ColumnPicker("Horas totais", table.headers, mapping.hours) { mapping = mapping.copy(hours = it) }
                        ColumnPicker("Valor da hora", table.headers, mapping.rate) { mapping = mapping.copy(rate = it) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Entrada 2 / Saída 2 servem para quem para no almoço. “Horas totais” só é usada " +
                                "quando a linha não tem entrada e saída (o registro começa às 08:00). " +
                                "Sem coluna de valor, usa o valor da hora dos Ajustes (${brl(vm.hourlyRate)}).",
                            color = Palette.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    AppCard(highlight = true) {
                        SectionTitle("Prévia")
                        Spacer(Modifier.height(6.dp))
                        if (mapping.date < 0) {
                            Text(
                                "Escolha a coluna da data.",
                                color = Palette.Danger,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "${result.sessions.size} registros prontos para importar",
                                color = Palette.Gold,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${result.skipped} de ${result.total} linhas ignoradas (sem data ou sem horário válido)",
                                color = Palette.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (vm.hourlyRate <= 0.0 && mapping.rate < 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "O valor da hora nos Ajustes está zerado: os ganhos ficarão em R$ 0,00.",
                                    color = Palette.Danger,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            result.sessions.sortedBy { it.startMillis }.take(6).forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${dateOf(s.startMillis).format(previewFormat)}  ${timeOf(s.startMillis)} → ${s.endMillis?.let { timeOf(it) } ?: "—"}",
                                        color = Palette.Text,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
                                    )
                                    Text(
                                        formatHm(s.durationMillis(0L)),
                                        color = Palette.Muted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (result.sessions.size > 6) {
                                Text(
                                    "… e mais ${result.sessions.size - 6}",
                                    color = Palette.Muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = Palette.Muted)
                    }
                    Button(
                        onClick = {
                            val (added, duplicated) = vm.importSessions(result.sessions)
                            val message = buildString {
                                append("$added registros importados")
                                if (duplicated > 0) append(" ($duplicated já existiam e foram ignorados)")
                            }
                            onDone(message)
                        },
                        enabled = result.sessions.isNotEmpty(),
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Palette.Gold,
                            contentColor = Palette.Black,
                        ),
                    ) { Text("Importar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ColumnPicker(
    label: String,
    options: List<String>,
    selected: Int,
    allowNone: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSelection = selected in options.indices
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Text, modifier = Modifier.weight(0.42f))
        Box(Modifier.weight(0.58f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (hasSelection) options[selected] else "— nenhuma —",
                    color = if (hasSelection) Palette.Gold else Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowNone) {
                    DropdownMenuItem(
                        text = { Text("— nenhuma —") },
                        onClick = {
                            onSelect(-1)
                            expanded = false
                        },
                    )
                }
                options.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
