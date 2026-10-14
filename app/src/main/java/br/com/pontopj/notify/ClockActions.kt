package br.com.pontopj.notify

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.pontopj.data.Store

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
        return true
    }
}
