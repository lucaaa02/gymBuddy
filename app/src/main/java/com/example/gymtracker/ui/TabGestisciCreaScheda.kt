package com.example.gymtracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.EsercizioCatalogo
import com.example.gymtracker.models.EsercizioInScheda
import com.example.gymtracker.models.SchedaAllenamento

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGestisciCreaSchedaSemplice(
    nomeUtente: String,
    schedaDaModificare: SchedaAllenamento? = null,
    onModificaCompletata: () -> Unit = {}
) {
    val context = LocalContext.current

    var mieSchede by remember { mutableStateOf(listOf<SchedaAllenamento>()) }
    var catalogoEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }

    var titoloScheda by remember { mutableStateOf("") }
    val listaEserciziScheda = remember { mutableStateListOf<EsercizioInScheda>() }

    var idSchedaInModifica by remember { mutableStateOf("") }
    var mostraSelettoreEsercizi by remember { mutableStateOf(false) }

    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaMieSchede(nomeUtente) { mieSchede = it }
        FirebaseManager.ascoltaCatalogoEsercizi { catalogoEsercizi = it }
    }

    LaunchedEffect(schedaDaModificare) {
        if (schedaDaModificare != null) {
            idSchedaInModifica = schedaDaModificare.idScheda
            titoloScheda = schedaDaModificare.titoloScheda
            listaEserciziScheda.clear()
            listaEserciziScheda.addAll(schedaDaModificare.esercizi)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (idSchedaInModifica.isBlank()) "📋 Crea Nuova Scheda" else "✏️ Modifica Scheda",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = titoloScheda,
                    onValueChange = { titoloScheda = it },
                    label = { Text("Titolo Scheda (es. Allenamento A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { mostraSelettoreEsercizi = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("➕ AGGIUNGI ESERCIZIO DA POOL", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(listaEserciziScheda) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            IconButton(onClick = { listaEserciziScheda.removeAt(index) }) {
                                Text("🗑️", fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = if (item.serieTarget == 0) "" else item.serieTarget.toString(),
                                onValueChange = { t ->
                                    val s = t.toIntOrNull() ?: 0
                                    listaEserciziScheda[index] = item.copy(serieTarget = s)
                                },
                                label = { Text("Serie") },
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = if (item.repsTarget == 0) "" else item.repsTarget.toString(),
                                onValueChange = { t ->
                                    val r = t.toIntOrNull() ?: 0
                                    listaEserciziScheda[index] = item.copy(repsTarget = r)
                                },
                                label = { Text("Reps") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // CAMPO PER INSERIRE LE NOTE DELL'ESERCIZIO
                        OutlinedTextField(
                            value = item.note,
                            onValueChange = { noteTesto ->
                                listaEserciziScheda[index] = item.copy(note = noteTesto)
                            },
                            label = { Text("📝 Note (es. 90s recupero, fermo al petto...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (titoloScheda.isBlank() || listaEserciziScheda.isEmpty()) {
                    Toast.makeText(context, "Inserisci un titolo e almeno un esercizio", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val schedaDaSalvare = SchedaAllenamento(
                    idScheda = idSchedaInModifica,
                    nomeUtente = nomeUtente,
                    titoloScheda = titoloScheda.trim(),
                    esercizi = listaEserciziScheda.toList()
                )

                FirebaseManager.salvaOAggiornaScheda(schedaDaSalvare)

                titoloScheda = ""
                idSchedaInModifica = ""
                listaEserciziScheda.clear()
                onModificaCompletata()
                Toast.makeText(context, "Scheda salvata con successo! 🚀", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("SALVA SCHEDA", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }

    val catalogoRaggruppato = remember(catalogoEsercizi) {
        catalogoEsercizi
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

    if (mostraSelettoreEsercizi) {
        AlertDialog(
            onDismissRequest = { mostraSelettoreEsercizi = false },
            title = { Text("Seleziona Esercizio 🏋️‍♂️") },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    catalogoRaggruppato.forEach { (gruppo, esercizi) ->
                        item {
                            Text(
                                text = "── $gruppo ──",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                        items(esercizi) { ex ->
                            TextButton(
                                onClick = {
                                    listaEserciziScheda.add(
                                        EsercizioInScheda(
                                            idEsercizio = ex.id,
                                            nomeEsercizio = ex.nome,
                                            serieTarget = 3,
                                            repsTarget = 10,
                                            note = ""
                                        )
                                    )
                                    mostraSelettoreEsercizi = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(ex.nome, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(ex.gruppoMuscolare, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mostraSelettoreEsercizi = false }) {
                    Text("Chiudi", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White
        )
    }
}
