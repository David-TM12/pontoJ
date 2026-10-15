package br.com.pontopj.sync

import br.com.pontopj.data.Privacy
import br.com.pontopj.data.Store
import br.com.pontopj.notify.ClockActions
import br.com.pontopj.notify.TrackingService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Recebe os comandos do relógio (entrada, saída, pedir estado), mesmo com o app fechado. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.PATH_CLOCK_IN -> ClockActions.clockIn(this)
            WearProtocol.PATH_CLOCK_OUT -> ClockActions.clockOut(this)
            WearProtocol.PATH_REQUEST_STATE -> WatchSync.push(this)
            WearProtocol.PATH_TOGGLE_HIDE -> {
                // Olho de ocultar valores: vale para o app, a notificação e o relógio.
                val store = Store(this)
                val hidden = !store.hideValues
                store.hideValues = hidden
                Privacy.hidden = hidden
                TrackingService.sync(this)
                WatchSync.push(this)
            }
        }
    }
}
