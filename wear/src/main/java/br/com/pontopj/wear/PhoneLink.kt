package br.com.pontopj.wear

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/** Conversa com o celular: pede o estado e manda os comandos de entrada e saída. */
object PhoneLink {
    /** Lê o último estado guardado na camada de dados e pede um novo ao celular. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        try {
            Wearable.getDataClient(app).dataItems.addOnSuccessListener { buffer ->
                try {
                    for (item in buffer) {
                        if (item.uri.path == WearProtocol.PATH_STATE) {
                            StateRepo.update(app, DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                } finally {
                    buffer.release()
                }
            }
        } catch (e: Exception) {
            // Sem Google Play Services no relógio: continua com o que está guardado.
        }
        send(app, WearProtocol.PATH_REQUEST_STATE) {}
    }

    /** Envia um comando a todos os aparelhos conectados (o celular). [onResult] recebe true se foi entregue. */
    fun send(context: Context, path: String, onResult: (Boolean) -> Unit) {
        val app = context.applicationContext
        try {
            Wearable.getNodeClient(app).connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isEmpty()) {
                        onResult(false)
                        return@addOnSuccessListener
                    }
                    var delivered = false
                    var pending = nodes.size
                    for (node in nodes) {
                        Wearable.getMessageClient(app).sendMessage(node.id, path, ByteArray(0))
                            .addOnSuccessListener { delivered = true }
                            .addOnCompleteListener {
                                pending -= 1
                                if (pending == 0) onResult(delivered)
                            }
                    }
                }
                .addOnFailureListener { onResult(false) }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}
