package br.com.pontopj.notify

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.pontopj.data.Store
import br.com.pontopj.data.WorkSession
import br.com.pontopj.sync.WatchSync

/** Avisa o app (se estiver aberto) que os registros mudaram fora dele, por exemplo pela notificação. */
object AppBus {
    @Volatile
    var onSessionsChanged: (() -> Unit)? = null

    fun notifyChanged() {
        val callback = onSessionsChanged ?: return
        Handler(Looper.getMainLooper()).post { callback() }
    }
}

object ClockActions {
    /** Encerra o ponto aberto agora (usado pelo botão da notificação). Devolve true se havia algo aberto. */
    fun clockOut(context: Context): Boolean {
        val store = Store(context)
        val sessions = store.loadSessions()
        val open = sessions.firstOrNull { it.isActive } ?: return false
        val now = System.currentTimeMillis()
        store.saveSessions(sessions.map { if (it.id == open.id) it.copy(endMillis = now) else it })
        TrackingService.sync(context)
        AppBus.notifyChanged()
        WatchSync.push(context)
        return true
    }

    /** Abre o ponto agora (usado pelo relógio). Devolve false se já havia um ponto aberto. */
    fun clockIn(context: Context): Boolean {
        val store = Store(context)
        val sessions = store.loadSessions()
        if (sessions.any { it.isActive }) {
            WatchSync.push(context)
            return false
        }
        val now = System.currentTimeMillis()
        val session = WorkSession(id = now, startMillis = now, rate = store.hourlyRate)
        store.saveSessions(listOf(session) + sessions)
        TrackingService.sync(context)
        AppBus.notifyChanged()
        WatchSync.push(context)
        return true
    }
}
