package br.com.pontopj

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import br.com.pontopj.data.LocationFix
import br.com.pontopj.data.Privacy
import br.com.pontopj.data.Store
import br.com.pontopj.data.WorkSession
import br.com.pontopj.location.reverseGeocode
import br.com.pontopj.notify.AppBus
import br.com.pontopj.notify.TrackingService

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    init {
        Privacy.hidden = store.hideValues
        Privacy.persist = { store.hideValues = it }
        // O botão "Registrar saída" da notificação mexe nos registros fora do app.
        AppBus.onSessionsChanged = { reload() }
        TrackingService.sync(app)
    }

    /** Recarrega os registros do armazenamento (mudaram fora do app, por exemplo pela notificação). */
    fun reload() {
        sessions = store.loadSessions().sortedByDescending { it.startMillis }
    }

    var notifyEnabled by mutableStateOf(store.notifyEnabled)
        private set
    var alertsEnabled by mutableStateOf(store.alertsEnabled)
        private set

    fun setNotifyEnabled(value: Boolean) {
        notifyEnabled = value
        store.notifyEnabled = value
        TrackingService.sync(getApplication())
    }

    fun setAlertsEnabled(value: Boolean) {
        alertsEnabled = value
        store.alertsEnabled = value
    }

    override fun onCleared() {
        AppBus.onSessionsChanged = null
    }

    var sessions by mutableStateOf(store.loadSessions().sortedByDescending { it.startMillis })
        private set
    var hourlyRate by mutableStateOf(store.hourlyRate)
        private set
    var monthlyGoal by mutableStateOf(store.monthlyGoal)
        private set
    var dailyGoalHours by mutableStateOf(store.dailyGoalHours)
        private set

    var fix by mutableStateOf<LocationFix?>(null)
        private set
    var address by mutableStateOf<String?>(null)
        private set

    private var geocodedAt: LocationFix? = null
    private var lastGeocodeAttempt = 0L

    val active: WorkSession? get() = sessions.firstOrNull { it.isActive }

    // ---------- Localização ----------

    fun onLocation(f: LocationFix) {
        fix = f
        val now = System.currentTimeMillis()
        val last = geocodedAt
        val moved = last == null || distanceM(last, f) > 40f
        val retry = address == null && now - lastGeocodeAttempt > 30_000L
        if (moved || retry) {
            geocodedAt = f
            lastGeocodeAttempt = now
            reverseGeocode(getApplication(), f.lat, f.lng) { text ->
                if (text != null) address = text
            }
        }
    }

    private fun distanceM(a: LocationFix, b: LocationFix): Float {
        val out = FloatArray(1)
        Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, out)
        return out[0]
    }

    /** Usa a localização só se ela for recente (até 10 min). */
    private fun freshFix(): LocationFix? =
        fix?.takeIf { System.currentTimeMillis() - it.timeMillis < 10 * 60_000L }

    // ---------- Ponto ----------

    fun clockIn() {
        if (active != null) return
        val f = freshFix()
        val nowMs = System.currentTimeMillis()
        val session = WorkSession(
            id = nowMs,
            startMillis = nowMs,
            rate = hourlyRate,
            inLat = f?.lat,
            inLng = f?.lng,
            inAddress = if (f != null) address else null,
        )
        persist(listOf(session) + sessions)
    }

    fun clockOut() {
        val current = active ?: return
        val f = freshFix()
        val nowMs = System.currentTimeMillis()
        persist(
            sessions.map {
                if (it.id == current.id) {
                    it.copy(
                        endMillis = nowMs,
                        outLat = f?.lat,
                        outLng = f?.lng,
                        outAddress = if (f != null) address else null,
                    )
                } else {
                    it
                }
            },
        )
    }

    /** Adiciona um registro novo ou substitui um existente (mesmo id). */
    fun saveSession(session: WorkSession) {
        val exists = sessions.any { it.id == session.id }
        persist(if (exists) sessions.map { if (it.id == session.id) session else it } else sessions + session)
    }

    fun deleteSession(id: Long) {
        persist(sessions.filterNot { it.id == id })
    }

    fun clearAll() {
        persist(emptyList())
    }

    /** Importa registros ignorando os que já existem (mesma entrada e saída). Devolve (novos, repetidos). */
    fun importSessions(list: List<WorkSession>): Pair<Int, Int> {
        val existing = sessions.map { it.startMillis to it.endMillis }.toSet()
        val fresh = list.filter { (it.startMillis to it.endMillis) !in existing }
        if (fresh.isNotEmpty()) persist(sessions + fresh)
        return fresh.size to (list.size - fresh.size)
    }

    // ---------- Ajustes ----------

    fun updateSettings(rate: Double, monthGoal: Double, dayGoalHours: Double) {
        hourlyRate = rate
        monthlyGoal = monthGoal
        dailyGoalHours = dayGoalHours
        store.hourlyRate = rate
        store.monthlyGoal = monthGoal
        store.dailyGoalHours = dayGoalHours
    }

    private fun persist(list: List<WorkSession>) {
        sessions = list.sortedByDescending { it.startMillis }
        store.saveSessions(sessions)
        TrackingService.sync(getApplication())
    }
}
