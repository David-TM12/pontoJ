package br.com.pontopj.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ExtraStats(
    val streak: Int,
    val longestDayMillis: Long,
    val longestDayDate: LocalDate?,
    val overtimeMillis: Long,
    val daysOverGoal: Int,
    val allTimeEarnings: Double,
    val remainingBusinessDays: Int,
)

private fun isWeekend(d: LocalDate): Boolean =
    d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

fun List<WorkSession>.extraStats(
    month: YearMonth,
    now: Long,
    today: LocalDate,
    dailyGoalHours: Double,
): ExtraStats {
    val dates = map { dateOf(it.startMillis) }.toSet()

    // Sequência de dias úteis trabalhados (fim de semana sem registro não quebra a sequência).
    var cursor = today
    if (cursor !in dates) cursor = cursor.minusDays(1)
    var streak = 0
    while (true) {
        if (cursor in dates) {
            streak++
        } else if (!isWeekend(cursor)) {
            break
        }
        cursor = cursor.minusDays(1)
    }

    val goalMs = (dailyGoalHours * 3_600_000).toLong()
    val monthDays = inMonth(month).daySummaries(now)
    val longest = monthDays.maxByOrNull { it.millis }
    val overtime = monthDays.sumOf { (it.millis - goalMs).coerceAtLeast(0L) }
    val daysOver = monthDays.count { it.millis > goalMs }
    val remaining = if (month == YearMonth.from(today)) {
        businessDays(month) - businessDays(month, today)
    } else {
        0
    }

    return ExtraStats(
        streak = streak,
        longestDayMillis = longest?.millis ?: 0L,
        longestDayDate = longest?.date,
        overtimeMillis = overtime,
        daysOverGoal = daysOver,
        allTimeEarnings = totalEarnings(now),
        remainingBusinessDays = remaining.coerceAtLeast(0),
    )
}

/** Tempo trabalhado em cada período do dia: madrugada, manhã, tarde e noite (blocos de 6 h). */
fun List<WorkSession>.millisByPeriod(now: Long): LongArray {
    val out = LongArray(4)
    val zone = ZoneId.systemDefault()
    for (session in this) {
        var cursor = Instant.ofEpochMilli(session.startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(session.endMillis ?: now).atZone(zone)
        while (cursor.isBefore(end)) {
            val nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val segmentEnd = if (nextHour.isBefore(end)) nextHour else end
            out[cursor.hour / 6] += Duration.between(cursor, segmentEnd).toMillis()
            cursor = segmentEnd
        }
    }
    return out
}
