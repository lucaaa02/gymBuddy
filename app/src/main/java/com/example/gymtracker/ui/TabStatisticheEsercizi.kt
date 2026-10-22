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
import com.example.gymtracker.models.SerieLog
import com.example.gymtracker.models.TipoSerie
import com.example.gymtracker.models.getSerieEffettive
import com.example.gymtracker.models.pesoMax

data class SingolaEsecuzione(
    val data: String,
    val pesoKg: Double,
    val serie: Int,
    val reps: Int,
    val note: String,
    val saltato: Boolean,
    val serieDettaglio: List<SerieLog> = emptyList()
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
                                val serieEff = ex.getSerieEffettive()
                                val serieNormali = serieEff.filter { it.tipo == TipoSerie.NORMALE }
                                listaUtente.add(
                                    SingolaEsecuzione(
                                        data = sessione.data,
                                        pesoKg = ex.pesoMax(),
                                        serie = serieNormali.size,
                                        reps = serieNormali.takeIf { it.isNotEmpty() }
                                            ?.let { it.sumOf { serie -> serie.reps } / it.size } ?: 0,
                                        note = ex.noteEsercizio,
                                        saltato = ex.saltato,
                                        serieDettaglio = serieEff
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

    val scrollStatoOrizzontale = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Analisi Esercizi 📊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Confronto indipendente per utente (dal più recente in cima)", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
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
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = menuEserciziEspanso,
                            onDismissRequest = { menuEserciziEspanso = false }
                        ) {
                            val catalogoOrdinato = catalogoEsercizi.sortedWith(
                                compareBy<EsercizioCatalogo> { ex ->
                                    ex.gruppoMuscolare.split(",").firstOrNull()?.trim()?.lowercase() ?: ""
                                }.thenBy { ex ->
                                    ex.gruppoMuscolare.lowercase()
                                }.thenBy { ex ->
                                    ex.nome.lowercase()
                                }
                            )
                            catalogoOrdinato.forEach { ex ->
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
                }
            }
        }

        if (esercizioSelezionato == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "Seleziona un esercizio per visualizzare la tabella comparativa degli utenti.",
                        modifier = Modifier.padding(18.dp),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (numeroMaxRighe == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "Nessun dato registrato per l'esercizio '${esercizioSelezionato?.nome}'.",
                        modifier = Modifier.padding(18.dp),
                        color = Color.Yellow,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollStatoOrizzontale)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
            }

            itemsIndexed((0 until numeroMaxRighe).toList()) { indexRiga, _ ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollStatoOrizzontale),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listaUtenti.forEach { utente ->
                        val listaEsecuzioni = mappaEsecuzioniPerUtente[utente] ?: emptyList()
                        val esecuzione = listaEsecuzioni.getOrNull(indexRiga)
                        Card(
                            modifier = Modifier.width(150.dp).padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    esecuzione == null -> Color(0xFF252525)
                                    esecuzione.saltato -> Color(0xFF3A1E1E)
                                    else -> Color(0xFF1E3A2A)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (esecuzione == null) {
                                    Text("-", color = Color.DarkGray, fontSize = 12.sp)
                                } else {
                                    Text(esecuzione.data, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (esecuzione.saltato) {
                                        Text("NON FATTO ❌", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("${esecuzione.pesoKg} kg", fontWeight = FontWeight.Black, color = Color.White, fontSize = 13.sp)
                                        Text("${esecuzione.serie} serie", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        if (esecuzione.serieDettaglio.isNotEmpty()) {
                                            val strSerie = esecuzione.serieDettaglio.joinToString(" | ") { "${it.pesoKg}k×${it.reps}" }
                                            Text(strSerie, color = Color.LightGray, fontSize = 8.sp, maxLines = 2, textAlign = TextAlign.Center)
                                        }
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
