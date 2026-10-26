package com.example.gymtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato

/** Utenti i cui allenamenti sono visibili nei resoconti social: utente corrente e amici. */
@Composable
internal fun rememberUtentiVisibili(nomeUtente: String): Set<String> {
    var chiaviAmici by remember(nomeUtente) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaChiaviAmici(nomeUtente) { chiaviAmici = it }
    }
    return remember(nomeUtente, chiaviAmici) {
        chiaviAmici + FirebaseManager.chiaveUtente(nomeUtente)
    }
}

internal fun AllenamentoCompletato.visibilePer(chiaviUtentiVisibili: Set<String>): Boolean =
    FirebaseManager.chiaveUtente(nomeUtente) in chiaviUtentiVisibili
