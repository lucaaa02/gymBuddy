package com.example.gymtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import java.util.Locale

data class UtenteClassifica(
    val nomeUtente: String,
    val numAllenamenti: Int,
    val tonnellaggioTotale: Double
)

data class RecordEsercizioMese(
    val nomeEsercizio: String,
    val pesoKg: Double,
    val vincitori: List<PrestazioneEsercizioMese>
)

data class PrestazioneEsercizioMese(
    val nomeUtente: String,
    val serie: Int,
    val reps: Int,
    val data: String
)

// PALETTA COLORI DISTINTI PER GLI UTENTI
val PalettaColoriUtenti = listOf(
    Color(0xFFFF9800), // Arancione
    Color(0xFF4CAF50), // Verde
    Color(0xFF2196F3), // Blu
    Color(0xFFE91E63), // Rosa / Magenta
    Color(0xFF9C27B0), // Viola
    Color(0xFF00BCD4), // Ciano
    Color(0xFFFFEB3B), // Giallo
    Color(0xFFFF5722), // Arancio Scuro
    Color(0xFF8BC34A)  // Verde Chiaro
)

@Composable
fun TabCommunityDashboard(nomeUtenteLoggato: String) {
    var feedAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }
    var catalogoEsercizi by remember { mutableStateOf(listOf<EsercizioCatalogo>()) }
    var offsetMesi by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { feedAllenamenti = it }
        FirebaseManager.ascoltaCatalogoEsercizi { catalogoEsercizi = it }
    }

    val utentiVisibili = rememberUtentiVisibili(nomeUtenteLoggato)
    val feedVisibile = remember(feedAllenamenti, utentiVisibili) {
        feedAllenamenti.filter { it.visibilePer(utentiVisibili) }
    }

    val calendarioSelezionato = remember(offsetMesi) {
        Calendar.getInstance(Locale.ITALIAN).apply { add(Calendar.MONTH, offsetMesi) }
    }
    val meseSelezionato = calendarioSelezionato.get(Calendar.MONTH)
    val annoSelezionato = calendarioSelezionato.get(Calendar.YEAR)
    val nomeMese = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)
        .format(calendarioSelezionato.time).replaceFirstChar { it.uppercase() }

    // 1. ASSEGNAZIONE COLORI AGLI UTENTI
    val mappaColoriUtenti = remember(feedVisibile, nomeUtenteLoggato) {
        val tuttiUtenti = (listOf(nomeUtenteLoggato) + feedVisibile.map { it.nomeUtente }).distinct().sorted()
        val mappa = mutableMapOf<String, Color>()
        tuttiUtenti.forEachIndexed { index, utente ->
            mappa[utente.uppercase()] = PalettaColoriUtenti[index % PalettaColoriUtenti.size]
        }
        mappa
    }

    // 2. MAPPA GIORNI CON LISTA UTENTI CHE SI SONO ALLENATI IN QUEL GIORNO
    val mappaGiorniUtenti = remember(feedVisibile, meseSelezionato, annoSelezionato) {
        val mappa = mutableMapOf<Int, MutableSet<String>>()
        feedVisibile.forEach { log ->
            val partiData = log.data.split("/")
            if (partiData.size >= 3) {
                val g = partiData[0].toIntOrNull() ?: -1
                val m = (partiData[1].toIntOrNull() ?: 1) - 1
                val a = partiData[2].toIntOrNull() ?: 2026
                if (m == meseSelezionato && a == annoSelezionato && g > 0) {
                    if (!mappa.containsKey(g)) mappa[g] = mutableSetOf()
                    mappa[g]?.add(log.nomeUtente.uppercase())
                }
            }
        }
        mappa
    }

    // 3. CLASSIFICA MENSELE
    val classificaMese = remember(feedVisibile, meseSelezionato, annoSelezionato) {
        val mappaUtenti = mutableMapOf<String, Pair<Int, Double>>()
        feedVisibile.forEach { log ->
            val partiData = log.data.split("/")
            if (partiData.size >= 3) {
                val mese = (partiData[1].toIntOrNull() ?: 1) - 1
                val anno = partiData[2].toIntOrNull() ?: 2026
                if (mese == meseSelezionato && anno == annoSelezionato) {
                    val nome = log.nomeUtente.uppercase()
                    val tonnLog = log.dettagliEsercizi.filter { !it.saltato }.sumOf { it.tonnellaggioTotale() }
                    val corrente = mappaUtenti[nome] ?: Pair(0, 0.0)
                    mappaUtenti[nome] = Pair(corrente.first + 1, corrente.second + tonnLog)
                }
            }
        }
        mappaUtenti.map { UtenteClassifica(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.numAllenamenti }
    }

    val recordPerEsercizio = remember(feedVisibile, catalogoEsercizi, meseSelezionato, annoSelezionato) {
        catalogoEsercizi
            .sortedBy { it.nome.lowercase() }
            .map { esercizio ->
                val candidati = feedVisibile
                    .filter { log ->
                        val partiData = log.data.split("/")
                        partiData.size >= 3 &&
                            (partiData[1].toIntOrNull() ?: 1) - 1 == meseSelezionato &&
                            (partiData[2].toIntOrNull() ?: 2026) == annoSelezionato
                    }
                    .flatMap { log ->
                        log.dettagliEsercizi
                            .filter { dettaglio ->
                                !dettaglio.saltato &&
                                    (dettaglio.nomeEsercizio.equals(esercizio.nome, ignoreCase = true) ||
                                        (esercizio.id.isNotBlank() && dettaglio.idEsercizio == esercizio.id))
                            }
                            .mapNotNull { dettaglio ->
                                val serie = dettaglio.getSerieEffettive()
                                val pesoMax = dettaglio.pesoMax()
                                if (pesoMax <= 0) return@mapNotNull null
                                val repsAlPesoMax = serie.filter { it.pesoKg == pesoMax }.maxOfOrNull { it.reps }
                                    ?: dettaglio.repsEseguite
                                PrestazioneEsercizioMese(
                                    nomeUtente = log.nomeUtente,
                                    serie = serie.count { it.tipo == TipoSerie.NORMALE },
                                    reps = repsAlPesoMax,
                                    data = log.data
                                ) to pesoMax
                            }
                    }

                val miglioriPerUtente = candidati
                    .groupBy { it.first.nomeUtente.uppercase() }
                    .values
                    .mapNotNull { prestazioniUtente ->
                        prestazioniUtente.maxWithOrNull(
                            compareBy<Pair<PrestazioneEsercizioMese, Double>> { it.second }
                                .thenBy { it.first.reps }
                        )
                    }
                val pesoRecord = miglioriPerUtente.maxOfOrNull { it.second }
                val vincitoriPeso = miglioriPerUtente.filter { it.second == pesoRecord }
                val repsRecord = vincitoriPeso.maxOfOrNull { it.first.reps }
                val vincitori = vincitoriPeso.filter { it.first.reps == repsRecord }.map { it.first }

                val record = if (pesoRecord == null || miglioriPerUtente.size < 2) {
                    null
                } else {
                    RecordEsercizioMese(
                        nomeEsercizio = esercizio.nome,
                        pesoKg = pesoRecord,
                        vincitori = vincitori
                    )
                }
                esercizio.nome to record
            }
            .filter { (_, record) -> record != null }
    }

    val giorniNelMese = calendarioSelezionato.getActualMaximum(Calendar.DAY_OF_MONTH)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Dashboard Community & Presenze 🏆", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Mappa cromatica delle presenze di tutti gli utenti e classifica mensile", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { offsetMesi-- }) {
                        Text("← Prec.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (offsetMesi == 0) "MESE CORRENTE" else "MESE $offsetMesi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(nomeMese, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    TextButton(onClick = { offsetMesi++ }, enabled = offsetMesi < 0) {
                        Text("Succ. →", fontWeight = FontWeight.Bold, color = if (offsetMesi < 0) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }
        }

        // 📅 CALENDARIO CROMATICO GRUPPO
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📅 Mappa Presenze Gruppo", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // LEGENDA COLORI UTENTI
                    Text("Legenda Utenti:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        mappaColoriUtenti.forEach { (nome, colore) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(colore, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(nome, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // GRIGLIA CALENDARIO CROMATICA
                    val numColonne = 7
                    val numRighe = (giorniNelMese + numColonne - 1) / numColonne

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (riga in 0 until numRighe) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until numColonne) {
                                    val giornoNum = riga * numColonne + col + 1
                                    if (giornoNum <= giorniNelMese) {
                                        val utentiInQuelGiorno = mappaGiorniUtenti[giornoNum] ?: emptySet()
                                        val listaColori = utentiInQuelGiorno.mapNotNull { mappaColoriUtenti[it] }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(Color(0xFF2B2B2B), shape = RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (listaColori.isNotEmpty()) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val w = size.width
                                                    val h = size.height

                                                    if (listaColori.size == 1) {
                                                        drawRect(color = listaColori[0], size = Size(w, h))
                                                    } else {
                                                        // Divisione in bande cromatiche per le sovrapposizioni
                                                        val numColori = listaColori.size
                                                        val fasciaW = w / numColori
                                                        listaColori.forEachIndexed { idx, c ->
                                                            drawRect(
                                                                color = c,
                                                                topLeft = Offset(idx * fasciaW, 0f),
                                                                size = Size(fasciaW, h)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "$giornoNum",
                                                fontSize = 11.sp,
                                                fontWeight = if (listaColori.isNotEmpty()) FontWeight.Black else FontWeight.Normal,
                                                color = if (listaColori.isNotEmpty()) Color.Black else Color.Gray
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🏆 CLASSIFICA MENSELE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏆 Classifica Mensile", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        Text("${classificaMese.size} Utenti Attivi", fontSize = 11.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (classificaMese.isEmpty()) {
                        Text("Nessun allenamento registrato in questo mese.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        classificaMese.forEachIndexed { index, utente ->
                            val medaglia = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${index + 1}°"
                            }

                            val coloreUtente = mappaColoriUtenti[utente.nomeUtente.uppercase()] ?: MaterialTheme.colorScheme.primary

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(coloreUtente.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(medaglia, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                                    Box(modifier = Modifier.size(8.dp).background(coloreUtente, shape = CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = utente.nomeUtente,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${utente.numAllenamenti} Sessioni", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Vol: ${utente.tonnellaggioTotale.toInt()} kg", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            if (index < classificaMese.size - 1) HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }

        // 🏋️ RECORD MENSILI PER ESERCIZIO
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🏋️ Record del gruppo per esercizio", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Peso massimo registrato da ciascun utente nel mese selezionato", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (catalogoEsercizi.isEmpty()) {
                        Text("Nessun esercizio disponibile nel database.", fontSize = 12.sp, color = Color.Gray)
                    } else if (recordPerEsercizio.isEmpty()) {
                        Text("Nessun esercizio è stato eseguito da almeno due utenti questo mese.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        recordPerEsercizio.forEachIndexed { index, (nomeEsercizio, record) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    if (record != null) {
                                        if (record.vincitori.size > 1) {
                                            Text("PARI MERITO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(
                                            record.vincitori.joinToString(" • ") {
                                                "${it.nomeUtente} (${it.serie} serie × ${it.reps} reps, ${it.data})"
                                            },
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    } else {
                                        Text("Nessun record nel mese", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                Text(
                                    text = record?.let { "${it.pesoKg} kg" } ?: "—",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (index < recordPerEsercizio.lastIndex) HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }

    }
}
