package br.com.pontopj.data

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val PtBR: Locale = Locale.forLanguageTag("pt-BR")

private fun zone(): ZoneId = ZoneId.systemDefault()

// ---------- Formatação ----------

fun brl(value: Double): String = NumberFormat.getCurrencyInstance(PtBR).format(value)

/** 1.2k, 850 ... para rótulos curtos em gráficos. */
fun compact(value: Double): String = when {
    value <= 0.0 -> ""
    value >= 1000 -> String.format(PtBR, "%.1fk", value / 1000)
    else -> String.format(PtBR, "%.0f", value)
}

fun formatHm(millis: Long): String {
    val minutes = millis / 60_000
    return String.format(PtBR, "%dh %02dmin", minutes / 60, minutes % 60)
}

fun formatClock(millis: Long): String {
    val s = millis / 1000
    return String.format(PtBR, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun minutesToClock(minutes: Int): String = String.format(PtBR, "%02d:%02d", minutes / 60, minutes % 60)

fun agoLabel(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}

fun formatMoneyInput(value: Double): String =
    if (value == 0.0) "" else String.format(PtBR, "%.2f", value)

/** Aceita "85", "85,5", "1.234,50", "R$ 85" e devolve null se não der para entender. */
fun parseMoney(text: String): Double? {
    var s = text.trim().replace("R$", "").replace(" ", "")
    if (s.isEmpty()) return null
    s = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    return s.toDoubleOrNull()?.takeIf { it >= 0 }
}

private fun String.cap(): String = replaceFirstChar { it.uppercase() }

fun monthLabel(ym: YearMonth): String =
    ym.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PtBR)).cap()

fun monthName(ym: YearMonth): String = ym.month.getDisplayName(TextStyle.FULL, PtBR).cap()

fun shortMonth(ym: YearMonth): String = ym.month.getDisplayName(TextStyle.SHORT, PtBR).trimEnd('.').cap()

fun shortWeekday(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, PtBR).trimEnd('.').cap()

fun dayLabel(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", PtBR)).cap()

fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

fun timeOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(zone()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun minuteOfDay(millis: Long): Int {
    val t = Instant.ofEpochMilli(millis).atZone(zone())
    return t.hour * 60 + t.minute
}

fun greeting(millis: Long): String {
    val hour = Instant.ofEpochMilli(millis).atZone(zone()).hour
    return when {
        hour < 12 -> "Bom dia"
        hour < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}

// ---------- Agregações ----------

data class DaySummary(
    val date: LocalDate,
    val sessions: List<WorkSession>,
    val millis: Long,
    val earnings: Double,
)

data class MonthStats(
    val month: YearMonth,
    val millis: Long,
    val earnings: Double,
    val daysWorked: Int,
    val sessionsCount: Int,
    val avgMillisPerDay: Long,
    val avgEarningsPerDay: Double,
    val bestDay: DaySummary?,
    val avgStartMinute: Int?,
    val avgEndMinute: Int?,
    val projection: Double,
)

fun List<WorkSession>.onDate(date: LocalDate): List<WorkSession> =
    filter { dateOf(it.startMillis) == date }

fun List<WorkSession>.inMonth(month: YearMonth): List<WorkSession> =
    filter { YearMonth.from(dateOf(it.startMillis)) == month }

fun List<WorkSession>.totalMillis(now: Long): Long = sumOf { it.durationMillis(now) }

fun List<WorkSession>.totalEarnings(now: Long): Double = sumOf { it.earnings(now) }

fun List<WorkSession>.daySummaries(now: Long): List<DaySummary> =
    groupBy { dateOf(it.startMillis) }
        .map { (date, list) ->
            DaySummary(date, list.sortedBy { it.startMillis }, list.totalMillis(now), list.totalEarnings(now))
        }
        .sortedByDescending { it.date }

fun List<WorkSession>.earningsUntilDay(month: YearMonth, day: Int, now: Long): Double =
    inMonth(month).filter { dateOf(it.startMillis).dayOfMonth <= day }.totalEarnings(now)

fun businessDays(month: YearMonth, upTo: LocalDate? = null): Int {
    var count = 0
    for (day in 1..month.lengthOfMonth()) {
        val d = month.atDay(day)
        if (upTo != null && d.isAfter(upTo)) break
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) count++
    }
    return count
}

fun List<WorkSession>.monthStats(month: YearMonth, now: Long, today: LocalDate): MonthStats {
    val monthSessions = inMonth(month)
    val days = monthSessions.daySummaries(now)
    val millis = monthSessions.totalMillis(now)
    val earnings = monthSessions.totalEarnings(now)
    val byDay = monthSessions.groupBy { dateOf(it.startMillis) }.values

    val starts = byDay.map { list -> list.minOf { minuteOfDay(it.startMillis) } }
    val ends = byDay.mapNotNull { list ->
        list.mapNotNull { it.endMillis }.maxOrNull()?.let { minuteOfDay(it) }
    }

    val worked = days.size
    val projection = if (month == YearMonth.from(today)) {
        val elapsed = maxOf(businessDays(month, today), worked, 1)
        earnings / elapsed * businessDays(month).coerceAtLeast(elapsed)
    } else {
        earnings
    }

    return MonthStats(
        month = month,
        millis = millis,
        earnings = earnings,
        daysWorked = worked,
        sessionsCount = monthSessions.size,
        avgMillisPerDay = if (worked > 0) millis / worked else 0L,
        avgEarningsPerDay = if (worked > 0) earnings / worked else 0.0,
        bestDay = days.maxByOrNull { it.earnings },
        avgStartMinute = if (starts.isNotEmpty()) starts.average().toInt() else null,
        avgEndMinute = if (ends.isNotEmpty()) ends.average().toInt() else null,
        projection = projection,
    )
}
