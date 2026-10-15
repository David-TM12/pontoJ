package br.com.pontopj

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda o último erro que fechou o app, para aparecer em Ajustes (e poder ser copiado). */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "ultimo_erro.txt")

    fun save(context: Context, error: Throwable) {
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date())
        val trace = Log.getStackTraceString(error).take(6000)
        file(context).writeText("$stamp\n$trace")
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

class PontoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                CrashLog.save(applicationContext, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
