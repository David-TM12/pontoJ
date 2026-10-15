package br.com.pontopj.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Armazenamento local simples (SharedPreferences + JSON). Os dados ficam só no aparelho. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("ponto_pj", Context.MODE_PRIVATE)

    fun loadSessions(): List<WorkSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toSession() }
        }.getOrDefault(emptyList())
    }

    fun saveSessions(list: List<WorkSession>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    var hourlyRate: Double
        get() = prefs.getString(KEY_RATE, null)?.toDoubleOrNull() ?: 0.0
        set(value) {
            prefs.edit().putString(KEY_RATE, value.toString()).apply()
        }

    var monthlyGoal: Double
        get() = prefs.getString(KEY_MONTH_GOAL, null)?.toDoubleOrNull() ?: 0.0
        set(value) {
            prefs.edit().putString(KEY_MONTH_GOAL, value.toString()).apply()
        }

    /** Notificação fixa com o tempo correndo enquanto há um ponto aberto. */
    var notifyEnabled: Boolean
        get() = prefs.getBoolean("notify_enabled", true)
        set(value) {
            prefs.edit().putBoolean("notify_enabled", value).apply()
        }

    /** Avisos: meta do dia batida e jornada muito longa (possível esquecimento da saída). */
    var alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts_enabled", true)
        set(value) {
            prefs.edit().putBoolean("alerts_enabled", value).apply()
        }

    /** Último registro para o qual cada aviso já foi dado (evita repetir). */
    var goalAlertSessionId: Long
        get() = prefs.getLong("goal_alert_id", 0L)
        set(value) {
            prefs.edit().putLong("goal_alert_id", value).apply()
        }

    var longAlertSessionId: Long
        get() = prefs.getLong("long_alert_id", 0L)
        set(value) {
            prefs.edit().putLong("long_alert_id", value).apply()
        }

    /** O que aparece na notificação do ponto. */
    var notifOptions: NotifOptions
        get() = NotifOptions(
            entry = prefs.getBoolean("n_entry", true),
            time = prefs.getBoolean("n_time", true),
            earnings = prefs.getBoolean("n_earnings", true),
            goal = prefs.getBoolean("n_goal", true),
            button = prefs.getBoolean("n_button", true),
        )
        set(value) {
            prefs.edit()
                .putBoolean("n_entry", value.entry)
                .putBoolean("n_time", value.time)
                .putBoolean("n_earnings", value.earnings)
                .putBoolean("n_goal", value.goal)
                .putBoolean("n_button", value.button)
                .apply()
        }

    /** Tema: 0 = sistema, 1 = escuro, 2 = claro. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(value) {
            prefs.edit().putInt("theme_mode", value).apply()
        }

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0)
        set(value) {
            prefs.edit().putInt("accent_index", value).apply()
        }

    var hideValues: Boolean
        get() = prefs.getBoolean("hide_values", false)
        set(value) {
            prefs.edit().putBoolean("hide_values", value).apply()
        }

    /** Lembrete de entrada nos dias úteis. */
    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminder_enabled", false)
        set(value) {
            prefs.edit().putBoolean("reminder_enabled", value).apply()
        }

    /** Horário do lembrete, em minutos desde meia-noite (padrão 09:00). */
    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 9 * 60)
        set(value) {
            prefs.edit().putInt("reminder_minutes", value.coerceIn(0, 23 * 60 + 59)).apply()
        }

    var dailyGoalHours: Double
        get() = prefs.getString(KEY_DAY_GOAL, null)?.toDoubleOrNull() ?: 8.0
        set(value) {
            prefs.edit().putString(KEY_DAY_GOAL, value.toString()).apply()
        }

    private fun WorkSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("start", startMillis)
        endMillis?.let { put("end", it) }
        put("rate", rate)
        inLat?.let { put("inLat", it) }
        inLng?.let { put("inLng", it) }
        inAddress?.let { put("inAddress", it) }
        outLat?.let { put("outLat", it) }
        outLng?.let { put("outLng", it) }
        outAddress?.let { put("outAddress", it) }
        put("manual", manual)
    }

    private fun JSONObject.toSession(): WorkSession = WorkSession(
        id = getLong("id"),
        startMillis = getLong("start"),
        endMillis = if (has("end")) getLong("end") else null,
        rate = optDouble("rate", 0.0),
        inLat = if (has("inLat")) getDouble("inLat") else null,
        inLng = if (has("inLng")) getDouble("inLng") else null,
        inAddress = if (has("inAddress")) getString("inAddress") else null,
        outLat = if (has("outLat")) getDouble("outLat") else null,
        outLng = if (has("outLng")) getDouble("outLng") else null,
        outAddress = if (has("outAddress")) getString("outAddress") else null,
        manual = optBoolean("manual", false),
    )

    private companion object {
        const val KEY_SESSIONS = "sessions"
        const val KEY_RATE = "hourly_rate"
        const val KEY_MONTH_GOAL = "monthly_goal"
        const val KEY_DAY_GOAL = "daily_goal_hours"
    }
}

/** Quais informações aparecem na notificação do ponto. O cabeçalho "Jornada ativa" sempre aparece. */
data class NotifOptions(
    val entry: Boolean = true,
    val time: Boolean = true,
    val earnings: Boolean = true,
    val goal: Boolean = true,
    val button: Boolean = true,
)
