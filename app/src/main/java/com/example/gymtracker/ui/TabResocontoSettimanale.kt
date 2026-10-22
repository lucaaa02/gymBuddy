package com.example.gymtracker.ui

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo
import com.example.gymtracker.models.TipoSerie
import com.example.gymtracker.models.getSerieEffettive
import com.example.gymtracker.models.pesoMax
import com.example.gymtracker.models.tonnellaggioTotale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RecordPersonaleEsercizio(
    val nomeEsercizio: String,
    val pesoMaxKg: Double,
    val serie: Int,
    val reps: Int,
    val dateRecord: List<String>
)

data class VolumeGiorno(
    val etichetta: String,
    val volumeKg: Double
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
        log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.tonnellaggioTotale() }
    }
    val tonnellaggioPrec = allenamentiSettimanaPrec.sumOf { log ->
        log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.tonnellaggioTotale() }
    }

    val diffTonnellaggioPercentuale = if (tonnellaggioPrec == 0.0) 0.0 else ((tonnellaggioTotale - tonnellaggioPrec) / tonnellaggioPrec) * 100.0

    val volumiGiornalieri = remember(allenamentiSettimana, inizoSettimanaDate) {
        val volumiPerData = allenamentiSettimana.groupBy { it.data }.mapValues { (_, logGiorno) ->
            logGiorno.sumOf { log ->
                log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.tonnellaggioTotale() }
            }
        }
        val calendario = Calendar.getInstance(Locale.ITALIAN).apply { time = inizoSettimanaDate }
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
        val formatoGiorno = SimpleDateFormat("EEE dd", Locale.ITALIAN)
        (0..6).map {
            val data = calendario.time
            VolumeGiorno(
                etichetta = formatoGiorno.format(data).replaceFirstChar { it.uppercase() },
                volumeKg = volumiPerData[formatoData.format(data)] ?: 0.0
            ).also { calendario.add(Calendar.DAY_OF_YEAR, 1) }
        }
    }

    val recordPersonali = remember(feedAllenamenti, catalogoEsercizi, nomeUtenteLoggato) {
        val mieiAllenamenti = feedAllenamenti.filter {
            it.nomeUtente.equals(nomeUtenteLoggato, ignoreCase = true)
        }
        catalogoEsercizi.mapNotNull { esercizio ->
            val prestazioni = mieiAllenamenti.flatMap { log ->
                log.dettagliEsercizi
                    .filter { dettaglio ->
                        !dettaglio.saltato &&
                            (dettaglio.nomeEsercizio.equals(esercizio.nome, ignoreCase = true) ||
                                (esercizio.id.isNotBlank() && dettaglio.idEsercizio == esercizio.id))
                    }
                    .mapNotNull { dettaglio ->
                        val serieEff = dettaglio.getSerieEffettive()
                        val peso = dettaglio.pesoMax()
                        val repsAlPesoMax = serieEff.filter { it.pesoKg == peso }.maxOfOrNull { it.reps }
                            ?: dettaglio.repsEseguite
                        if (peso <= 0.0 && repsAlPesoMax <= 0) null
                        else {
                            val serieNormali = serieEff.filter { it.tipo == TipoSerie.NORMALE }
                            Pair(Triple(peso, repsAlPesoMax, serieNormali.size), log.data)
                        }
                    }
            }
            val pesoRecord = prestazioni.maxOfOrNull { it.first.first }
            val prestazioniPesoRecord = prestazioni.filter { it.first.first == pesoRecord }
            val repsRecord = prestazioniPesoRecord.maxOfOrNull { it.first.second }
            val prestazioniRecord = prestazioniPesoRecord.filter { it.first.second == repsRecord }
            val migliore = prestazioniRecord.firstOrNull() ?: return@mapNotNull null
            RecordPersonaleEsercizio(
                nomeEsercizio = esercizio.nome,
                pesoMaxKg = migliore.first.first,
                serie = migliore.first.third,
                reps = migliore.first.second,
                dateRecord = prestazioniRecord.map { it.second }.distinct()
            )
        }.sortedBy { it.nomeEsercizio.lowercase() }
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
                val gruppi = exCat?.gruppoMuscolare?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("Altro")
                val listaGruppiEsercizio = if (gruppi.isEmpty()) listOf("Altro") else gruppi
                val numSerieEff = exLog.getSerieEffettive().size.coerceAtLeast(1)
                listaGruppiEsercizio.forEach { gruppo ->
                    mappaGruppi[gruppo] = (mappaGruppi[gruppo] ?: 0) + numSerieEff
                    totale += numSerieEff
                }
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
            Text("Resoconto Personale 📅", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Analisi delle performance e dei volumi di allenamento", fontSize = 12.sp, color = Color.Gray)

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

        // VOLUME GIORNALIERO DELLA SETTIMANA
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📊 Volume giornaliero", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Volume sollevato ogni giorno della settimana (kg)", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    GraficoVolumeGiornaliero(volumiGiornalieri)
                }
            }
        }

        // RECORD PERSONALI STORICI
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🏅 Record Personali", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Miglior carico per esercizio nel tuo storico", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (recordPersonali.isEmpty()) {
                        Text("Nessun record disponibile: completa un esercizio con peso o ripetizioni registrati.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        recordPersonali.forEachIndexed { index, record ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text(
                                        "${record.serie} serie × ${record.reps} reps • ${record.dateRecord.joinToString()}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    if (record.dateRecord.size > 1) {
                                        Text("RECORD RIPETUTO (${record.dateRecord.size} volte)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    "${record.pesoMaxKg} kg",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (index < recordPersonali.lastIndex) HorizontalDivider(color = Color(0xFF333333))
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

@Composable
private fun GraficoVolumeGiornaliero(dati: List<VolumeGiorno>) {
    val coloreBarra = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
        if (dati.isEmpty()) return@Canvas

        val sinistra = 48.dp.toPx()
        val destra = 8.dp.toPx()
        val alto = 12.dp.toPx()
        val basso = 28.dp.toPx()
        val larghezzaGrafico = size.width - sinistra - destra
        val altezzaGrafico = size.height - alto - basso
        val massimoVolume = dati.maxOfOrNull { it.volumeKg }?.coerceAtLeast(1.0) ?: 1.0
        val larghezzaFascia = larghezzaGrafico / dati.size

        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            textSize = 10.dp.toPx()
            textAlign = NativePaint.Align.RIGHT
        }

        for (divisione in 0..4) {
            val frazione = divisione / 4f
            val y = alto + altezzaGrafico * (1f - frazione)
            drawLine(
                color = Color(0xFF3A3A3A),
                start = androidx.compose.ui.geometry.Offset(sinistra, y),
                end = androidx.compose.ui.geometry.Offset(size.width - destra, y),
                strokeWidth = 1.dp.toPx()
            )
            val valore = massimoVolume * frazione
            drawContext.canvas.nativeCanvas.drawText(
                valore.toInt().toString(),
                sinistra - 6.dp.toPx(),
                y + paint.textSize / 3f,
                paint
            )
        }

        dati.forEachIndexed { index, giorno ->
            val centroX = sinistra + larghezzaFascia * (index + 0.5f)
            val altezzaBarra = (giorno.volumeKg / massimoVolume * altezzaGrafico).toFloat()
            val larghezzaBarra = larghezzaFascia * 0.56f
            drawRect(
                color = coloreBarra,
                topLeft = androidx.compose.ui.geometry.Offset(centroX - larghezzaBarra / 2f, alto + altezzaGrafico - altezzaBarra),
                size = androidx.compose.ui.geometry.Size(larghezzaBarra, altezzaBarra)
            )
            paint.textAlign = NativePaint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                giorno.etichetta,
                centroX,
                size.height - 6.dp.toPx(),
                paint
            )
            paint.textAlign = NativePaint.Align.RIGHT
        }
    }
}
