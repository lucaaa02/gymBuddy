package com.example.gymtracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioEseguitoLog
import com.example.gymtracker.models.SchedaAllenamento
import com.example.gymtracker.models.SerieLog
import com.example.gymtracker.models.TipoSerie
import com.example.gymtracker.models.getSerieEffettive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataEsecuzioneAllenamento(
    nomeUtente: String,
    scheda: SchedaAllenamento,
    idAllenamento: String,
    dettagliList: MutableList<EsercizioEseguitoLog>,
    secondiTrascorsi: Int,
    secondiTimer: Int,
    timerAttivo: Boolean,
    dataAllenamento: String,
    onDataAllenamentoCambiata: (String) -> Unit,
    pesiDigitatiIniziali: Map<String, String>,
    ripetizioniDigitateIniziali: Map<String, String>,
    riprendeBozza: Boolean,
    onInputParzialiCambiati: (Map<String, String>, Map<String, String>) -> Unit,
    onAvviaTimer: (Int) -> Unit,
    onPausaTimer: () -> Unit,
    onRiprendiTimer: () -> Unit,
    onMinimizza: () -> Unit,
    onAnnullaSessione: () -> Unit,
    onTerminato: () -> Unit
) {
    val context = LocalContext.current
    val pesiSetMap = remember(scheda.idScheda) {
        mutableStateMapOf<String, String>().apply { putAll(pesiDigitatiIniziali) }
    }
    val repsSetMap = remember(scheda.idScheda) {
        mutableStateMapOf<String, String>().apply { putAll(ripetizioniDigitateIniziali) }
    }

    var ultimiLogMap by remember { mutableStateOf<Map<String, EsercizioEseguitoLog>>(emptyMap()) }
    var serieInizializzateDaStorico by remember(scheda.idScheda) { mutableStateOf(riprendeBozza) }

    var mostraCustomTimerDialog by remember { mutableStateOf(false) }
    var inputSecondiCustom by remember { mutableStateOf("90") }
    var mostraConfermaAnnullaDialog by remember { mutableStateOf(false) }

    // GESTIONE SELEZIONE DATA PERSONALIZZATA
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN) }
    val dataSelezionataStr = dataAllenamento
    var mostraDialogData by remember { mutableStateOf(false) }

    BackHandler {
        onMinimizza()
    }

    LaunchedEffect(nomeUtente) {
        FirebaseManager.ascoltaFeedAllenamenti { tuttiGliAllenamenti ->
            val mappaRisultati = mutableMapOf<String, EsercizioEseguitoLog>()
            val iMieiAllenamenti = tuttiGliAllenamenti.filter { 
                it.nomeUtente.equals(nomeUtente, ignoreCase = true) 
            }

            for (ex in scheda.esercizi) {
                for (allenamento in iMieiAllenamenti) {
                    val trovato = allenamento.dettagliEsercizi.firstOrNull { 
                        it.nomeEsercizio.equals(ex.nomeEsercizio, ignoreCase = true) && !it.saltato 
                    }
                    if (trovato != null) {
                        mappaRisultati[ex.nomeEsercizio.lowercase()] = trovato
                        break
                    }
                }
            }
            ultimiLogMap = mappaRisultati
        }
    }

    LaunchedEffect(pesiSetMap.toMap(), repsSetMap.toMap()) {
        onInputParzialiCambiati(pesiSetMap.toMap(), repsSetMap.toMap())
    }

    LaunchedEffect(ultimiLogMap) {
        if (!serieInizializzateDaStorico && ultimiLogMap.isNotEmpty()) {
            dettagliList.indices.forEach { index ->
                val item = dettagliList[index]
                val seriePrecedenti = ultimiLogMap[item.nomeEsercizio.lowercase()]?.getSerieEffettive().orEmpty()
                if (seriePrecedenti.isNotEmpty()) {
                    val serieAttuali = item.getSerieEffettive()
                    val numeroSerie = maxOf(serieAttuali.size, seriePrecedenti.size)
                    val serieAggiornate = (0 until numeroSerie).map { serieIndex ->
                        val attuale = serieAttuali.getOrNull(serieIndex)
                        val precedente = seriePrecedenti.getOrNull(serieIndex)
                        when {
                            attuale != null && precedente != null -> attuale.copy(
                                numeroSerie = serieIndex + 1,
                                tipo = precedente.tipo
                            )
                            attuale != null -> attuale.copy(numeroSerie = serieIndex + 1)
                            precedente != null -> precedente.copy(
                                numeroSerie = serieIndex + 1,
                                pesoKg = 0.0,
                                reps = 0
                            )
                            else -> SerieLog(numeroSerie = serieIndex + 1)
                        }
                    }
                    dettagliList[index] = item.copy(serieDettaglio = serieAggiornate)
                }
            }
            serieInizializzateDaStorico = true
        }
    }

    val minCron = secondiTrascorsi / 60
    val secCron = secondiTrascorsi % 60
    val cronometroStr = String.format("%02d:%02d", minCron, secCron)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "⏱️ $cronometroStr",
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (timerAttivo) "⏳ Recupero: ${secondiTimer}s" else if (secondiTimer > 0) "⏸️ Pausa: ${secondiTimer}s" else "💤 Timer fermo",
                            fontSize = 16.sp,
                            color = if (timerAttivo) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (timerAttivo) {
                            Button(
                                onClick = { onPausaTimer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Pausa", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { onRiprendiTimer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Riprendi", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { onMinimizza() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("📥", fontSize = 18.sp)
                        }

                        IconButton(
                            onClick = { mostraConfermaAnnullaDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.2f))
                        ) {
                            Text("❌", fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(
                    selectedTabIndex = 0,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {}
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(90, 120, 180, 240, 300).forEach { sec ->
                            FilterChip(
                                selected = false,
                                onClick = { onAvviaTimer(sec) },
                                label = { Text("${sec}s", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        FilterChip(
                            selected = false,
                            onClick = { mostraCustomTimerDialog = true },
                            label = { Text("✏️ Custom", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                labelColor = MaterialTheme.colorScheme.secondary
                            )
                        )

                    }
                }

                TextButton(
                    onClick = { mostraDialogData = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "📅 Data: $dataSelezionataStr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(dettagliList) { index, item ->
                val ultimoLog = ultimiLogMap[item.nomeEsercizio.lowercase()]

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.nomeEsercizio,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Non fatto", fontSize = 11.sp, color = Color.Gray)
                                Checkbox(
                                    checked = item.saltato,
                                    onCheckedChange = { checked ->
                                        dettagliList[index] = item.copy(saltato = checked)
                                    }
                                )
                            }
                        }

                        if (ultimoLog != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val serieUltimo = ultimoLog.getSerieEffettive()
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "📌 Ultima volta svolto (${serieUltimo.size} serie):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        serieUltimo.forEach { s ->
                                            Surface(
                                                color = Color(0xFF262626),
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E3E3E))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(TipoSerie.icona(s.tipo), fontSize = 10.sp)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "#${s.numeroSerie}: ${s.pesoKg}kg × ${s.reps}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (ultimoLog.noteEsercizio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFF2B281B),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "📜 Note scorso allenamento:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ultimoLog.noteEsercizio,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE6E0D4)
                                        )
                                    }
                                }
                            }
                        }

                        if (!item.saltato) {
                            val serieList = if (item.serieDettaglio.isNotEmpty()) {
                                item.serieDettaglio
                            } else {
                                val count = if (item.serieEseguite > 0) item.serieEseguite else 3
                                val defaultReps = if (item.repsEseguite > 0) item.repsEseguite else 10
                                val initList = (1..count).map { num ->
                                    SerieLog(numeroSerie = num, pesoKg = item.pesoKg, reps = defaultReps, tipo = TipoSerie.NORMALE)
                                }
                                dettagliList[index] = item.copy(serieDettaglio = initList)
                                initList
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Intestazione tabella serie
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.width(36.dp))
                                Text("TIPO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1.3f))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("KG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("REPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(36.dp))
                            }

                            // Righe delle serie
                            serieList.forEachIndexed { sIdx, serieItem ->
                                val setKey = "${index}_$sIdx"
                                val seriePrecedente = ultimoLog?.getSerieEffettive()?.getOrNull(sIdx)
                                var menuTipoEspanso by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Numero serie badge
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF2A2A2A), shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${sIdx + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Selettore Tipo Serie con Dropdown
                                    Box(modifier = Modifier.weight(1.3f)) {
                                        Surface(
                                            onClick = { menuTipoEspanso = true },
                                            shape = RoundedCornerShape(10.dp),
                                            color = when (serieItem.tipo) {
                                                TipoSerie.RISCALDAMENTO -> Color(0xFFE65100).copy(alpha = 0.25f)
                                                TipoSerie.BACKOFF -> Color(0xFF0288D1).copy(alpha = 0.25f)
                                                TipoSerie.SUPERSET -> Color(0xFF7B1FA2).copy(alpha = 0.25f)
                                                else -> Color(0xFF2E2E2E)
                                            },
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                when (serieItem.tipo) {
                                                    TipoSerie.RISCALDAMENTO -> Color(0xFFFF9800)
                                                    TipoSerie.BACKOFF -> Color(0xFF29B6F6)
                                                    TipoSerie.SUPERSET -> Color(0xFFBA68C8)
                                                    else -> Color(0xFF555555)
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 9.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${TipoSerie.icona(serieItem.tipo)} ${TipoSerie.etichettaCorta(serieItem.tipo)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text("▾", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = menuTipoEspanso,
                                            onDismissRequest = { menuTipoEspanso = false }
                                        ) {
                                            TipoSerie.TUTTI.forEach { tipoOpz ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(TipoSerie.icona(tipoOpz), fontSize = 14.sp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(tipoOpz, fontWeight = if (serieItem.tipo == tipoOpz) FontWeight.Bold else FontWeight.Normal)
                                                        }
                                                    },
                                                    onClick = {
                                                        val nuoveSerie = serieList.toMutableList()
                                                        nuoveSerie[sIdx] = serieItem.copy(tipo = tipoOpz)
                                                        dettagliList[index] = item.copy(serieDettaglio = nuoveSerie)
                                                        menuTipoEspanso = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Campo KG
                                    OutlinedTextField(
                                        value = pesiSetMap[setKey] ?: if (serieItem.pesoKg == 0.0) "" else serieItem.pesoKg.toString(),
                                        onValueChange = { text ->
                                            pesiSetMap[setKey] = text
                                            val pesoParsed = text.replace(',', '.').toDoubleOrNull() ?: 0.0
                                            val nuoveSerie = serieList.toMutableList()
                                            nuoveSerie[sIdx] = serieItem.copy(pesoKg = pesoParsed)
                                            dettagliList[index] = item.copy(serieDettaglio = nuoveSerie)
                                        },
                                        placeholder = {
                                            Text(
                                                seriePrecedente?.pesoKg?.toString() ?: "0",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color(0xFF444444)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Campo REPS
                                    OutlinedTextField(
                                        value = repsSetMap[setKey] ?: if (serieItem.reps == 0) "" else serieItem.reps.toString(),
                                        onValueChange = { text ->
                                            repsSetMap[setKey] = text
                                            val repsParsed = text.toIntOrNull() ?: 0
                                            val nuoveSerie = serieList.toMutableList()
                                            nuoveSerie[sIdx] = serieItem.copy(reps = repsParsed)
                                            dettagliList[index] = item.copy(serieDettaglio = nuoveSerie)
                                        },
                                        placeholder = {
                                            Text(
                                                seriePrecedente?.reps?.toString() ?: "0",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                            unfocusedBorderColor = Color(0xFF444444)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Pulsante elimina serie
                                    IconButton(
                                        onClick = {
                                            if (serieList.size > 1) {
                                                val nuoveSerie = serieList.toMutableList()
                                                nuoveSerie.removeAt(sIdx)
                                                val riordinate = nuoveSerie.mapIndexed { i, s -> s.copy(numeroSerie = i + 1) }
                                                dettagliList[index] = item.copy(serieDettaglio = riordinate)
                                                pesiSetMap.remove(setKey)
                                                repsSetMap.remove(setKey)
                                            }
                                        },
                                        enabled = serieList.size > 1,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text(
                                            text = "🗑️",
                                            fontSize = 14.sp,
                                            color = if (serieList.size > 1) Color.Red else Color(0xFF444444)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Pulsante Aggiungi Serie
                            Button(
                                onClick = {
                                    val nuoveSerie = serieList.toMutableList()
                                    val ultima = serieList.lastOrNull()
                                    val nextNum = serieList.size + 1
                                    nuoveSerie.add(
                                        SerieLog(
                                            numeroSerie = nextNum,
                                            pesoKg = ultima?.pesoKg ?: 0.0,
                                            reps = ultima?.reps ?: 10,
                                            tipo = if (ultima?.tipo == TipoSerie.RISCALDAMENTO) TipoSerie.NORMALE else (ultima?.tipo ?: TipoSerie.NORMALE)
                                        )
                                    )
                                    dettagliList[index] = item.copy(serieDettaglio = nuoveSerie)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text(
                                    text = "+ AGGIUNGI SERIE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF252525)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "✏️ Note per la sessione di oggi",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = item.noteEsercizio,
                                        onValueChange = { noteTesto ->
                                            dettagliList[index] = item.copy(noteEsercizio = noteTesto)
                                        },
                                        placeholder = { Text("Scrivi note per oggi...", fontSize = 12.sp, color = Color.Gray) },
                                        singleLine = false,
                                        maxLines = 3,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                            unfocusedBorderColor = Color(0xFF3A3A3A)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val nonIncompilati = dettagliList.filter { ex ->
                    if (ex.saltato) return@filter false
                    val serie = ex.getSerieEffettive()
                    if (serie.isEmpty()) return@filter true
                    serie.any { it.reps <= 0 }
                }
                if (nonIncompilati.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Compila le ripetizioni per tutte le serie svolte oppure segna 'Non fatto'",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                val listaFinale = dettagliList.map { ex ->
                    if (ex.saltato) {
                        ex.copy(pesoKg = 0.0, serieEseguite = 0, repsEseguite = 0, serieDettaglio = emptyList())
                    } else {
                        val serie = ex.getSerieEffettive()
                        val pesoMax = serie.maxOfOrNull { it.pesoKg } ?: 0.0
                        val repsMedie = if (serie.isNotEmpty()) (serie.sumOf { it.reps } / serie.size) else 0
                        ex.copy(
                            pesoKg = pesoMax,
                            serieEseguite = serie.size,
                            repsEseguite = repsMedie,
                            serieDettaglio = serie
                        )
                    }
                }

                val allenamentoCompletato = AllenamentoCompletato(
                    id = idAllenamento,
                    nomeUtente = nomeUtente,
                    titoloScheda = scheda.titoloScheda,
                    data = dataSelezionataStr,
                    durataSecondi = secondiTrascorsi,
                    dettagliEsercizi = listaFinale
                )
                FirebaseManager.salvaAllenamentoEseguito(allenamentoCompletato)
                onTerminato()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("COMPLETA E CONDIVIDI 🚀", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black)
        }
    }

    // DIALOG SCELTA DATA DELL'ALLENAMENTO
    if (mostraDialogData) {
        var tempInputData by remember { mutableStateOf(dataSelezionataStr) }

        AlertDialog(
            onDismissRequest = { mostraDialogData = false },
            title = { Text("Seleziona Data Allenamento 📅", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Pulsanti rapidi o inserisci la data (GG/MM/AAAA):", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                tempInputData = dateFormat.format(Date())
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Oggi", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                tempInputData = dateFormat.format(cal.time)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Ieri", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempInputData,
                        onValueChange = { tempInputData = it },
                        label = { Text("Data (es. 15/10/2026)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDataAllenamentoCambiata(tempInputData)
                        mostraDialogData = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("CONFERMA", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogData = false }) {
                    Text("Annulla", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (mostraConfermaAnnullaDialog) {
        AlertDialog(
            onDismissRequest = { mostraConfermaAnnullaDialog = false },
            title = { Text("Annullare Allenamento? ⚠️", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFE53935)) },
            text = {
                Text(
                    "Sei sicuro di voler annullare la sessione corrente? Tutti i dati inseriti e i progressi del timer andranno persi.",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostraConfermaAnnullaDialog = false
                        onAnnullaSessione()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("SÌ, ANNULLA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraConfermaAnnullaDialog = false }) {
                    Text("NO, CONTINUA", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (mostraCustomTimerDialog) {
        AlertDialog(
            onDismissRequest = { mostraCustomTimerDialog = false },
            title = { Text("Imposta Timer Personalizzato ⏱️", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Inserisci la durata del recupero in secondi:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputSecondiCustom,
                        onValueChange = { inputSecondiCustom = it },
                        label = { Text("Secondi") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sec = inputSecondiCustom.toIntOrNull() ?: 90
                        if (sec > 0) {
                            onAvviaTimer(sec)
                            mostraCustomTimerDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("AVVIA", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraCustomTimerDialog = false }) {
                    Text("Annulla", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
