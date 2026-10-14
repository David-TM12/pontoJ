package br.com.pontopj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.brl
import br.com.pontopj.data.businessDays
import br.com.pontopj.data.compact
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.dayLabel
import br.com.pontopj.data.daySummaries
import br.com.pontopj.data.earningsUntilDay
import br.com.pontopj.data.extraStats
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.inMonth
import br.com.pontopj.data.millisByPeriod
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: AppViewModel, onSeeAll: () -> Unit = {}) {
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
    val extra = vm.sessions.extraStats(month, now, today, vm.dailyGoalHours)
    val goal = vm.monthlyGoal
    val monthSessions = vm.sessions.inMonth(month)
    val monthDays = monthSessions.daySummaries(now)

    // ---- Gráfico de ganhos por dia (filtro + seleção) ----
    var rangeDays by rememberSaveable { mutableIntStateOf(7) }
    var pickedIndex by remember(rangeDays) { mutableIntStateOf(-1) }
    val days = (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val dayValues = days.map { vm.sessions.onDate(it).totalEarnings(now) }
    val dayMillis = days.map { vm.sessions.onDate(it).totalMillis(now) }
    val selectedIndex = if (pickedIndex in days.indices) pickedIndex else days.lastIndex

    val half = dayValues.size / 2
    val firstAverage = dayValues.take(half).average()
    val secondAverage = dayValues.drop(half).average()
    val trendPercent: Double? =
        if (!firstAverage.isNaN() && firstAverage > 0.0) (secondAverage / firstAverage - 1.0) * 100 else null

    val samePeriodBefore = vm.sessions.earningsUntilDay(previous, today.dayOfMonth, now)

    // ---- Dados dos demais gráficos ----
    val lastDayShown = today.dayOfMonth
    var running = 0.0
    val cumulative = (1..lastDayShown).map { d ->
        running += vm.sessions.onDate(month.atDay(d)).totalEarnings(now)
        running
    }

    val periodHours = monthSessions.millisByPeriod(now).map { it / 3_600_000.0 }
    val periodLabels = listOf("Madrugada (0–6h)", "Manhã (6–12h)", "Tarde (12–18h)", "Noite (18–24h)")
    val periodColors = listOf(Color(0xFF5C4A12), Palette.Gold, Color(0xFFF2D27A), Palette.GoldDeep)

    val millisByDay = monthDays.associate { it.date to it.millis }
    val earningsByDay = monthDays.associate { it.date to it.earnings }

    val weekdays = DayOfWeek.values().toList()
    val hoursByWeekday = weekdays.map { dow ->
        monthSessions.filter { dateOf(it.startMillis).dayOfWeek == dow }.totalMillis(now) / 3_600_000.0
    }

    val weekBuckets = (0 until (month.lengthOfMonth() + 6) / 7).map { w ->
        val from = w * 7 + 1
        val to = minOf(from + 6, month.lengthOfMonth())
        val sum = monthSessions
            .filter { dateOf(it.startMillis).dayOfMonth in from..to }
            .totalEarnings(now)
        "Semana ${w + 1} (dias $from–$to)" to sum
    }

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
                            if (missing > 0) {
                                "Faltam ${brl(missing)} para a meta de ${brl(goal)}"
                            } else {
                                "Meta de ${brl(goal)} batida 🎯"
                            }
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

        // ---- Extrato diário recente ----
        DailyStatementCard(days = vm.sessions.daySummaries(now), onSeeAll = onSeeAll)

        // ---- Ganhos por dia: filtro, barras clicáveis e linha de tendência ----
        AppCard {
            val rangeOptions = listOf(7 to "7 dias", 14 to "14 dias", 21 to "21 dias", 30 to "1 mês")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionTitle("Ganhos por dia")
                FilterDropdown(
                    options = rangeOptions.map { it.second },
                    selectedIndex = rangeOptions.indexOfFirst { it.first == rangeDays }.coerceAtLeast(0),
                    onSelect = { rangeDays = rangeOptions[it].first },
                )
            }
            Text(
                "Toque em uma barra para destacar o dia",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            val workedValues = dayValues.filter { it > 0.0 }
            DayPanel(
                date = days[selectedIndex],
                earnings = dayValues[selectedIndex],
                millis = dayMillis[selectedIndex],
                sessions = vm.sessions.onDate(days[selectedIndex]),
                average = if (workedValues.isEmpty()) 0.0 else workedValues.average(),
                now = now,
            )
            Spacer(Modifier.height(14.dp))
            EarningsChart(
                values = dayValues,
                dates = days,
                selected = selectedIndex,
                onSelect = { pickedIndex = it },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(Palette.Gold, "Ganho do dia")
                LegendDot(Color(0xFFFFE9A8), "Média móvel (3 dias)", line = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "No período: ${brl(dayValues.sum())}  ·  média de ${brl(dayValues.average())}/dia",
                color = Palette.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (trendPercent != null) {
                val up = trendPercent >= 0
                Text(
                    "${if (up) "▲" else "▼"} Tendência: ${abs(trendPercent).roundToInt()}% ${if (up) "acima" else "abaixo"} da primeira metade do período",
                    color = if (up) Palette.Gold else Palette.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
        TileRow {
            StatTile(
                "Sequência",
                "${extra.streak} ${if (extra.streak == 1) "dia" else "dias"}",
                Modifier.weight(1f),
                sub = "dias úteis seguidos",
            )
            StatTile(
                "Dias acima da meta",
                "${extra.daysOverGoal}",
                Modifier.weight(1f),
                sub = "de ${stats.daysWorked} trabalhados",
            )
        }
        TileRow {
            StatTile(
                "Horas extras",
                formatHm(extra.overtimeMillis),
                Modifier.weight(1f),
                sub = "acima da meta diária",
            )
            StatTile(
                "Maior jornada",
                formatHm(extra.longestDayMillis),
                Modifier.weight(1f),
                sub = extra.longestDayDate?.format(DateTimeFormatter.ofPattern("dd/MM")),
            )
        }
        TileRow {
            val missing = (goal - stats.earnings).coerceAtLeast(0.0)
            StatTile(
                "Ritmo para a meta",
                when {
                    goal <= 0.0 -> "—"
                    missing <= 0.0 -> "Batida 🎯"
                    extra.remainingBusinessDays > 0 -> brl(missing / extra.remainingBusinessDays)
                    else -> brl(missing)
                },
                Modifier.weight(1f),
                sub = when {
                    goal <= 0.0 -> "defina uma meta"
                    missing <= 0.0 -> null
                    extra.remainingBusinessDays > 0 -> "por dia útil (${extra.remainingBusinessDays} restantes)"
                    else -> "ainda faltando"
                },
                valueColor = Palette.Gold,
            )
            StatTile(
                "Acumulado total",
                brl(extra.allTimeEarnings),
                Modifier.weight(1f),
                sub = "desde o primeiro registro",
            )
        }

        TileRow {
            val workedValues = dayValues.filter { it > 0.0 }
            val elapsedBusiness = businessDays(month, today)
            StatTile(
                "Média diária",
                if (workedValues.isEmpty()) "—" else brl(workedValues.average()),
                Modifier.weight(1f),
                sub = "base ${dayValues.size} dias",
                valueColor = Palette.Gold,
            )
            StatTile(
                "Consistência",
                if (elapsedBusiness > 0) "${(minOf(stats.daysWorked, elapsedBusiness) * 100.0 / elapsedBusiness).roundToInt()}%" else "—",
                Modifier.weight(1f),
                sub = "dos dias úteis com registro",
            )
        }

        // ---- Evolução acumulada ----
        AppCard {
            SectionTitle("Evolução do mês")
            Text(
                "Quanto você já acumulou, a meta e a projeção",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            CumulativeChart(
                points = cumulative,
                daysInMonth = month.lengthOfMonth(),
                goal = goal,
                projection = stats.projection,
            )
        }

        // ---- Período do dia ----
        AppCard {
            SectionTitle("Quando você trabalha")
            Text(
                "Horas por período do dia em ${monthName(month)}",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            DonutChart(
                values = periodHours,
                labels = periodLabels,
                colors = periodColors,
                centerTop = String.format(Locale.US, "%.0fh", periodHours.sum()),
                centerBottom = "no mês",
                valueText = { String.format(Locale.US, "%.1fh", it) },
            )
        }

        // ---- Calendário de atividade ----
        AppCard {
            SectionTitle("Calendário de atividade")
            Text(
                "Quanto mais dourado, mais horas no dia",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            CalendarHeatmap(
                month = month,
                millisByDay = millisByDay,
                earningsByDay = earningsByDay,
                today = today,
            )
        }

        // ---- Ganhos por semana ----
        AppCard {
            SectionTitle("Ganhos por semana")
            Text(
                "Blocos de 7 dias dentro do mês",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            HorizontalBars(items = weekBuckets, valueText = { brl(it) })
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
                valueText = { if (it > 0) String.format(Locale.US, "%.0fh", it) else "" },
            )
        }
    }
}
