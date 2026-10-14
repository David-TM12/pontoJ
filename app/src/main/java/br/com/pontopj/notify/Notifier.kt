package br.com.pontopj.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import br.com.pontopj.MainActivity
import br.com.pontopj.R
import br.com.pontopj.data.WorkSession
import br.com.pontopj.data.brlPlain
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

    private fun hm(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L)
        return String.format("%02dh %02dm", minutes / 60, minutes % 60)
    }

    /**
     * Notificação fixa do ponto aberto, em cartão escuro com cronômetro, ganho em tempo real e barra
     * da meta. O cronômetro anda sozinho (é desenhado pelo Android); o ganho e a barra são
     * atualizados pelo serviço a cada 15 segundos.
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
        val entry = "Entrada: ${timeOf(session.startMillis)}"
        val earnings = if (hideValues) "R$ ••••" else brlPlain(dayEarnings)
        val chronoBase = SystemClock.elapsedRealtime() - dayMillis
        val stopIntent = clockOutIntent(context)

        val percent = if (goalMs > 0L) (dayMillis * 1000L / goalMs).coerceIn(0L, 1000L) / 10.0 else 0.0
        val goalStatus = when {
            goalMs <= 0L -> ""
            dayMillis >= goalMs -> "Meta batida 🎯"
            else -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f%% concluído • Faltam %s", percent, hm(goalMs - dayMillis))
        }
        val goalLabel = "Meta: ${(goalMs / 3_600_000L)}h${String.format("%02d", (goalMs / 60_000L) % 60)}"

        val big = RemoteViews(context.packageName, R.layout.notification_big).apply {
            setTextViewText(R.id.notif_entry, entry)
            setChronometer(R.id.notif_chrono, chronoBase, null, true)
            setTextViewText(R.id.notif_earnings, earnings)
            if (goalMs > 0L) {
                setViewVisibility(R.id.notif_goal_block, View.VISIBLE)
                setViewVisibility(R.id.notif_progress, View.VISIBLE)
                setTextViewText(R.id.notif_goal, goalLabel)
                setTextViewText(R.id.notif_goal_status, goalStatus)
                setProgressBar(R.id.notif_progress, 100, percent.toInt().coerceIn(0, 100), false)
            } else {
                setViewVisibility(R.id.notif_goal_block, View.GONE)
                setViewVisibility(R.id.notif_progress, View.GONE)
            }
            setOnClickPendingIntent(R.id.notif_btn, stopIntent)
        }
        val small = RemoteViews(context.packageName, R.layout.notification_small).apply {
            setChronometer(R.id.notif_chrono, chronoBase, null, true)
            setTextViewText(R.id.notif_earnings, earnings)
            setOnClickPendingIntent(R.id.notif_btn, stopIntent)
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(GOLD)
            .setContentTitle("Jornada ativa")
            .setContentText("$entry  ·  $earnings")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setSmallIcon(R.drawable.ic_stat_clock)
                    .setColor(GOLD)
                    .setContentTitle("Jornada ativa")
                    .setWhen(session.startMillis)
                    .setUsesChronometer(true)
                    .build(),
            )
            .build()
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
