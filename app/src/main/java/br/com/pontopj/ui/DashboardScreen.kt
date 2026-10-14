package br.com.pontopj.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.brl
import br.com.pontopj.data.businessDays
import br.com.pontopj.data.compact
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.earningsUntilDay
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.inMonth
import br.com.pontopj.data.minutesToClock
import br.com.pontopj.data.monthName
import br.com.pontopj.data.monthStats
import br.com.pontopj.data.onDate
import br.com.pontopj.data.shortWeekday
import br.com.pontopj.data.totalEarnings
import br.com.pontopj.data.totalMillis
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val today = dateOf(now)
    val month = YearMonth.from(today)
    val previous = month.minusMonths(1)
    val stats = vm.sessions.monthStats(month, now, today)

    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val last7Values = last7.map { vm.sessions.onDate(it).totalEarnings(now) }
    val last7Labels = last7.map { shortWeekday(it.dayOfWeek) }

    val weekdays = DayOfWeek.values().toList()
    val monthSessions = vm.sessions.inMonth(month)
    val hoursByWeekday = weekdays.map { dow ->
        monthSessions.filter { dateOf(it.startMillis).dayOfWeek == dow }.totalMillis(now) / 3_600_000.0
    }

    val samePeriodBefore = vm.sessions.earningsUntilDay(previous, today.dayOfMonth, now)
    val goal = vm.monthlyGoal

    ScreenColumn {
        ScreenTitle("Dashboard", "Seus números de ${monthName(month)}")

        // ---- Ganhos do mês + meta ----
        AppCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ganhos no mês", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        brl(stats.earnings),
                        color = Palette.Gold,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TABULAR_NUMBERS,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (goal > 0) {
                            val missing = (goal - stats.earnings).coerceAtLeast(0.0)
                            if (missing > 0) "Faltam ${brl(missing)} para a meta de ${brl(goal)}" else "Meta de ${brl(goal)} batida 🎯"
                        } else {
                            "Defina uma meta mensal em Ajustes"
                        },
                        color = Palette.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (goal > 0) {
                    Spacer(Modifier.width(12.dp))
                    val progress = (stats.earnings / goal).toFloat()
                    ProgressRing(progress = progress, ringSize = 88.dp, stroke = 9.dp) {
                        Text(
                            "${(progress * 100).roundToInt().coerceAtMost(999)}%",
                            color = Palette.Text,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (samePeriodBefore > 0.0) {
                Spacer(Modifier.height(14.dp))
                val change = (stats.earnings / samePeriodBefore - 1.0) * 100
                val arrow = if (change >= 0) "▲" else "▼"
                Text(
                    "$arrow ${abs(change).roundToInt()}% em relação ao mesmo período de ${monthName(previous)}",
                    color = if (change >= 0) Palette.Gold else Palette.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ---- Últimos 7 dias ----
        AppCard {
            SectionTitle("Últimos 7 dias")
            Text("Ganhos por dia", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            BarChart(
                values = last7Values,
                labels = last7Labels,
                highlight = last7Values.lastIndex,
                valueText = { compact(it) },
            )
        }

        // ---- Indicadores ----
        TileRow {
            StatTile("Horas no mês", formatHm(stats.millis), Modifier.weight(1f))
            StatTile(
                "Dias trabalhados",
                "${stats.daysWorked}",
                Modifier.weight(1f),
                sub = "de ${businessDays(month, today)} dias úteis",
            )
        }
        TileRow {
            StatTile("Média por dia", formatHm(stats.avgMillisPerDay), Modifier.weight(1f))
            StatTile(
                "Ganho médio/dia",
                brl(stats.avgEarningsPerDay),
                Modifier.weight(1f),
                valueColor = Palette.Gold,
            )
        }
        TileRow {
            StatTile(
                "Melhor dia",
                stats.bestDay?.let { brl(it.earnings) } ?: "—",
                Modifier.weight(1f),
                sub = stats.bestDay?.date?.format(DateTimeFormatter.ofPattern("dd/MM")),
                valueColor = Palette.Gold,
            )
            StatTile(
                "Projeção do mês",
                brl(stats.projection),
                Modifier.weight(1f),
                sub = "no ritmo atual",
            )
        }
        TileRow {
            StatTile(
                "Entrada média",
                stats.avgStartMinute?.let { minutesToClock(it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile(
                "Saída média",
                stats.avgEndMinute?.let { minutesToClock(it) } ?: "—",
                Modifier.weight(1f),
            )
        }

        // ---- Horas por dia da semana ----
        AppCard {
            SectionTitle("Horas por dia da semana")
            Text(
                "Soma de horas em ${monthName(month)}",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            val busiest = hoursByWeekday.indices.maxByOrNull { hoursByWeekday[it] } ?: -1
            BarChart(
                values = hoursByWeekday,
                labels = weekdays.map { shortWeekday(it) },
                highlight = if (busiest >= 0 && hoursByWeekday[busiest] > 0) busiest else -1,
                chartHeight = 110.dp,
                valueText = { if (it > 0) String.format(java.util.Locale.US, "%.0fh", it) else "" },
            )
        }
    }
}
