package com.example.gymtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.RichiestaAmicizia

@Composable
fun TabAmici(nomeUtente: String) {
    var profili by remember { mutableStateOf(emptyList<String>()) }
    var amici by remember { mutableStateOf(emptyList<String>()) }
    var richiesteRicevute by remember { mutableStateOf(emptyList<RichiestaAmicizia>()) }
    var richiesteInviate by remember { mutableStateOf(emptyList<String>()) }
    var ricerca by remember { mutableStateOf("") }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var messaggioErrore by remember { mutableStateOf(false) }
    var operazioneInCorso by remember { mutableStateOf(false) }

    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaProfili { profili = it }
        FirebaseManager.ascoltaAmici(nomeUtente) { amici = it }
        FirebaseManager.ascoltaRichiesteAmicizia(nomeUtente) { richiesteRicevute = it }
        FirebaseManager.ascoltaRichiesteInviate(nomeUtente) { richiesteInviate = it }
    }

    val chiaviAmici = remember(amici) { amici.map { it.trim().lowercase(Locale.ROOT) }.toSet() }
    val chiaviRichiesteInviate = remember(richiesteInviate) { richiesteInviate.toSet() }
    val utentiTrovati = remember(profili, amici, richiesteRicevute, ricerca, nomeUtente) {
        profili.asSequence()
            .filter { it.isNotBlank() && !it.equals(nomeUtente, ignoreCase = true) }
            .filter { it.trim().lowercase(Locale.ROOT) !in chiaviAmici }
            .filter { candidato -> richiesteRicevute.none { it.nickname.equals(candidato, ignoreCase = true) } }
            .filter { it.contains(ricerca.trim(), ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
            .take(30)
            .toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Amici", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Cerca utenti, invia richieste e gestisci i tuoi amici.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = ricerca,
                onValueChange = { ricerca = it; messaggio = null },
                label = { Text("Cerca per nickname") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (messaggio != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    messaggio!!,
                    color = if (messaggioErrore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
        }

        item {
            TitoloSezioneAmici("Richieste ricevute", richiesteRicevute.size)
        }
        if (richiesteRicevute.isEmpty()) {
            item { TestoVuotoAmici("Non hai richieste in sospeso.") }
        } else {
            items(richiesteRicevute, key = { "request_${it.idUtente}" }) { richiesta ->
                CardAmici {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(richiesta.nickname, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = !operazioneInCorso,
                            onClick = {
                                operazioneInCorso = true
                                FirebaseManager.rifiutaRichiestaAmicizia(nomeUtente, richiesta) { ok, testo ->
                                    operazioneInCorso = false
                                    messaggioErrore = !ok
                                    messaggio = testo
                                }
                            }
                        ) { Text("Rifiuta", color = Color.LightGray) }
                        Button(
                            enabled = !operazioneInCorso,
                            onClick = {
                                operazioneInCorso = true
                                FirebaseManager.accettaRichiestaAmicizia(nomeUtente, richiesta) { ok, testo ->
                                    operazioneInCorso = false
                                    messaggioErrore = !ok
                                    messaggio = testo
                                }
                            }
                        ) { Text("Accetta") }
                    }
                }
            }
        }

        item { TitoloSezioneAmici("I tuoi amici", amici.size) }
        if (amici.isEmpty()) {
            item { TestoVuotoAmici("Non hai ancora amici. Cerca un utente per inviare una richiesta.") }
        } else {
            items(amici, key = { "friend_${it.lowercase(Locale.ROOT)}" }) { amico ->
                CardAmici {
                    Text(amico, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }

        item {
            TitoloSezioneAmici("Cerca utenti", if (ricerca.isBlank()) 0 else utentiTrovati.size)
        }
        if (ricerca.isBlank()) {
            item { TestoVuotoAmici("Scrivi un nickname per cercare tra i profili registrati.") }
        } else if (utentiTrovati.isEmpty()) {
            item { TestoVuotoAmici("Nessun utente trovato con questo nickname.") }
        } else {
            items(utentiTrovati, key = { "profile_${it.lowercase(Locale.ROOT)}" }) { utente ->
                val chiaveUtente = FirebaseManager.chiaveUtente(utente)
                val richiestaGiaInviata = chiaveUtente in chiaviRichiesteInviate
                CardAmici {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(utente, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (richiestaGiaInviata) {
                            Text("Richiesta inviata", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Button(
                                enabled = !operazioneInCorso,
                                onClick = {
                                    operazioneInCorso = true
                                    FirebaseManager.inviaRichiestaAmicizia(nomeUtente, utente) { ok, testo ->
                                        operazioneInCorso = false
                                        messaggioErrore = !ok
                                        messaggio = testo
                                    }
                                }
                            ) { Text("Chiedi amicizia") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitoloSezioneAmici(titolo: String, conteggio: Int) {
    Text("$titolo ($conteggio)", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TestoVuotoAmici(testo: String) {
    Text(testo, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun CardAmici(contenuto: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { contenuto() }
    }
}
