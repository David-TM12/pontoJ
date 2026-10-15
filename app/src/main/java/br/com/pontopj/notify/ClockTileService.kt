package br.com.pontopj.notify

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import br.com.pontopj.data.Store

/** Atalho nas Configurações Rápidas: um toque registra a entrada ou a saída, sem abrir o app. */
class ClockTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val open = Store(this).loadSessions().any { it.isActive }
        if (open) {
            if (ClockActions.clockOut(this)) Notifier.cancelAlerts(this)
        } else {
            ClockActions.clockIn(this)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val open = Store(this).loadSessions().any { it.isActive }
        tile.state = if (open) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Ponto PJ"
        tile.subtitle = if (open) "Toque para sair" else "Toque para entrar"
        tile.updateTile()
    }
}
