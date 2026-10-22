package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioEseguitoLog
import com.example.gymtracker.models.SchedaAllenamento
import com.example.gymtracker.models.SerieLog
import com.example.gymtracker.models.TipoSerie
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TabAllenatiOra(
    nomeUtente: String,
    onAvviaAllenamento: (SchedaAllenamento) -> Unit
) {
    var mieSchedePersonali by remember { mutableStateOf(listOf<SchedaAllenamento>()) }

    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaMieSchede(nomeUtente) { schede ->
            mieSchedePersonali = schede
        }
    }

    Column {
        Text("Le Tue Schede Personali:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Seleziona una tua scheda per iniziare la sessione", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        if (mieSchedePersonali.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "Non hai ancora creato nessuna scheda personale!\nVai nel menu 'Le Mie Schede 📋' per aggiungerne una tua.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mieSchedePersonali) { scheda ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(scheda.titoloScheda, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text("${scheda.esercizi.size} Esercizi inclusi", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onAvviaAllenamento(scheda) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("INIZIA QUESTO ALLENAMENTO", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchermataEsecuzioneAllenamento(
    nomeUtente: String,
    scheda: SchedaAllenamento,
    secondiTrascorsi: Int,
    onTempoUpdate: (Int) -> Unit,
    onMinimizza: () -> Unit,
    onTerminato: () -> Unit
) {
    val pesiState = remember { mutableStateMapOf<Int, String>() }
    val serieFatteState = remember { mutableStateMapOf<Int, String>() }
    val repsFatteState = remember { mutableStateMapOf<Int, String>() }
    val noteEsercizioState = remember { mutableStateMapOf<Int, String>() }
    val eserciziSaltatiState = remember { mutableStateMapOf<Int, Boolean>() }
    var notaGeneraleAllenamento by remember { mutableStateOf("") }

    var mostraPopupErroreIncompleto by remember { mutableStateOf(false) }
    var mostraConfermaAnnullamento by remember { mutableStateOf(false) }
    var listaEserciziIncompletiNames by remember { mutableStateOf(listOf<String>()) }

    var storicoAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { lista ->
            storicoAllenamenti = lista.filter { it.nomeUtente.equals(nomeUtente, ignoreCase = true) }
        }
    }

    // GESTIONE TIMER DI RECUPERO
    var tempoTimerImpostatoSec by remember { mutableStateOf(90) }
    var timerRimanenteSec by remember { mutableStateOf(tempoTimerImpostatoSec) }
    var timerInEsecuzione by remember { mutableStateOf(false) }
    var mostraDialogImpostaTimer by remember { mutableStateOf(false) }

    // CRONOMETRO INARRESTABILE
    val inizioTimestamp = remember { System.currentTimeMillis() - (secondiTrascorsi * 1000L) }
    var tempoAttualeCrono by remember { mutableStateOf(secondiTrascorsi) }

    LaunchedEffect(Unit) {
        while (true) {
            val secLive = ((System.currentTimeMillis() - inizioTimestamp) / 1000L).toInt()
            tempoAttualeCrono = secLive
            onTempoUpdate(secLive)
            delay(500L)
        }
    }

    // TIMER DI RECUPERO
    LaunchedEffect(timerInEsecuzione, timerRimanenteSec) {
        if (timerInEsecuzione && timerRimanenteSec > 0) {
            delay(1000L)
            timerRimanenteSec--
        } else if (timerRimanenteSec == 0) {
            timerInEsecuzione = false
        }
    }

    val minutiCrono = tempoAttualeCrono / 60
    val secCrono = tempoAttualeCrono % 60
    val cronoFormattato = String.format(Locale.getDefault(), "%02d:%02d", minutiCrono, secCrono)

    val minutiTimer = timerRimanenteSec / 60
    val secTimer = timerRimanenteSec % 60
    val timerFormattato = String.format(Locale.getDefault(), "%02d:%02d", minutiTimer, secTimer)

    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMinimizza) {
                            Text("←", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(scheda.titoloScheda, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }

                    TextButton(onClick = { mostraConfermaAnnullamento = true }) {
                        Text("Annulla", color = Color.Red, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATA", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = cronoFormattato,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RECUPERO", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timerFormattato,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (timerRimanenteSec == 0) Color.Red else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { mostraDialogImpostaTimer = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("⚙️", fontSize = 16.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                if (timerRimanenteSec == 0) timerRimanenteSec = tempoTimerImpostatoSec
                                timerInEsecuzione = !timerInEsecuzione
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (timerInEsecuzione) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (timerInEsecuzione) "PAUSA" else "AVVIA", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                timerRimanenteSec = tempoTimerImpostatoSec
                                timerInEsecuzione = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) {
                            Text("🔄", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(scheda.esercizi.size) { index ->
                val ex = scheda.esercizi[index]
                val saltato = eserciziSaltatiState[index] ?: false

                val ultimoLogEsercizio = storicoAllenamenti
                    .flatMap { it.dettagliEsercizi }
                    .firstOrNull { 
                        (it.nomeEsercizio.equals(ex.nomeEsercizio, ignoreCase = true) || 
                        (it.idEsercizio.isNotBlank() && it.idEsercizio == ex.idEsercizio)) &&
                        !it.saltato
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (saltato) Color(0xFF2C1C1C) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Non fatto ❌", fontSize = 11.sp, color = Color.Gray)
                                Checkbox(
                                    checked = saltato,
                                    onCheckedChange = { eserciziSaltatiState[index] = it }
                                )
                            }
                        }

                        Text("Target scheda: ${ex.serieTarget} serie × ${ex.repsTarget} reps", fontSize = 11.sp, color = Color.Gray)

                        if (ultimoLogEsercizio != null) {
                            Text(
                                text = "📊 Ultima volta svolto: ${ultimoLogEsercizio.pesoKg} kg × ${ultimoLogEsercizio.repsEseguite} reps (${ultimoLogEsercizio.serieEseguite} serie)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (ultimoLogEsercizio.noteEsercizio.isNotBlank()) {
                                Text(
                                    text = "📝 Note precedenti: ${ultimoLogEsercizio.noteEsercizio}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        } else {
                            Text("📊 Ultima volta svolto: Nessuna esecuzione precedente trovata", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        if (!saltato) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = pesiState[index] ?: "",
                                    onValueChange = { pesiState[index] = it },
                                    label = { Text("Peso (kg)", fontSize = 11.sp) },
                                    placeholder = { Text("es. 80 o 0", fontSize = 11.sp, color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = serieFatteState[index] ?: "",
                                    onValueChange = { serieFatteState[index] = it },
                                    label = { Text("Serie", fontSize = 11.sp) },
                                    placeholder = { Text("${ex.serieTarget}", fontSize = 11.sp, color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = repsFatteState[index] ?: "",
                                    onValueChange = { repsFatteState[index] = it },
                                    label = { Text("Reps", fontSize = 11.sp) },
                                    placeholder = { Text("${ex.repsTarget}", fontSize = 11.sp, color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = noteEsercizioState[index] ?: "",
                                onValueChange = { noteEsercizioState[index] = it },
                                label = { Text("Note esercizio", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Esercizio contrassegnato come saltato.", fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = notaGeneraleAllenamento,
            onValueChange = { notaGeneraleAllenamento = it },
            label = { Text("Note generali sulla sessione") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val eserciziIncompleti = mutableListOf<String>()

                scheda.esercizi.forEachIndexed { index, ex ->
                    val saltato = eserciziSaltatiState[index] ?: false
                    
                    val pesoTesto = pesiState[index]?.trim() ?: ""
                    val serieTesto = serieFatteState[index]?.trim() ?: ""
                    val repsTesto = repsFatteState[index]?.trim() ?: ""

                    val compilatoPienamente = pesoTesto.isNotBlank() && (repsTesto.isNotBlank() || serieTesto.isNotBlank())

                    if (!saltato && !compilatoPienamente) {
                        eserciziIncompleti.add(ex.nomeEsercizio)
                    }
                }

                if (eserciziIncompleti.isNotEmpty()) {
                    listaEserciziIncompletiNames = eserciziIncompleti
                    mostraPopupErroreIncompleto = true
                } else {
                    val dataOggi = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    val listaEseguiti = scheda.esercizi.mapIndexed { index, ex ->
                        val eSaltato = eserciziSaltatiState[index] ?: false
                        
                        val pesoNum = pesiState[index]?.toDoubleOrNull() ?: 0.0
                        val serieNum = serieFatteState[index]?.toIntOrNull() ?: ex.serieTarget
                        val repsNum = repsFatteState[index]?.toIntOrNull() ?: ex.repsTarget

                        val serieDett = if (eSaltato) emptyList() else (1..serieNum).map { num ->
                            SerieLog(numeroSerie = num, pesoKg = pesoNum, reps = repsNum, tipo = TipoSerie.NORMALE)
                        }
                        EsercizioEseguitoLog(
                            idEsercizio = ex.idEsercizio,
                            nomeEsercizio = ex.nomeEsercizio,
                            pesoKg = if (eSaltato) 0.0 else pesoNum,
                            repsEseguite = if (eSaltato) 0 else repsNum,
                            serieEseguite = if (eSaltato) 0 else serieNum,
                            noteEsercizio = if (eSaltato) "Saltato" else (noteEsercizioState[index]?.trim() ?: ""),
                            saltato = eSaltato,
                            serieDettaglio = serieDett
                        )
                    }

                    val logFinale = AllenamentoCompletato(
                        nomeUtente = nomeUtente,
                        titoloScheda = "${scheda.titoloScheda} ($cronoFormattato)",
                        data = dataOggi,
                        notaGeneraleSessione = notaGeneraleAllenamento.trim(),
                        dettagliEsercizi = listaEseguiti
                    )

                    FirebaseManager.salvaAllenamentoEseguito(logFinale)
                    onTerminato()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("COMPLETA E CONDIVIDI CON GLI AMICI", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
        }
    }

    // POPUP CONFERMA ANNULLAMENTO ALLENAMENTO
    if (mostraConfermaAnnullamento) {
        AlertDialog(
            onDismissRequest = { mostraConfermaAnnullamento = false },
            confirmButton = {
                Button(
                    onClick = {
                        mostraConfermaAnnullamento = false
                        onTerminato()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("SÌ, ANNULLA", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraConfermaAnnullamento = false }) {
                    Text("Rimani nell'allenamento", color = Color.White)
                }
            },
            title = { Text("Annullare l'allenamento? ⚠️", fontWeight = FontWeight.Bold) },
            text = { Text("Sei sicuro di voler annullare la sessione in corso? I progressi inseriti finora andranno persi.", fontSize = 13.sp) },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // POPUP SPIEGAZIONE ESERCIZI INCOMPLETI
    if (mostraPopupErroreIncompleto) {
        AlertDialog(
            onDismissRequest = { mostraPopupErroreIncompleto = false },
            confirmButton = {
                Button(onClick = { mostraPopupErroreIncompleto = false }) {
                    Text("HO CAPITO", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            title = { Text("Allenamento Incompleto ⚠️", fontWeight = FontWeight.Bold, color = Color.Yellow) },
            text = {
                Column {
                    Text("Per salvare l'allenamento devi inserire i dati o spuntare 'Non fatto ❌' per questi esercizi:\n", fontSize = 13.sp)
                    listaEserciziIncompletiNames.forEach { nomeEx ->
                        Text("• $nomeEx", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Inserisci il peso (scrivi 0 se a corpo libero) e le reps, oppure spunta 'Non fatto ❌'.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (mostraDialogImpostaTimer) {
        var inputSecondi by remember { mutableStateOf(tempoTimerImpostatoSec.toString()) }

        AlertDialog(
            onDismissRequest = { mostraDialogImpostaTimer = false },
            confirmButton = {
                Button(onClick = {
                    val nuoviSecondi = inputSecondi.toIntOrNull() ?: 90
                    tempoTimerImpostatoSec = nuoviSecondi
                    timerRimanenteSec = nuoviSecondi
                    timerInEsecuzione = false
                    mostraDialogImpostaTimer = false
                }) {
                    Text("IMPOSTA TIMER", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogImpostaTimer = false }) {
                    Text("Annulla", color = Color.Red)
                }
            },
            title = { Text("Imposta Recupero (Secondi)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputSecondi,
                        onValueChange = { inputSecondi = it },
                        label = { Text("Secondi di recupero") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = false, onClick = { inputSecondi = "60" }, label = { Text("60s") })
                        FilterChip(selected = false, onClick = { inputSecondi = "90" }, label = { Text("90s") })
                        FilterChip(selected = false, onClick = { inputSecondi = "120" }, label = { Text("120s") })
                        FilterChip(selected = false, onClick = { inputSecondi = "180" }, label = { Text("180s") })
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White
        )
    }
}
