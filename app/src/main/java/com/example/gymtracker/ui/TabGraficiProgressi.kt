package com.example.gymtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo
import kotlin.math.roundToInt

data class PontoGrafico(
    val data: String,
    val pesoKg: Double,
    val serie: Int,
    val reps: Int,
    val volumeTotale: Double,
    val unRMStimato: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGraficiProgressi(nomeUtenteLoggato: String) {
    var catalogoEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }
    var feedAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }

    var esercizioSelezionato by remember { mutableStateOf<EsercizioCatalogo?>(null) }
    var menuEserciziEspanso by remember { mutableStateOf(false) }

    // Selezione dell'utente (Default: Utente Loggato)
    var utenteSelezionato by remember { mutableStateOf(nomeUtenteLoggato) }
    var menuUtentiEspanso by remember { mutableStateOf(false) }

    var inputNumSessioni by remember { mutableStateOf("4") }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaCatalogoEsercizi { catalogoEsercizi = it }
        FirebaseManager.ascoltaFeedAllenamenti { feedAllenamenti = it }
    }

    // Lista distinta degli utenti registrati nel feed
    val listaUtenti = remember(feedAllenamenti, nomeUtenteLoggato) {
        (listOf(nomeUtenteLoggato) + feedAllenamenti.map { it.nomeUtente }).distinct().sorted()
    }

    // Storico cronologico per l'utente selezionato
    val storicoPunti = remember(esercizioSelezionato, feedAllenamenti, utenteSelezionato) {
        if (esercizioSelezionato == null) emptyList()
        else {
            val punti = mutableListOf<PontoGrafico>()
            feedAllenamenti
                .filter { it.nomeUtente.equals(utenteSelezionato, ignoreCase = true) }
                .reversed()
                .forEach { sessione ->
                    sessione.dettagliEsercizi.forEach { ex ->
                        if ((ex.nomeEsercizio.equals(esercizioSelezionato!!.nome, ignoreCase = true) ||
                            (ex.idEsercizio.isNotBlank() && ex.idEsercizio == esercizioSelezionato!!.id)) && !ex.saltato && ex.pesoKg > 0
                        ) {
                            val volume = ex.pesoKg * ex.serieEseguite * ex.repsEseguite
                            val epley1RM = ex.pesoKg * (1.0 + (ex.repsEseguite / 30.0))
                            punti.add(
                                PontoGrafico(
                                    data = sessione.data,
                                    pesoKg = ex.pesoKg,
                                    serie = ex.serieEseguite,
                                    reps = ex.repsEseguite,
                                    volumeTotale = volume,
                                    unRMStimato = epley1RM
                                )
                            )
                        }
                    }
                }
            punti
        }
    }

    val numSessioniParsed = inputNumSessioni.toIntOrNull() ?: 0

    val puntiFiltrati = remember(storicoPunti, numSessioniParsed) {
        if (numSessioniParsed <= 0 || storicoPunti.size <= numSessioniParsed) storicoPunti
        else storicoPunti.takeLast(numSessioniParsed)
    }

    val percentualeCrescita = remember(puntiFiltrati) {
        if (puntiFiltrati.size < 2) 0.0
        else {
            val primo = puntiFiltrati.first().pesoKg
            val ultimo = puntiFiltrati.last().pesoKg
            if (primo == 0.0) 0.0 else ((ultimo - primo) / primo) * 100.0
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Grafici Progressi & Performance 📈", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Seleziona utente ed esercizio per analizzare la crescita", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(10.dp))

            // SELEZIONE UTENTE
            Text("1. Seleziona Utente:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ExposedDropdownMenuBox(
                expanded = menuUtentiEspanso,
                onExpandedChange = { menuUtentiEspanso = !menuUtentiEspanso }
            ) {
                OutlinedTextField(
                    value = utenteSelezionato,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuUtentiEspanso) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = menuUtentiEspanso,
                    onDismissRequest = { menuUtentiEspanso = false }
                ) {
                    listaUtenti.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u) },
                            onClick = {
                                utenteSelezionato = u
                                menuUtentiEspanso = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SELEZIONE ESERCIZIO
            Text("2. Seleziona Esercizio:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
        }

        if (esercizioSelezionato != null && storicoPunti.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Numero Sessioni da Analizzare:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = inputNumSessioni,
                        onValueChange = { inputNumSessioni = it },
                        label = { Text("es. 3, 5, 10", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Variazione Carico", fontSize = 11.sp, color = Color.Gray)
                            val segno = if (percentualeCrescita >= 0) "+" else ""
                            Text(
                                text = "$segno${(percentualeCrescita * 10).roundToInt() / 10.0}%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (percentualeCrescita >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Max Caricato", fontSize = 11.sp, color = Color.Gray)
                            val maxPeso = puntiFiltrati.maxOfOrNull { it.pesoKg } ?: 0.0
                            Text(
                                text = "$maxPeso kg",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("1RM Stimato", fontSize = 11.sp, color = Color.Gray)
                            val max1RM = puntiFiltrati.maxOfOrNull { it.unRMStimato } ?: 0.0
                            Text(
                                text = "${(max1RM * 10).roundToInt() / 10.0} kg",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Andamento Peso - $utenteSelezionato (Kg)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        GraficoLineeConDate(punti = puntiFiltrati)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Dettaglio Sessioni di $utenteSelezionato (${puntiFiltrati.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        puntiFiltrati.reversed().forEach { pt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pt.data, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${pt.pesoKg} kg (${pt.serie}s × ${pt.reps}r)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Vol: ${pt.volumeTotale.toInt()} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        } else if (esercizioSelezionato != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "Nessuna sessione registrata da $utenteSelezionato per questo esercizio.",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Yellow,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GraficoLineeConDate(punti: List<PontoGrafico>) {
    if (punti.isEmpty()) return

    val pesi = punti.map { it.pesoKg }
    val minPeso = (pesi.minOrNull() ?: 0.0) - 2.0
    val maxPeso = (pesi.maxOrNull() ?: 100.0) + 2.0
    val deltaPeso = if (maxPeso == minPeso) 1.0 else maxPeso - minPeso

    val colArancio = MaterialTheme.colorScheme.primary

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height

            val stepX = if (punti.size > 1) width / (punti.size - 1) else width / 2

            val path = Path()

            punti.forEachIndexed { index, pt ->
                val x = if (punti.size == 1) width / 2 else index * stepX
                val y = height - (((pt.pesoKg - minPeso) / deltaPeso) * height).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }

                drawCircle(
                    color = colArancio,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            drawPath(
                path = path,
                color = colArancio,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            punti.forEach { pt ->
                Text(
                    text = pt.data,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
