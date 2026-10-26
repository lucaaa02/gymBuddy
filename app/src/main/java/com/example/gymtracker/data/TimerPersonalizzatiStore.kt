package com.example.gymtracker.data

import android.content.Context

object TimerPersonalizzatiStore {
    private const val PREFS = "timer_personalizzati"

    fun carica(context: Context, chiaveUtente: String): List<Int> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(chiaveUtente, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    fun salva(context: Context, chiaveUtente: String, secondi: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(chiaveUtente, secondi.filter { it > 0 }.distinct().map(Int::toString).toSet())
            .apply()
    }
}
