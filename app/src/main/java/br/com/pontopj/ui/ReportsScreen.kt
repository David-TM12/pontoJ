package br.com.pontopj.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.DaySummary
import br.com.pontopj.data.MonthStats
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brl
import br.com.pontopj.data.compact
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.dayLabel
import br.com.pontopj.data.daySummaries
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.inMonth
import br.com.pontopj.data.monthLabel
import br.com.pontopj.data.monthStats
import br.com.pontopj.data.shortMonth
import br.com.pontopj.data.timeOf
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(vm: AppViewModel) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val context = LocalContext.current
    val today = dateOf(now)
    var monthsBack by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(today).minusMonths(monthsBack.toLong())

    val stats = vm.sessions.monthStats(month, now, today)
    val days = vm.sessions.inMonth(month).daySummaries(now)

    val chartMonths = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val chartValues = chartMonths.map { ym -> vm.sessions.monthStats(ym, now, today).earnings }

    var showManual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkSession?>(null) }

    ScreenColumn {
        ScreenTitle("Relatórios", "Quanto você ganhou em cada mês")

        // ---- Seletor de mês ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "‹",
                color = Palette.Gold,
                fontSize = 34.sp,
                modifier = Modifier
                    .clickable { if (monthsBack < 60) monthsBack++ }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                monthLabel(month),
                color = Palette.Text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "›",
                color = if (monthsBack > 0) Palette.Gold else Palette.Outline,
                fontSize = 34.sp,
                modifier = Modifier
                    .clickable(enabled = monthsBack > 0) { monthsBack-- }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ---- Total do mês ----
        AppCard(highlight = true) {
            Text("Total ganho em ${monthLabel(month)}", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
            Text(
                brl(stats.earnings),
                color = Palette.Gold,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_NUMBERS,
                ),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniStat("Horas", formatHm(stats.millis))
                MiniStat("Dias", "${stats.daysWorked}")
                MiniStat("Registros", "${stats.sessionsCount}")
            }
        }

        // ---- Comparativo dos últimos 6 meses ----
        AppCard {
            SectionTitle("Últimos 6 meses")
            Text("Ganhos mensais", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            BarChart(
                values = chartValues,
                labels = chartMonths.map { shortMonth(it) },
                highlight = chartValues.lastIndex,
                valueText = { compact(it) },
            )
        }

        // ---- Ações ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val text = buildReport(month, stats, days, now)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Ganhos – ${monthLabel(month)}")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartilhar relatório"))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.Black),
            ) { Text("Compartilhar", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { showManual = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) { Text("+ Lançamento", color = Palette.Gold) }
        }

        // ---- Detalhe por dia ----
        SectionTitle("Dia a dia")
        if (days.isEmpty()) {
            AppCard {
                Text(
                    "Sem registros em ${monthLabel(month)}.",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            days.forEach { day ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dayLabel(day.date), color = Palette.Text, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatHm(day.millis),
                                color = Palette.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            brl(day.earnings),
                            color = Palette.Gold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    day.sessions.forEach { s -> SessionRow(s, now) { editing = s } }
                }
            }
        }
    }

    if (showManual) {
        SessionDialog(
            initial = null,
            defaultRate = vm.hourlyRate,
            onDismiss = { showManual = false },
            onSave = {
                vm.saveSession(it)
                showManual = false
            },
            onDelete = null,
        )
    }
    editing?.let { s ->
        SessionDialog(
            initial = s,
            defaultRate = vm.hourlyRate,
            onDismiss = { editing = null },
            onSave = {
                vm.saveSession(it)
                editing = null
            },
            onDelete = {
                vm.deleteSession(s.id)
                editing = null
            },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            color = Palette.Text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_NUMBERS),
        )
    }
}

private fun buildReport(month: YearMonth, stats: MonthStats, days: List<DaySummary>, now: Long): String {
    val dayFormat = DateTimeFormatter.ofPattern("dd/MM")
    return buildString {
        appendLine("Relatório de ganhos – ${monthLabel(month)}")
        appendLine("Total: ${brl(stats.earnings)}")
        appendLine("Horas trabalhadas: ${formatHm(stats.millis)}")
        appendLine("Dias trabalhados: ${stats.daysWorked}")
        appendLine()
        days.sortedBy { it.date }.forEach { d ->
            val periods = d.sessions.joinToString(" | ") { s ->
                val end = s.endMillis?.let { timeOf(it) } ?: "em andamento"
                "${timeOf(s.startMillis)}–$end"
            }
            appendLine("${d.date.format(dayFormat)}: $periods → ${formatHm(d.millis)} • ${brl(d.earnings)}")
        }
    }
}
