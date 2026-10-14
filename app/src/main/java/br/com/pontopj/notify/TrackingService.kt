package br.com.pontopj.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import br.com.pontopj.data.Store
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.onDate
import br.com.pontopj.data.totalEarnings
import br.com.pontopj.data.totalMillis
import android.app.NotificationManager

/**
 * Mantém a notificação do ponto aberto. Só roda enquanto existe um registro em andamento
 * e a notificação está ligada nos Ajustes.
 */
class TrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (refresh()) handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        handler.removeCallbacks(tick)
        if (!refresh(first = true)) {
            // O Android exige startForeground depois de startForegroundService, mesmo que seja para encerrar.
            startForeground(Notifier.ID_ONGOING, Notifier.placeholder(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.postDelayed(tick, REFRESH_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Atualiza a notificação. Devolve false quando não há mais o que mostrar. */
    private fun refresh(first: Boolean = false): Boolean {
        val store = Store(this)
        val sessions = store.loadSessions()
        val open = sessions.firstOrNull { it.isActive }
        if (open == null || !store.notifyEnabled) return false

        val now = System.currentTimeMillis()
        val today = sessions.onDate(dateOf(now))
        val dayMillis = today.totalMillis(now)
        val notification = Notifier.ongoing(
            context = this,
            session = open,
            dayMillis = dayMillis,
            dayEarnings = today.totalEarnings(now),
            goalHours = store.dailyGoalHours,
            hideValues = store.hideValues,
            options = store.notifOptions,
            now = now,
        )
        if (first) {
            startForeground(Notifier.ID_ONGOING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            getSystemService(NotificationManager::class.java).notify(Notifier.ID_ONGOING, notification)
        }

        if (store.alertsEnabled) checkAlerts(store, open.id, open.durationMillis(now), dayMillis)
        return true
    }

    private fun checkAlerts(store: Store, sessionId: Long, openMillis: Long, dayMillis: Long) {
        val goalMs = (store.dailyGoalHours * 3_600_000.0).toLong()
        if (goalMs > 0L && dayMillis >= goalMs && store.goalAlertSessionId != sessionId) {
            store.goalAlertSessionId = sessionId
            Notifier.alert(
                this, Notifier.ID_GOAL,
                "Meta do dia batida 🎯",
                "Você completou as ${br.com.pontopj.data.formatHm(goalMs)} de hoje. Se quiser, já pode registrar a saída.",
                withClockOut = true,
            )
        }
        if (openMillis >= LONG_SESSION_MS && store.longAlertSessionId != sessionId) {
            store.longAlertSessionId = sessionId
            Notifier.alert(
                this, Notifier.ID_LONG,
                "Esqueceu de registrar a saída?",
                "Seu ponto está aberto há ${br.com.pontopj.data.formatHm(openMillis)}.",
                withClockOut = true,
            )
        }
    }

    companion object {
        private const val REFRESH_MS = 15_000L
        private const val LONG_SESSION_MS = 12 * 3_600_000L

        /** Liga ou desliga o serviço conforme haja ponto aberto e a notificação esteja ativada. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val store = Store(app)
            val shouldRun = store.notifyEnabled && store.loadSessions().any { it.isActive }
            val intent = Intent(app, TrackingService::class.java)
            try {
                if (shouldRun) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
            } catch (e: Exception) {
                // Sistema não permitiu iniciar agora; tenta de novo na próxima abertura do app.
            }
        }
    }
}
