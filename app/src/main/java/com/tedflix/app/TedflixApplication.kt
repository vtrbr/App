package com.tedflix.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.tedflix.app.auth.AuthSession

/**
 * Captura exceções não tratadas apenas para diagnóstico local.
 * O handler original continua sendo chamado para preservar o comportamento do Android.
 */
class TedflixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthSession.init(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(LAST_CRASH_KEY, buildCrashReport(thread, error))
                    .commit()
            } catch (storageError: Throwable) {
                Log.e(TAG, "Não foi possível persistir o crash", storageError)
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }

    private fun buildCrashReport(thread: Thread, error: Throwable): String {
        return buildString {
            appendLine("TEDFLIX — CRASH NATIVO CAPTURADO")
            appendLine("Thread: ${thread.name}")
            appendLine("Tipo: ${error.javaClass.name}")
            appendLine("Mensagem: ${error.message ?: "(sem mensagem)"}")
            appendLine()
            appendLine("Stack trace:")
            append(Log.getStackTraceString(error).take(MAX_STACK_LENGTH))
        }
    }

    companion object {
        const val PREFS = "tedflix_crash_diagnostics"
        const val LAST_CRASH_KEY = "last_uncaught_exception"
        private const val TAG = "TedflixCrashHandler"
        private const val MAX_STACK_LENGTH = 12_000

        fun consumeLastCrash(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val report = preferences.getString(LAST_CRASH_KEY, null)
            if (!report.isNullOrBlank()) {
                preferences.edit().remove(LAST_CRASH_KEY).apply()
            }
            return report
        }
    }
}
