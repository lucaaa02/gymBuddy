package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.gymtracker.models.EsercizioCatalogo
import com.example.gymtracker.models.EsercizioInScheda
import com.example.gymtracker.models.SchedaAllenamento
import com.example.gymtracker.models.repsPerSerieEffettive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGestisciCreaScheda(
    nomeUtente: String,
    schedaDaModificare: SchedaAllenamento?,
    onModificaCompletata: () -> Unit
) {
    var mieSchede by remember { mutableStateOf(listOf<SchedaAllenamento>()) }
    var poolEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }

    var idSchedaAttuale by remember { mutableStateOf("") }
    var titoloScheda by remember { mutableStateOf("") }
    val listaEserciziInScheda = remember { mutableStateListOf<EsercizioInScheda>() }

    var esercizioSelezionatoPool by remember { mutableStateOf<EsercizioCatalogo?>(null) }
    var dropdownEspanso by remember { mutableStateOf(false) }

    var serieTarget by remember { mutableStateOf("4") }
    var repsTarget by remember { mutableStateOf("10") }
    var noteEx by remember { mutableStateOf("") }

    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaMieSchede(nomeUtente) { mieSchede = it }
        FirebaseManager.ascoltaCatalogoEsercizi { poolEsercizi = it }
    }

    LaunchedEffect(schedaDaModificare) {
        if (schedaDaModificare != null) {
            idSchedaAttuale = schedaDaModificare.idScheda
            titoloScheda = schedaDaModificare.titoloScheda
            listaEserciziInScheda.clear()
            listaEserciziInScheda.addAll(schedaDaModificare.esercizi)
        }
    }

    val poolEserciziRaggruppati = remember(poolEsercizi) {
        poolEsercizi
            .sortedWith(
                compareBy<EsercizioCatalogo> { ex ->
                    ex.gruppoMuscolare.split(",").firstOrNull()?.trim()?.lowercase() ?: ""
                }.thenBy { ex ->
                    ex.gruppoMuscolare.lowercase()
                }.thenBy { ex ->
                    ex.nome.lowercase()
                }
            )
            .groupBy { ex ->
                ex.gruppoMuscolare.split(",").firstOrNull()?.trim()?.ifBlank { "Altro" } ?: "Altro"
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "editor") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (idSchedaAttuale.isBlank()) "Crea Nuova Scheda" else "Modifica Scheda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = titoloScheda,
                        onValueChange = { titoloScheda = it },
                        label = { Text("Titolo Scheda (es. Lunedì - Petto)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Seleziona Esercizio dalla Pool Globale:", fontSize = 12.sp, color = Color.Gray)

                    ExposedDropdownMenuBox(
                        expanded = dropdownEspanso,
                        onExpandedChange = { dropdownEspanso = !dropdownEspanso }
                    ) {
                        OutlinedTextField(
                            value = esercizioSelezionatoPool?.let { "${it.nome} (${it.gruppoMuscolare})" } ?: "Scegli un esercizio...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownEspanso) },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownEspanso,
                            onDismissRequest = { dropdownEspanso = false }
                        ) {
                            if (poolEsercizi.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Nessun esercizio. Aggiungilo nel tab Pool!") },
                                    onClick = { dropdownEspanso = false }
                                )
                            } else {
                                poolEserciziRaggruppati.forEach { (gruppo, lista) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "── $gruppo ──",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {},
                                        enabled = false
                                    )
                                    lista.forEach { ex ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(ex.nome, color = Color.White, fontWeight = FontWeight.Medium)
                                                    Text(ex.gruppoMuscolare, color = Color.Gray, fontSize = 10.sp)
                                                }
                                            },
                                            onClick = {
                                                esercizioSelezionatoPool = ex
                                                dropdownEspanso = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serieTarget,
                            onValueChange = { serieTarget = it },
                            label = { Text("Serie") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repsTarget,
                            onValueChange = { repsTarget = it },
                            label = { Text("Reps Target") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = noteEx,
                        onValueChange = { noteEx = it },
                        label = { Text("Note Esercizio") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val ex = esercizioSelezionatoPool
                            if (ex != null) {
                                val serie = serieTarget.toIntOrNull() ?: 3
                                val reps = repsTarget.toIntOrNull() ?: 10
                                listaEserciziInScheda.add(
                                    EsercizioInScheda(
                                        idEsercizio = ex.id,
                                        nomeEsercizio = ex.nome,
                                        serieTarget = serie,
                                        repsTarget = reps,
                                        repsPerSerie = List(serie.coerceAtLeast(0)) { reps },
                                        note = noteEx.trim()
                                    )
                                )
                                esercizioSelezionatoPool = null
                                noteEx = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("+ AGGIUNGI ALLA SCHEDA", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // VISUALIZZAZIONE ESERCIZI IN CORSO DI AGGIUNTA
        if (listaEserciziInScheda.isNotEmpty()) {
            item(key = "exercise-header") {
                Text(
                    text = "Esercizi in questa scheda (${listaEserciziInScheda.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            itemsIndexed(
                items = listaEserciziInScheda,
                key = { index, ex -> "exercise-$index-${ex.idEsercizio}" }
            ) { index, ex ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(
                                "${ex.serieTarget} serie • ${ex.repsPerSerieEffettive().joinToString("/") } reps",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ripetizioni per serie", fontSize = 11.sp, color = Color.Gray)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                (0 until ex.serieTarget.coerceAtLeast(0)).toList().chunked(3).forEach { gruppoIndici ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        gruppoIndici.forEach { indiceSerie ->
                                            OutlinedTextField(
                                                value = ex.repsPerSerie.getOrNull(indiceSerie)?.toString()
                                                    ?: ex.repsTarget.toString(),
                                                onValueChange = { testo ->
                                                    val repsPerSerie = List(ex.serieTarget.coerceAtLeast(0)) { serie ->
                                                        ex.repsPerSerie.getOrNull(serie) ?: ex.repsTarget
                                                    }.toMutableList()
                                                    repsPerSerie[indiceSerie] = testo.toIntOrNull() ?: 0
                                                    listaEserciziInScheda[index] = ex.copy(repsPerSerie = repsPerSerie)
                                                },
                                                label = { Text("Serie ${indiceSerie + 1}", fontSize = 10.sp) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - gruppoIndici.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            if (ex.note.isNotBlank()) {
                                Text("Note: ${ex.note}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { listaEserciziInScheda.remove(ex) }) {
                            Text("🗑️", fontSize = 16.sp)
                        }
                    }
                }
            }

            item(key = "save-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (titoloScheda.isNotBlank()) {
                                val scheda = SchedaAllenamento(
                                    idScheda = idSchedaAttuale,
                                    nomeUtente = nomeUtente,
                                    titoloScheda = titoloScheda.trim(),
                                    esercizi = listaEserciziInScheda.toList()
                                )
                                FirebaseManager.salvaOAggiornaScheda(scheda)
                                idSchedaAttuale = ""
                                titoloScheda = ""
                                listaEserciziInScheda.clear()
                                onModificaCompletata()
                            }
                        },
                        modifier = Modifier.weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(if (idSchedaAttuale.isBlank()) "SALVA SCHEDA" else "AGGIORNA SCHEDA", fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    if (idSchedaAttuale.isNotBlank()) {
                        TextButton(onClick = {
                            idSchedaAttuale = ""
                            titoloScheda = ""
                            listaEserciziInScheda.clear()
                            onModificaCompletata()
                        }, shape = RoundedCornerShape(14.dp)) {
                            Text("Annulla", color = Color.Red)
                        }
                    }
                }
            }
        }

        item(key = "saved-sheets-header") {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Le Tue Schede Salvate (${mieSchede.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        items(
            items = mieSchede,
            key = { scheda -> "saved-sheet-${scheda.idScheda}" }
        ) { scheda ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scheda.titoloScheda, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${scheda.esercizi.size} esercizi", fontSize = 11.sp, color = Color.Gray)
                    }

                    Row {
                        TextButton(onClick = {
                            idSchedaAttuale = scheda.idScheda
                            titoloScheda = scheda.titoloScheda
                            listaEserciziInScheda.clear()
                            listaEserciziInScheda.addAll(scheda.esercizi)
                        }, shape = RoundedCornerShape(14.dp)) {
                            Text("Modifica", fontSize = 12.sp)
                        }
                        TextButton(onClick = { FirebaseManager.eliminaScheda(scheda.idScheda) }, shape = RoundedCornerShape(14.dp)) {
                            Text("Elimina", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
