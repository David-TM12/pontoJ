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
) {
    /** Horas de hoje, somando o registro aberto em tempo real. */
    fun dayMillis(now: Long): Long = dayBaseMillis + openMillis(now)

    fun dayEarnings(now: Long): Double = dayBaseEarnings + openEarnings(now)

    fun monthEarnings(now: Long): Double = monthBaseEarnings + openEarnings(now)

    private fun openMillis(now: Long): Long = if (active) (now - openStart).coerceAtLeast(0L) else 0L

    private fun openEarnings(now: Long): Double = openMillis(now) / 3_600_000.0 * openRate

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
            .apply()
    }

    const val DEFAULT_ACCENT = 0xFFF5BA1E.toInt()
}
