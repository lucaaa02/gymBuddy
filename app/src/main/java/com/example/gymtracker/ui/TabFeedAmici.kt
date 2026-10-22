package com.example.gymtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioEseguitoLog
import com.example.gymtracker.models.SerieLog
import com.example.gymtracker.models.TipoSerie
import com.example.gymtracker.models.getSerieEffettive
import com.example.gymtracker.models.pesoMax
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class GruppoUtenteFeed(
    val nickname: String,
    val periodi: List<Pair<String, List<AllenamentoCompletato>>>
)

@Composable
fun TabFeedAmici(nomeUtenteLoggato: String) {
    var feed by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }
    var allenamentoSelezionato by remember { mutableStateOf<AllenamentoCompletato?>(null) }
    var inModalitaModifica by remember { mutableStateOf(false) }
    var filtroPeriodo by remember { mutableStateOf("Tutti") }
    var erroreOperazione by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { feed = it }
    }

    val parserData = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN) }
    val gruppiUtenti = remember(feed, filtroPeriodo) {
        val adesso = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val inizioFiltro = when (filtroPeriodo) {
            "Ultimi 7 giorni" -> (adesso.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
            "Ultimi 30 giorni" -> (adesso.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) }
            else -> null
        }
        feed.filter { allenamento ->
            val data = runCatching { parserData.parse(allenamento.data) }.getOrNull()
            inizioFiltro == null || (data != null && !data.before(inizioFiltro.time))
        }.groupBy { it.nomeUtente.trim().ifBlank { "Utente sconosciuto" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (nickname, allenamenti) ->
                val periodi = allenamenti.groupBy { allenamento ->
                    val data = runCatching { parserData.parse(allenamento.data) }.getOrNull()
                    data?.let { SimpleDateFormat("yyyy-MM", Locale.ITALIAN).format(it) } ?: "data-sconosciuta"
                }.toSortedMap(compareByDescending { it })
                    .map { (chiave, lista) ->
                        val titoloPeriodo = if (chiave == "data-sconosciuta") "Data non disponibile"
                        else runCatching {
                            SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(SimpleDateFormat("yyyy-MM", Locale.ITALIAN).parse(chiave)!!)
                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
                        }.getOrDefault(chiave)
                        titoloPeriodo to lista.sortedByDescending { runCatching { parserData.parse(it.data)?.time }.getOrNull() ?: 0L }
                    }
                GruppoUtenteFeed(nickname, periodi)
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Feed Allenamenti Gruppo 🔥", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Gli allenamenti sono raggruppati per utente e mese. Puoi modificare o eliminare solo i tuoi.", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tutti", "Ultimi 7 giorni", "Ultimi 30 giorni").forEach { filtro ->
                FilterChip(selected = filtroPeriodo == filtro, onClick = { filtroPeriodo = filtro }, label = { Text(filtro) })
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (gruppiUtenti.isEmpty()) {
                item { Text("Nessun allenamento nel periodo selezionato.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            }
            items(gruppiUtenti, key = { it.nickname.lowercase() }) { gruppo ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gruppo.nickname.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    gruppo.periodi.forEach { (periodo, allenamenti) ->
                        Text(periodo, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        allenamenti.forEach { log ->
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
        }
    }
    }

    if (allenamentoSelezionato != null) {
        val log = allenamentoSelezionato!!
        val proprietario = log.nomeUtente.equals(nomeUtenteLoggato, ignoreCase = true)

        if (!inModalitaModifica) {
            AlertDialog(
                onDismissRequest = { allenamentoSelezionato = null },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (proprietario) {
                            TextButton(
                                onClick = { erroreOperazione = null; inModalitaModifica = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                            ) {
                                Text("Modifica", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = {
                                    FirebaseManager.eliminaAllenamentoSeProprietario(log.id, nomeUtenteLoggato) { eliminato ->
                                        if (eliminato) allenamentoSelezionato = null
                                        else erroreOperazione = "Non è stato possibile eliminare l'allenamento."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                            ) {
                                Text("Elimina", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        TextButton(
                            onClick = { allenamentoSelezionato = null },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Text("Chiudi", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp, maxLines = 1, softWrap = false)
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
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!proprietario) {
                            item { Text("Puoi modificare solo i tuoi allenamenti.", color = Color.Gray, fontSize = 12.sp) }
                        }
                        erroreOperazione?.let { messaggio ->
                            item { Text(messaggio, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        }
                        if (log.notaGeneraleSessione.isNotBlank()) {
                            item {
                                Text("📝 Note generali: ${log.notaGeneraleSessione}", fontSize = 13.sp, color = Color.Yellow)
                                HorizontalDivider(color = Color.DarkGray)
                            }
                        }

                        item {
                            Text("Esercizi Eseguiti:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        items(log.dettagliEsercizi) { ex ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                        if (ex.saltato) {
                                            Text("NON FATTO ❌", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("Max: ${ex.pesoMax()} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                        }
                                    }

                                    if (!ex.saltato) {
                                        val serie = ex.getSerieEffettive()
                                        if (serie.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                serie.forEach { s ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${TipoSerie.icona(s.tipo)} Set ${s.numeroSerie} (${s.tipo}):",
                                                            fontSize = 11.sp,
                                                            color = Color.Gray,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "${s.pesoKg} kg × ${s.reps} reps",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (ex.noteEsercizio.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Note: ${ex.noteEsercizio}", fontSize = 11.sp, color = Color(0xFFFFD54F))
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
            val listaEserciziMod = remember(log) {
                mutableStateListOf<EsercizioEseguitoLog>().apply {
                    addAll(log.dettagliEsercizi.map { ex ->
                        val eff = ex.getSerieEffettive()
                        ex.copy(serieDettaglio = eff.map { it.copy() })
                    })
                }
            }
            var notaGeneraleModificata by remember { mutableStateOf(log.notaGeneraleSessione) }

            AlertDialog(
                onDismissRequest = { inModalitaModifica = false },
                confirmButton = {
                    Button(onClick = {
                        val listaFinal = listaEserciziMod.map { ex ->
                            val s = ex.getSerieEffettive()
                            val pMax = s.maxOfOrNull { it.pesoKg } ?: ex.pesoKg
                            val rAvg = if (s.isNotEmpty()) s.sumOf { it.reps } / s.size else ex.repsEseguite
                            ex.copy(
                                pesoKg = pMax,
                                serieEseguite = s.size,
                                repsEseguite = rAvg,
                                serieDettaglio = s
                            )
                        }
                        val allenamentoAggiornato = log.copy(
                            notaGeneraleSessione = notaGeneraleModificata.trim(),
                            dettagliEsercizi = listaFinal
                        )
                        FirebaseManager.aggiornaAllenamentoSeProprietario(allenamentoAggiornato, nomeUtenteLoggato) { salvato ->
                            if (salvato) {
                                allenamentoSelezionato = null
                                inModalitaModifica = false
                                erroreOperazione = null
                            } else {
                                erroreOperazione = "Modifica non salvata: puoi intervenire solo sui tuoi allenamenti."
                            }
                        }
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
                        erroreOperazione?.let { messaggio ->
                            item { Text(messaggio, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        }
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

                        itemsIndexed(listaEserciziMod) { index, ex ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(ex.nomeEsercizio, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val serieDett = ex.getSerieEffettive()
                                    serieDett.forEachIndexed { sIdx, serieItem ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("#${sIdx + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.width(24.dp))

                                            var menuT by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.width(80.dp)) {
                                                Surface(
                                                    onClick = { menuT = true },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF383838)
                                                ) {
                                                    Text(
                                                        text = "${TipoSerie.icona(serieItem.tipo)} ${TipoSerie.etichettaCorta(serieItem.tipo)}",
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                                    )
                                                }
                                                DropdownMenu(expanded = menuT, onDismissRequest = { menuT = false }) {
                                                    TipoSerie.TUTTI.forEach { t ->
                                                        DropdownMenuItem(
                                                            text = { Text("${TipoSerie.icona(t)} $t", fontSize = 12.sp) },
                                                            onClick = {
                                                                val nList = serieDett.toMutableList()
                                                                nList[sIdx] = serieItem.copy(tipo = t)
                                                                listaEserciziMod[index] = ex.copy(serieDettaglio = nList)
                                                                menuT = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            OutlinedTextField(
                                                value = if (serieItem.pesoKg == 0.0) "" else serieItem.pesoKg.toString(),
                                                onValueChange = { txt ->
                                                    val p = txt.replace(',', '.').toDoubleOrNull() ?: 0.0
                                                    val nList = serieDett.toMutableList()
                                                    nList[sIdx] = serieItem.copy(pesoKg = p)
                                                    listaEserciziMod[index] = ex.copy(serieDettaglio = nList)
                                                },
                                                label = { Text("Kg", fontSize = 9.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = if (serieItem.reps == 0) "" else serieItem.reps.toString(),
                                                onValueChange = { txt ->
                                                    val r = txt.toIntOrNull() ?: 0
                                                    val nList = serieDett.toMutableList()
                                                    nList[sIdx] = serieItem.copy(reps = r)
                                                    listaEserciziMod[index] = ex.copy(serieDettaglio = nList)
                                                },
                                                label = { Text("Reps", fontSize = 9.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = ex.noteEsercizio,
                                        onValueChange = { nTxt ->
                                            listaEserciziMod[index] = ex.copy(noteEsercizio = nTxt)
                                        },
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
