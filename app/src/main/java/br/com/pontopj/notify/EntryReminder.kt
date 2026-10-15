package br.com.pontopj.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.pontopj.data.Store
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.onDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lembrete de entrada: nos dias úteis, no horário escolhido, avisa se o ponto ainda não foi aberto.
 * Usa alarme aproximado (sem permissão especial); o Android pode atrasar alguns minutos para poupar bateria.
 */
object EntryReminder {
    private const val REQUEST_CODE = 77

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Agenda (ou cancela) o próximo lembrete conforme os Ajustes. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val store = Store(app)
        if (!store.reminderEnabled) {
            alarms.cancel(pending(app))
            return
        }
        val triggerAt = nextTrigger(store.reminderMinutes, System.currentTimeMillis())
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(app))
        } catch (e: Exception) {
            // Sistema não permitiu agendar agora; tenta de novo na próxima abertura do app.
        }
    }

    /** Próximo dia útil (segunda a sexta) no horário escolhido que ainda esteja no futuro. */
    fun nextTrigger(minutesOfDay: Int, now: Long): Long {
        val zone = ZoneId.systemDefault()
        var date: LocalDate = dateOf(now)
        while (true) {
            val candidate = LocalDateTime.of(date, java.time.LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
            val millis = candidate.atZone(zone).toInstant().toEpochMilli()
            val weekday = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            if (weekday && millis > now) return millis
            date = date.plusDays(1)
        }
    }
}

/** Dispara o lembrete no horário e já agenda o próximo dia útil. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        EntryReminder.schedule(context)
        val store = Store(context)
        if (!store.reminderEnabled) return
        val sessions = store.loadSessions()
        val now = System.currentTimeMillis()
        val alreadyWorking = sessions.any { it.isActive } || sessions.onDate(dateOf(now)).isNotEmpty()
        if (!alreadyWorking) Notifier.entryReminder(context)
    }

    companion object {
        const val ACTION_REMIND = "br.com.pontopj.action.REMIND_ENTRY"
    }
}

/** Toque em "Registrar entrada" no lembrete. */
class ClockInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLOCK_IN) return
        if (ClockActions.clockIn(context)) {
            android.widget.Toast.makeText(context, "Entrada registrada", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_CLOCK_IN = "br.com.pontopj.action.CLOCK_IN"
    }
}
