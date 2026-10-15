package br.com.pontopj.sync

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import br.com.pontopj.data.Store
import br.com.pontopj.data.dateOf
import br.com.pontopj.data.extraStats
import br.com.pontopj.data.monthStats
import br.com.pontopj.data.inMonth
import br.com.pontopj.data.onDate
import br.com.pontopj.data.totalEarnings
import br.com.pontopj.data.totalMillis
import br.com.pontopj.ui.Accents
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.YearMonth

/** Envia ao relógio o estado do ponto (aberto/fechado, ganhos do dia e do mês, metas, cor). */
object WatchSync {
    fun push(context: Context) {
        try {
            val app = context.applicationContext
            val store = Store(app)
            val sessions = store.loadSessions()
            val now = System.currentTimeMillis()
            val open = sessions.firstOrNull { it.isActive }

            // Só registros já fechados: o relógio soma o registro aberto em tempo real.
            val closed = sessions.filter { !it.isActive }
            val today = closed.onDate(dateOf(now))
            val monthClosed = closed.inMonth(YearMonth.from(dateOf(now)))

            val todayDate = dateOf(now)
            val month = YearMonth.from(todayDate)
            val extra = sessions.extraStats(month, now, todayDate, store.dailyGoalHours)
            val projection = sessions.monthStats(month, now, todayDate).projection
            // Ganho de cada um dos últimos 7 dias (o mais antigo primeiro); o de hoje só tem registros fechados.
            val last7 = FloatArray(7) { i ->
                closed.onDate(todayDate.minusDays((6 - i).toLong())).totalEarnings(now).toFloat()
            }

            val request = PutDataMapRequest.create(WearProtocol.PATH_STATE).apply {
                dataMap.putBoolean(WearProtocol.K_ACTIVE, open != null)
                dataMap.putLong(WearProtocol.K_OPEN_START, open?.startMillis ?: 0L)
                dataMap.putDouble(WearProtocol.K_OPEN_RATE, open?.rate ?: store.hourlyRate)
                dataMap.putLong(WearProtocol.K_DAY_BASE_MILLIS, today.totalMillis(now))
                dataMap.putDouble(WearProtocol.K_DAY_BASE_EARN, today.totalEarnings(now))
                dataMap.putLong(WearProtocol.K_MONTH_BASE_MILLIS, monthClosed.totalMillis(now))
                dataMap.putDouble(WearProtocol.K_MONTH_BASE_EARN, monthClosed.totalEarnings(now))
                dataMap.putDouble(WearProtocol.K_MONTH_GOAL, store.monthlyGoal)
                dataMap.putDouble(WearProtocol.K_GOAL_HOURS, store.dailyGoalHours)
                dataMap.putLong(WearProtocol.K_BALANCE, extra.balanceMillis)
                dataMap.putInt(WearProtocol.K_STREAK, extra.streak)
                dataMap.putDouble(WearProtocol.K_PROJECTION, projection)
                dataMap.putFloatArray(WearProtocol.K_LAST7, last7)
                dataMap.putBoolean(WearProtocol.K_HIDE, store.hideValues)
                dataMap.putInt(WearProtocol.K_ACCENT, Accents[store.accentIndex.coerceIn(Accents.indices)].color.toArgb())
                // Muda a cada envio, para o relógio sempre receber a atualização.
                dataMap.putLong(WearProtocol.K_UPDATED_AT, now)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(app).putDataItem(request)
        } catch (e: Exception) {
            // Sem Google Play Services ou sem relógio pareado: o app funciona normalmente sem o relógio.
        }
    }
}
