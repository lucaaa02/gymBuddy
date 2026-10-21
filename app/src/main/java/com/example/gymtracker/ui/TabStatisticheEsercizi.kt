package com.example.gymtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo

data class SingolaEsecuzione(
    val data: String,
    val pesoKg: Double,
    val serie: Int,
    val reps: Int,
    val note: String,
    val saltato: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabStatisticheEsercizi() {
    var catalogoEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }
    var feedAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }

    var esercizioSelezionato by remember { mutableStateOf<EsercizioCatalogo?>(null) }
    var menuEserciziEspanso by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaCatalogoEsercizi { catalogoEsercizi = it }
        FirebaseManager.ascoltaFeedAllenamenti { feedAllenamenti = it }
    }

    // Lista distinta di tutti gli utenti presenti nel Feed
    val listaUtenti = remember(feedAllenamenti) {
        feedAllenamenti.map { it.nomeUtente.uppercase() }.distinct().sorted()
    }

    // Mappa per ciascun utente della lista ordinata delle sue esecuzioni (dal più recente al meno recente)
    val mappaEsecuzioniPerUtente = remember(esercizioSelezionato, feedAllenamenti, listaUtenti) {
        if (esercizioSelezionato == null) emptyMap()
        else {
            val mappa = mutableMapOf<String, List<SingolaEsecuzione>>()
            listaUtenti.forEach { utente ->
                val listaUtente = mutableListOf<SingolaEsecuzione>()
                feedAllenamenti.forEach { sessione ->
                    if (sessione.nomeUtente.equals(utente, ignoreCase = true)) {
                        sessione.dettagliEsercizi.forEach { ex ->
                            if (ex.nomeEsercizio.equals(esercizioSelezionato!!.nome, ignoreCase = true) ||
                                (ex.idEsercizio.isNotBlank() && ex.idEsercizio == esercizioSelezionato!!.id)
                            ) {
                                listaUtente.add(
                                    SingolaEsecuzione(
                                        data = sessione.data,
                                        pesoKg = ex.pesoKg,
                                        serie = ex.serieEseguite,
                                        reps = ex.repsEseguite,
                                        note = ex.noteEsercizio,
                                        saltato = ex.saltato
                                    )
                                )
                            }
                        }
                    }
                }
                mappa[utente] = listaUtente
            }
            mappa
        }
    }

    // Determina il numero massimo di righe da mostrare tra tutti gli utenti
    val numeroMaxRighe = remember(mappaEsecuzioniPerUtente) {
        mappaEsecuzioniPerUtente.values.maxOfOrNull { it.size } ?: 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Analisi Esercizi 📊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Confronto indipendente per utente (dal più recente in cima)", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(10.dp))

        // SELEZIONE ESERCIZIO
        Text("Seleziona Esercizio da Analizzare:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ExposedDropdownMenuBox(
            expanded = menuEserciziEspanso,
            onExpandedChange = { menuEserciziEspanso = !menuEserciziEspanso }
        ) {
            OutlinedTextField(
                value = esercizioSelezionato?.let { "${it.nome} (${it.gruppoMuscolare})" } ?: "Scegli un esercizio...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuEserciziEspanso) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = menuEserciziEspanso,
                onDismissRequest = { menuEserciziEspanso = false }
            ) {
                catalogoEsercizi.forEach { ex ->
                    DropdownMenuItem(
                        text = { Text("${ex.nome} (${ex.gruppoMuscolare})") },
                        onClick = {
                            esercizioSelezionato = ex
                            menuEserciziEspanso = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (esercizioSelezionato == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "Seleziona un esercizio per visualizzare la tabella comparativa degli utenti.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else if (numeroMaxRighe == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "Nessun dato registrato per l'esercizio '${esercizioSelezionato?.nome}'.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Yellow,
                    fontSize = 13.sp
                )
            }
        } else {
            val scrollStatoOrizzontale = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                // INTESTAZIONE COLONNE (NOMI UTENTI)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollStatoOrizzontale)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp)
                ) {
                    listaUtenti.forEach { utente ->
                        Text(
                            text = utente,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray)

                // TABELLA DELLE ESESECUZIONI DAL PIÙ RECENTE AL MENO RECENTE
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollStatoOrizzontale)
                ) {
                    itemsIndexed((0 until numeroMaxRighe).toList()) { indexRiga, _ ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listaUtenti.forEach { utente ->
                                val listaEsecuzioni = mappaEsecuzioniPerUtente[utente] ?: emptyList()
                                val esecuzione = listaEsecuzioni.getOrNull(indexRiga)

                                Card(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(horizontal = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            esecuzione == null -> Color(0xFF252525)
                                            esecuzione.saltato -> Color(0xFF3A1E1E)
                                            else -> Color(0xFF1E3A2A)
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (esecuzione == null) {
                                            Text("-", color = Color.DarkGray, fontSize = 12.sp)
                                        } else {
                                            Text(
                                                text = esecuzione.data,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))

                                            if (esecuzione.saltato) {
                                                Text("NON FATTO ❌", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("${esecuzione.pesoKg} kg", fontWeight = FontWeight.Black, color = Color.White, fontSize = 13.sp)
                                                Text("${esecuzione.serie}s × ${esecuzione.reps}r", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                                                if (esecuzione.note.isNotBlank()) {
                                                    Text(esecuzione.note, color = Color(0xFFFFD54F), fontSize = 9.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}
