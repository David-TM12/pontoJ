package br.com.pontopj.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Recebe do celular o estado do ponto, mesmo com o app do relógio fechado. */
class WearListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearProtocol.PATH_STATE) {
                StateRepo.update(this, DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }
}
