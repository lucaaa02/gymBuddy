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
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TopEsercizioSettimana(
    val nomeEsercizio: String,
    val pesoMaxKg: Double,
    val serie: Int,
    val reps: Int,
    val data: String
)

data class StatVolumeMuscolo(
    val nomeGruppo: String,
    val percentuale: Double
)

@Composable
fun TabResocontoSettimanale(nomeUtenteLoggato: String) {
    var feedAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }
    var catalogoEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }

    var offsetSettimane by remember { mutableStateOf(0) } // 0 = Settimana Corrente, -1 = Prec, ecc.

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { feedAllenamenti = it }
        FirebaseManager.ascoltaCatalogoEsercizi { catalogoEsercizi = it }
    }

    // Calcolo range date per la settimana selezionata (da Lunedì a Domenica)
    val (inizoSettimanaDate, fineSettimanaDate, testoRange) = remember(offsetSettimane) {
        val cal = Calendar.getInstance(Locale.ITALIAN)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.add(Calendar.WEEK_OF_YEAR, offsetSettimane)

        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val inizio = cal.time

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val fine = cal.time

        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
        Triple(inizio, fine, "${fmt.format(inizio)} - ${fmt.format(fine)}")
    }

    // Calcolo range per la settimana PRECEDENTE (per il confronto)
    val (inizioPrecDate, finePrecDate) = remember(offsetSettimane) {
        val cal = Calendar.getInstance(Locale.ITALIAN)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.add(Calendar.WEEK_OF_YEAR, offsetSettimane - 1)

        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        val inizio = cal.time

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val fine = cal.time

        Pair(inizio, fine)
    }

    val sdfData = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)

    // Helper per verificare se una data stringa ricade in un intervallo
    fun dataInIntervallo(dataStr: String, inizio: Date, fine: Date): Boolean {
        return try {
            val d = sdfData.parse(dataStr) ?: return false
            d >= inizio && d <= fine
        } catch (e: Exception) {
            false
        }
    }

    // Filter Allenamenti Settimana Corrente e Precedente per l'utente loggato
    val allenamentiSettimana = remember(feedAllenamenti, inizoSettimanaDate, fineSettimanaDate, nomeUtenteLoggato) {
        feedAllenamenti.filter { log ->
            log.nomeUtente.equals(nomeUtenteLoggato, ignoreCase = true) &&
            dataInIntervallo(log.data, inizoSettimanaDate, fineSettimanaDate)
        }
    }

    val allenamentiSettimanaPrec = remember(feedAllenamenti, inizioPrecDate, finePrecDate, nomeUtenteLoggato) {
        feedAllenamenti.filter { log ->
            log.nomeUtente.equals(nomeUtenteLoggato, ignoreCase = true) &&
            dataInIntervallo(log.data, inizioPrecDate, finePrecDate)
        }
    }

    // Statistiche chiave
    val numSessioni = allenamentiSettimana.size
    val numSessioniPrec = allenamentiSettimanaPrec.size
    val diffSessioni = numSessioni - numSessioniPrec

    val tonnellaggioTotale = allenamentiSettimana.sumOf { log ->
        log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.pesoKg * it.serieEseguite * it.repsEseguite }
    }
    val tonnellaggioPrec = allenamentiSettimanaPrec.sumOf { log ->
        log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.pesoKg * it.serieEseguite * it.repsEseguite }
    }

    val diffTonnellaggioPercentuale = if (tonnellaggioPrec == 0.0) 0.0 else ((tonnellaggioTotale - tonnellaggioPrec) / tonnellaggioPrec) * 100.0

    // Top pesi raggiunti per ciascun esercizio nella settimana
    val listaTopEsercizi = remember(allenamentiSettimana) {
        val mappaTop = mutableMapOf<String, TopEsercizioSettimana>()

        allenamentiSettimana.forEach { log ->
            log.dettagliEsercizi.filter { !it.saltato && it.pesoKg > 0 }.forEach { ex ->
                val chiave = ex.nomeEsercizio.uppercase()
                val esistente = mappaTop[chiave]

                if (esistente == null || ex.pesoKg > esistente.pesoMaxKg) {
                    mappaTop[chiave] = TopEsercizioSettimana(
                        nomeEsercizio = ex.nomeEsercizio,
                        pesoMaxKg = ex.pesoKg,
                        serie = ex.serieEseguite,
                        reps = ex.repsEseguite,
                        data = log.data
                    )
                }
            }
        }
        mappaTop.values.sortedByDescending { it.pesoMaxKg }
    }

    // Distribuzione Volume per Gruppo Muscolare nella settimana
    val distribuzioneMuscolare = remember(allenamentiSettimana, catalogoEsercizi) {
        val mappaGruppi = mutableMapOf<String, Int>()
        var totale = 0

        allenamentiSettimana.forEach { log ->
            log.dettagliEsercizi.filter { !it.saltato }.forEach { exLog ->
                val exCat = catalogoEsercizi.firstOrNull { 
                    it.nome.equals(exLog.nomeEsercizio, ignoreCase = true) || (it.id.isNotBlank() && it.id == exLog.idEsercizio) 
                }
                val gruppo = exCat?.gruppoMuscolare ?: "Altro"
                mappaGruppi[gruppo] = (mappaGruppi[gruppo] ?: 0) + 1
                totale++
            }
        }

        mappaGruppi.map { 
            StatVolumeMuscolo(
                nomeGruppo = it.key,
                percentuale = if (totale == 0) 0.0 else (it.value.toDouble() / totale) * 100.0
            )
        }.sortedByDescending { it.percentuale }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Resoconto Settimanale 📅", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Analisi delle performance e volumi di lavoro della settimana", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            // NAVIGATORE SETTIMANE
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { offsetSettimane-- }) {
                        Text("← Prec.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (offsetSettimane == 0) "SETTIMANA CORRENTE" else "SETTIMANA ${if (offsetSettimane > 0) "+$offsetSettimane" else offsetSettimane}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(testoRange, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    TextButton(
                        onClick = { offsetSettimane++ },
                        enabled = offsetSettimane < 0
                    ) {
                        Text("Succ. →", fontWeight = FontWeight.Bold, color = if (offsetSettimane < 0) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }
        }

        // HIGHLIGHTS E CONFRONTO VS SETTIMANA PRECEDENTE
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Allenamenti", fontSize = 11.sp, color = Color.Gray)
                        Text("$numSessioni", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        val segnoSess = if (diffSessioni >= 0) "+" else ""
                        Text(
                            text = "$segnoSess$diffSessioni vs prec.",
                            fontSize = 10.sp,
                            color = if (diffSessioni >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Volume Sollevato", fontSize = 11.sp, color = Color.Gray)
                        Text("${tonnellaggioTotale.toInt()} kg", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        val segnoTonn = if (diffTonnellaggioPercentuale >= 0) "+" else ""
                        Text(
                            text = "$segnoTonn${String.format("%.1f", diffTonnellaggioPercentuale)}% vs prec.",
                            fontSize = 10.sp,
                            color = if (diffTonnellaggioPercentuale >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // TOP PESI DELLA SETTIMANA
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔝 Migliori Carichi della Settimana", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Il peso più alto sollevato per ciascun esercizio eseguito", fontSize = 11.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (listaTopEsercizi.isEmpty()) {
                        Text("Nessun allenamento o esercizio registrato in questa settimana.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        listaTopEsercizi.forEachIndexed { index, ex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("il ${ex.data} • ${ex.serie}s × ${ex.reps}r", fontSize = 10.sp, color = Color.Gray)
                                }

                                Text(
                                    text = "${ex.pesoMaxKg} kg",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (index < listaTopEsercizi.size - 1) HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }

        // DISTRIBUZIONE DEL VOLUME DELLA SETTIMANA
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📊 Distribuzione Volume Settimanale", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Percentuale di stimolazione per distretto muscolare", fontSize = 11.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(12.dp))

                    if (distribuzioneMuscolare.isEmpty()) {
                        Text("Nessun dato sul volume muscolare per questa settimana.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        distribuzioneMuscolare.forEach { stat ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stat.nomeGruppo, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                    Text("${String.format("%.1f", stat.percentuale)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { (stat.percentuale / 100.0).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color(0xFF2A2A2A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
