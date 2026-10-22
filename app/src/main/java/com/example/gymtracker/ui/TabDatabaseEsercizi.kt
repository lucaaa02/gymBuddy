package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.EsercizioCatalogo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TabDatabaseEsercizi() {
    var catalogo by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }
    var nomeEsercizio by remember { mutableStateOf("") }
    val gruppiSelezionati = remember { mutableStateListOf("Petto") }
    var ricerca by remember { mutableStateOf("") }

    var esercizioInModifica by remember { mutableStateOf<EsercizioCatalogo?>(null) }
    var editNome by remember { mutableStateOf("") }
    val editGruppi = remember { mutableStateListOf<String>() }

    // Elenco completo con Gambe, Quadricipiti, Femorali e Polpacci
    val listaGruppi = listOf("Petto", "Dorso", "Gambe", "Quadricipiti", "Femorali", "Polpacci", "Spalle", "Bicipiti", "Tricipiti", "Addome", "Multiarticolari", "Altro")

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaCatalogoEsercizi { catalogo = it }
    }

    val eserciziFiltrati = remember(catalogo, ricerca) {
        catalogo
            .filter { it.nome.contains(ricerca.trim(), ignoreCase = true) || it.gruppoMuscolare.contains(ricerca.trim(), ignoreCase = true) }
            .sortedWith(
                compareBy<EsercizioCatalogo> { ex ->
                    ex.gruppoMuscolare.split(",").firstOrNull()?.trim()?.lowercase() ?: ""
                }.thenBy { ex ->
                    ex.gruppoMuscolare.lowercase()
                }.thenBy { ex ->
                    ex.nome.lowercase()
                }
            )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("➕ Aggiungi Nuovo Esercizio", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nomeEsercizio,
                    onValueChange = { nomeEsercizio = it },
                    label = { Text("Nome Esercizio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Distretti Muscolari (seleziona uno o più):", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listaGruppi.forEach { g ->
                        val selezionato = gruppiSelezionati.contains(g)
                        FilterChip(
                            selected = selezionato,
                            onClick = {
                                if (selezionato) {
                                    if (gruppiSelezionati.size > 1) {
                                        gruppiSelezionati.remove(g)
                                    }
                                } else {
                                    gruppiSelezionati.add(g)
                                }
                            },
                            label = { Text(g, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (nomeEsercizio.isNotBlank() && gruppiSelezionati.isNotEmpty()) {
                            FirebaseManager.aggiungiEsercizioCatalogo(nomeEsercizio.trim(), gruppiSelezionati.toList())
                            nomeEsercizio = ""
                            gruppiSelezionati.clear()
                            gruppiSelezionati.add("Petto")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("SALVA NEL POOL", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = ricerca,
            onValueChange = { ricerca = it },
            label = { Text("🔍 Cerca tra gli esercizi...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eserciziFiltrati) { ex ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.nome, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text(ex.gruppoMuscolare, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        }

                        IconButton(onClick = {
                            esercizioInModifica = ex
                            editNome = ex.nome
                            editGruppi.clear()
                            val parsed = ex.gruppoMuscolare.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parsed.isEmpty()) {
                                editGruppi.add("Petto")
                            } else {
                                editGruppi.addAll(parsed)
                            }
                        }) {
                            Text("✏️", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (esercizioInModifica != null) {
        AlertDialog(
            onDismissRequest = { esercizioInModifica = null },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNome.isNotBlank() && editGruppi.isNotEmpty()) {
                            FirebaseManager.aggiornaEsercizioCatalogo(esercizioInModifica!!.id, editNome.trim(), editGruppi.toList())
                            esercizioInModifica = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("SALVA MODIFICHE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { esercizioInModifica = null }) {
                    Text("Annulla", color = Color.White)
                }
            },
            title = { Text("Modifica Esercizio ✏️", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNome,
                        onValueChange = { editNome = it },
                        label = { Text("Nome Esercizio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Distretti Muscolari (seleziona uno o più):", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listaGruppi.forEach { g ->
                            val selezionato = editGruppi.contains(g)
                            FilterChip(
                                selected = selezionato,
                                onClick = {
                                    if (selezionato) {
                                        if (editGruppi.size > 1) {
                                            editGruppi.remove(g)
                                        }
                                    } else {
                                        editGruppi.add(g)
                                    }
                                },
                                label = { Text(g, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
