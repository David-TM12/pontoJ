package br.com.pontopj.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Recebe o toque em "Registrar saída" na notificação. */
class ClockOutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLOCK_OUT) return
        if (ClockActions.clockOut(context)) {
            Notifier.cancelAlerts(context)
            Toast.makeText(context, "Saída registrada", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_CLOCK_OUT = "br.com.pontopj.action.CLOCK_OUT"
    }
}
