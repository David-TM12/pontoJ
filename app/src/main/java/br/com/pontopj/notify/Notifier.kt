package br.com.pontopj.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.pontopj.MainActivity
import br.com.pontopj.R
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brlPlain
import br.com.pontopj.data.formatHm
import br.com.pontopj.data.timeOf

/** Cria e monta as notificações do ponto. */
object Notifier {
    const val CHANNEL_ONGOING = "ponto_andamento"
    const val CHANNEL_ALERTS = "ponto_avisos"
    const val ID_ONGOING = 1001
    const val ID_GOAL = 1002
    const val ID_LONG = 1003

    private const val GOLD = 0xFFF5BA1E.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ponto em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra o tempo correndo enquanto você está com o ponto aberto"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Avisos do ponto", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meta do dia batida e lembrete de saída esquecida"
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun clockOutIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ClockOutReceiver::class.java).setAction(ClockOutReceiver.ACTION_CLOCK_OUT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Notificação fixa do ponto aberto. O cronômetro é desenhado pelo próprio Android (anda sozinho);
     * o ganho e a barra da meta diária são atualizados pelo serviço a cada 30 segundos.
     *
     * @param dayMillis horas já feitas hoje, somando todos os registros do dia (inclusive o aberto).
     * @param dayEarnings ganho de hoje.
     */
    fun ongoing(
        context: Context,
        session: WorkSession,
        dayMillis: Long,
        dayEarnings: Double,
        goalHours: Double,
        hideValues: Boolean,
        now: Long,
    ): Notification {
        val goalMs = (goalHours * 3_600_000.0).toLong()
        val entry = "Entrada às ${timeOf(session.startMillis)}"
        val earningsPart = if (hideValues) "" else "  ·  ${brlPlain(dayEarnings)} hoje"
        val short = entry + earningsPart

        val big = buildString {
            append(short)
            if (goalMs > 0L) {
                append('\n')
                if (dayMillis >= goalMs) {
                    append("Meta do dia batida 🎯  (${formatHm(dayMillis)} de ${formatHm(goalMs)})")
                } else {
                    val remaining = goalMs - dayMillis
                    append("Meta de ${formatHm(goalMs)} às ${timeOf(now + remaining)}  ·  faltam ${formatHm(remaining)}")
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(GOLD)
            .setContentTitle("Ponto aberto")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setWhen(session.startMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Registrar saída", clockOutIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setSmallIcon(R.drawable.ic_stat_clock)
                    .setColor(GOLD)
                    .setContentTitle("Ponto aberto")
                    .setWhen(session.startMillis)
                    .setUsesChronometer(true)
                    .build(),
            )
        if (goalMs > 0L) {
            val percent = (dayMillis * 100L / goalMs).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun alert(context: Context, id: Int, title: String, text: String, withClockOut: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
        if (withClockOut) builder.addAction(0, "Registrar saída", clockOutIntent(context))
        nm.notify(id, builder.build())
    }

    fun cancelAlerts(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_GOAL)
        nm.cancel(ID_LONG)
    }

    /** Notificação mínima, usada só quando o serviço precisa subir e já não há ponto aberto. */
    fun placeholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(GOLD)
            .setContentTitle("Ponto PJ")
            .setSilent(true)
            .build()
}
