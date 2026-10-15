package br.com.pontopj.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado do ponto enviado pelo celular. Os totais "base" só contam registros já fechados. */
data class WatchState(
    val active: Boolean,
    val openStart: Long,
    val openRate: Double,
    val dayBaseMillis: Long,
    val dayBaseEarnings: Double,
    val monthBaseMillis: Long,
    val monthBaseEarnings: Double,
    val monthGoal: Double,
    val goalHours: Double,
    val hide: Boolean,
    val accent: Int,
    val updatedAt: Long,
    val balanceMillis: Long = 0L,
    val streak: Int = 0,
    val projection: Double = 0.0,
    /** Ganho dos últimos 7 dias, do mais antigo ao de hoje (o de hoje só com registros fechados). */
    val last7: List<Float> = List(7) { 0f },
    /** Períodos fechados de hoje: entrada, saída, entrada, saída... */
    val todaySpans: List<Long> = emptyList(),
    /** Horas de cada dia da semana (segunda a domingo), só registros fechados. */
    val weekMillis: List<Long> = List(7) { 0L },
    val weekToday: Int = 0,
    val remainingDays: Int = 0,
) {
    /** Horas de cada dia da semana; hoje soma o registro aberto. */
    fun weekLive(now: Long): List<Long> =
        weekMillis.mapIndexed { i, v -> if (i == weekToday) v + openMillis(now) else v }

    /** Horas de hoje, somando o registro aberto em tempo real. */
    fun dayMillis(now: Long): Long = dayBaseMillis + openMillis(now)

    fun dayEarnings(now: Long): Double = dayBaseEarnings + openEarnings(now)

    fun monthEarnings(now: Long): Double = monthBaseEarnings + openEarnings(now)

    fun openMillis(now: Long): Long = if (active) (now - openStart).coerceAtLeast(0L) else 0L

    private fun openEarnings(now: Long): Double = openMillis(now) / 3_600_000.0 * openRate

    /** Banco de horas do mês; com o ponto aberto, cresce junto com o relógio. */
    fun balance(now: Long): Long = balanceMillis + if (active) (now - updatedAt).coerceAtLeast(0L) else 0L

    /** Os 7 dias para o gráfico: o último soma o registro aberto em tempo real. */
    fun last7Live(now: Long): List<Float> =
        last7.mapIndexed { i, v -> if (i == last7.lastIndex) v + openEarnings(now).toFloat() else v }

    /** Fração (0 a 1) da meta diária já cumprida. */
    fun goalProgress(now: Long): Float {
        val goalMs = goalHours * 3_600_000.0
        return if (goalMs <= 0.0) 0f else (dayMillis(now) / goalMs).toFloat().coerceIn(0f, 1f)
    }
}

/** Guarda o último estado recebido (também no armazenamento, para abrir já com dados). */
object StateRepo {
    private const val PREFS = "watch_state"
    private val _state = MutableStateFlow<WatchState?>(null)
    val state: StateFlow<WatchState?> = _state

    fun load(context: Context) {
        if (_state.value != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(WearProtocol.K_UPDATED_AT)) return
        _state.value = WatchState(
            active = p.getBoolean(WearProtocol.K_ACTIVE, false),
            openStart = p.getLong(WearProtocol.K_OPEN_START, 0L),
            openRate = p.getString(WearProtocol.K_OPEN_RATE, "0")?.toDoubleOrNull() ?: 0.0,
            dayBaseMillis = p.getLong(WearProtocol.K_DAY_BASE_MILLIS, 0L),
            dayBaseEarnings = p.getString(WearProtocol.K_DAY_BASE_EARN, "0")?.toDoubleOrNull() ?: 0.0,
            monthBaseMillis = p.getLong(WearProtocol.K_MONTH_BASE_MILLIS, 0L),
            monthBaseEarnings = p.getString(WearProtocol.K_MONTH_BASE_EARN, "0")?.toDoubleOrNull() ?: 0.0,
            monthGoal = p.getString(WearProtocol.K_MONTH_GOAL, "0")?.toDoubleOrNull() ?: 0.0,
            goalHours = p.getString(WearProtocol.K_GOAL_HOURS, "8")?.toDoubleOrNull() ?: 8.0,
            hide = p.getBoolean(WearProtocol.K_HIDE, false),
            accent = p.getInt(WearProtocol.K_ACCENT, DEFAULT_ACCENT),
            updatedAt = p.getLong(WearProtocol.K_UPDATED_AT, 0L),
            balanceMillis = p.getLong(WearProtocol.K_BALANCE, 0L),
            streak = p.getInt(WearProtocol.K_STREAK, 0),
            projection = p.getString(WearProtocol.K_PROJECTION, "0")?.toDoubleOrNull() ?: 0.0,
            last7 = parseLast7(p.getString(WearProtocol.K_LAST7, null)),
            todaySpans = parseLongs(p.getString(WearProtocol.K_TODAY_SPANS, null)),
            weekMillis = parseLongs(p.getString(WearProtocol.K_WEEK_MILLIS, null)).let { if (it.size == 7) it else List(7) { 0L } },
            weekToday = p.getInt(WearProtocol.K_WEEK_TODAY, 0),
            remainingDays = p.getInt(WearProtocol.K_REMAIN_DAYS, 0),
        )
    }

    fun update(context: Context, map: DataMap) {
        val s = WatchState(
            active = map.getBoolean(WearProtocol.K_ACTIVE),
            openStart = map.getLong(WearProtocol.K_OPEN_START),
            openRate = map.getDouble(WearProtocol.K_OPEN_RATE),
            dayBaseMillis = map.getLong(WearProtocol.K_DAY_BASE_MILLIS),
            dayBaseEarnings = map.getDouble(WearProtocol.K_DAY_BASE_EARN),
            monthBaseMillis = map.getLong(WearProtocol.K_MONTH_BASE_MILLIS),
            monthBaseEarnings = map.getDouble(WearProtocol.K_MONTH_BASE_EARN),
            monthGoal = map.getDouble(WearProtocol.K_MONTH_GOAL),
            goalHours = map.getDouble(WearProtocol.K_GOAL_HOURS, 8.0),
            hide = map.getBoolean(WearProtocol.K_HIDE),
            accent = map.getInt(WearProtocol.K_ACCENT, DEFAULT_ACCENT),
            updatedAt = map.getLong(WearProtocol.K_UPDATED_AT),
            balanceMillis = map.getLong(WearProtocol.K_BALANCE),
            streak = map.getInt(WearProtocol.K_STREAK),
            projection = map.getDouble(WearProtocol.K_PROJECTION),
            last7 = (map.getFloatArray(WearProtocol.K_LAST7) ?: FloatArray(7)).toList(),
            todaySpans = (map.getLongArray(WearProtocol.K_TODAY_SPANS) ?: LongArray(0)).toList(),
            weekMillis = (map.getLongArray(WearProtocol.K_WEEK_MILLIS) ?: LongArray(7)).toList(),
            weekToday = map.getInt(WearProtocol.K_WEEK_TODAY),
            remainingDays = map.getInt(WearProtocol.K_REMAIN_DAYS),
        )
        // Ignora um estado mais velho que o já guardado (a ordem de chegada pode variar).
        val current = _state.value
        if (current != null && s.updatedAt < current.updatedAt) return
        apply(context, s)
    }

    /** Muda o estado na hora, antes de o celular confirmar (resposta imediata ao toque). */
    fun optimistic(context: Context, change: (WatchState) -> WatchState) {
        val current = _state.value ?: return
        apply(context, change(current))
    }

    private fun apply(context: Context, s: WatchState) {
        _state.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(WearProtocol.K_ACTIVE, s.active)
            .putLong(WearProtocol.K_OPEN_START, s.openStart)
            .putString(WearProtocol.K_OPEN_RATE, s.openRate.toString())
            .putLong(WearProtocol.K_DAY_BASE_MILLIS, s.dayBaseMillis)
            .putString(WearProtocol.K_DAY_BASE_EARN, s.dayBaseEarnings.toString())
            .putLong(WearProtocol.K_MONTH_BASE_MILLIS, s.monthBaseMillis)
            .putString(WearProtocol.K_MONTH_BASE_EARN, s.monthBaseEarnings.toString())
            .putString(WearProtocol.K_MONTH_GOAL, s.monthGoal.toString())
            .putString(WearProtocol.K_GOAL_HOURS, s.goalHours.toString())
            .putBoolean(WearProtocol.K_HIDE, s.hide)
            .putInt(WearProtocol.K_ACCENT, s.accent)
            .putLong(WearProtocol.K_UPDATED_AT, s.updatedAt)
            .putLong(WearProtocol.K_BALANCE, s.balanceMillis)
            .putInt(WearProtocol.K_STREAK, s.streak)
            .putString(WearProtocol.K_PROJECTION, s.projection.toString())
            .putString(WearProtocol.K_LAST7, s.last7.joinToString(","))
            .putString(WearProtocol.K_TODAY_SPANS, s.todaySpans.joinToString(","))
            .putString(WearProtocol.K_WEEK_MILLIS, s.weekMillis.joinToString(","))
            .putInt(WearProtocol.K_WEEK_TODAY, s.weekToday)
            .putInt(WearProtocol.K_REMAIN_DAYS, s.remainingDays)
            .apply()
    }

    private fun parseLongs(raw: String?): List<Long> =
        raw?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    private fun parseLast7(raw: String?): List<Float> {
        val parsed = raw?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
        return if (parsed.size == 7) parsed else List(7) { 0f }
    }

    const val DEFAULT_ACCENT = 0xFFF5BA1E.toInt()
}
