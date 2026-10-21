package com.example.gymtracker.ui

import androidx.compose.foundation.clickable
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

@Composable
fun TabFeedAmici() {
    var feed by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }
    var allenamentoSelezionato by remember { mutableStateOf<AllenamentoCompletato?>(null) }
    var inModalitaModifica by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { feed = it }
    }

    Column {
        Text("Feed Allenamenti Gruppo 🔥", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Clicca su un allenamento per consultarlo, modificarlo o eliminarlo", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(feed) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            allenamentoSelezionato = log
                            inModalitaModifica = false
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(log.nomeUtente.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Text(log.data, fontSize = 10.sp, color = Color.Gray)
                        }
                        Text(log.titoloScheda, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text("${log.dettagliEsercizi.size} Esercizi svolti ➔ Tocca per la scheda completa", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }

    if (allenamentoSelezionato != null) {
        val log = allenamentoSelezionato!!

        if (!inModalitaModifica) {
            AlertDialog(
                onDismissRequest = { allenamentoSelezionato = null },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { inModalitaModifica = true }) {
                            Text("✏️ MODIFICA", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            FirebaseManager.eliminaAllenamentoCompletato(log.id)
                            allenamentoSelezionato = null
                        }) {
                            Text("🗑️ ELIMINA", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { allenamentoSelezionato = null }) {
                            Text("CHIUDI", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                },
                title = {
                    Column {
                        Text("${log.nomeUtente.uppercase()} • ${log.data}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(log.titoloScheda, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (log.notaGeneraleSessione.isNotBlank()) {
                            Text("📝 Note generali: ${log.notaGeneraleSessione}", fontSize = 13.sp, color = Color.Yellow)
                            HorizontalDivider(color = Color.DarkGray)
                        }

                        Text("Esercizi Eseguiti:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        log.dettagliEsercizi.forEach { ex ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text("${ex.serieEseguite}s × ${ex.repsEseguite}r @ ${ex.pesoKg} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                    }
                                    if (ex.noteEsercizio.isNotBlank()) {
                                        Text("Note: ${ex.noteEsercizio}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        } else {
            val pesiModificati = remember { mutableStateMapOf<Int, String>().apply { log.dettagliEsercizi.forEachIndexed { i, e -> put(i, e.pesoKg.toString()) } } }
            val serieModificate = remember { mutableStateMapOf<Int, String>().apply { log.dettagliEsercizi.forEachIndexed { i, e -> put(i, e.serieEseguite.toString()) } } }
            val repsModificate = remember { mutableStateMapOf<Int, String>().apply { log.dettagliEsercizi.forEachIndexed { i, e -> put(i, e.repsEseguite.toString()) } } }
            val noteModificate = remember { mutableStateMapOf<Int, String>().apply { log.dettagliEsercizi.forEachIndexed { i, e -> put(i, e.noteEsercizio) } } }
            var notaGeneraleModificata by remember { mutableStateOf(log.notaGeneraleSessione) }

            AlertDialog(
                onDismissRequest = { inModalitaModifica = false },
                confirmButton = {
                    Button(onClick = {
                        val listaEserciziAggiornata = log.dettagliEsercizi.mapIndexed { index, ex ->
                            EsercizioEseguitoLog(
                                idEsercizio = ex.idEsercizio,
                                nomeEsercizio = ex.nomeEsercizio,
                                pesoKg = pesiModificati[index]?.toDoubleOrNull() ?: ex.pesoKg,
                                serieEseguite = serieModificate[index]?.toIntOrNull() ?: ex.serieEseguite,
                                repsEseguite = repsModificate[index]?.toIntOrNull() ?: ex.repsEseguite,
                                noteEsercizio = noteModificate[index]?.trim() ?: ""
                            )
                        }

                        val allenamentoAggiornato = log.copy(
                            notaGeneraleSessione = notaGeneraleModificata.trim(),
                            dettagliEsercizi = listaEserciziAggiornata
                        )

                        FirebaseManager.salvaOAggiornaAllenamentoEseguito(allenamentoAggiornato)
                        allenamentoSelezionato = null
                        inModalitaModifica = false
                    }) {
                        Text("SALVA MODIFICHE", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { inModalitaModifica = false }) {
                        Text("Annulla", color = Color.Red)
                    }
                },
                title = { Text("Modifica Allenamento", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedTextField(
                                value = notaGeneraleModificata,
                                onValueChange = { notaGeneraleModificata = it },
                                label = { Text("Nota generale sessione") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(log.dettagliEsercizi.size) { index ->
                            val ex = log.dettagliEsercizi[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedTextField(
                                            value = pesiModificati[index] ?: "",
                                            onValueChange = { pesiModificati[index] = it },
                                            label = { Text("Peso", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = serieModificate[index] ?: "",
                                            onValueChange = { serieModificate[index] = it },
                                            label = { Text("Serie", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = repsModificate[index] ?: "",
                                            onValueChange = { repsModificate[index] = it },
                                            label = { Text("Reps", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = noteModificate[index] ?: "",
                                        onValueChange = { noteModificate[index] = it },
                                        label = { Text("Note esercizio", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White
            )
        }
    }
}
