package com.example.gymtracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataEsecuzioneAllenamento(
    nomeUtente: String,
    scheda: SchedaAllenamento,
    dettagliList: MutableList<EsercizioEseguitoLog>,
    secondiTrascorsi: Int,
    secondiTimer: Int,
    timerAttivo: Boolean,
    onAvviaTimer: (Int) -> Unit,
    onPausaTimer: () -> Unit,
    onRiprendiTimer: () -> Unit,
    onMinimizza: () -> Unit,
    onAnnullaSessione: () -> Unit,
    onTerminato: () -> Unit
) {
    val context = LocalContext.current
    val pesiTestoMap = remember { mutableStateMapOf<Int, String>() }

    var ultimiLogMap by remember { mutableStateOf<Map<String, EsercizioEseguitoLog>>(emptyMap()) }

    var mostraCustomTimerDialog by remember { mutableStateOf(false) }
    var inputSecondiCustom by remember { mutableStateOf("90") }
    var mostraConfermaAnnullaDialog by remember { mutableStateOf(false) }

    // GESTIONE SELEZIONE DATA PERSONALIZZATA
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN) }
    var dataSelezionataStr by remember { mutableStateOf(dateFormat.format(Date())) }
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
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏋️‍♂️ Esecuzione: ${scheda.titoloScheda}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // PULSANTE PER SELEZIONARE LA DATA DI ESECUZIONE
            TextButton(onClick = { mostraDialogData = true }) {
                Text(
                    text = "📅 Data: $dataSelezionataStr",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
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
                            
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📌 Ultima volta: ${ultimoLog.pesoKg} Kg x ${ultimoLog.serieEseguite}s x ${ultimoLog.repsEseguite}r",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pesiTestoMap[index] ?: if (item.pesoKg == 0.0) "" else item.pesoKg.toString(),
                                    onValueChange = { text ->
                                        pesiTestoMap[index] = text
                                        val pesoParsed = text.replace(',', '.').toDoubleOrNull() ?: 0.0
                                        dettagliList[index] = item.copy(pesoKg = pesoParsed)
                                    },
                                    label = { Text("Peso (Kg)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = if (item.serieEseguite == 0) "" else item.serieEseguite.toString(),
                                    onValueChange = { text ->
                                        val s = text.toIntOrNull() ?: 0
                                        dettagliList[index] = item.copy(serieEseguite = s)
                                    },
                                    label = { Text("Serie") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = if (item.repsEseguite == 0) "" else item.repsEseguite.toString(),
                                    onValueChange = { text ->
                                        val r = text.toIntOrNull() ?: 0
                                        dettagliList[index] = item.copy(repsEseguite = r)
                                    },
                                    label = { Text("Reps") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
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
                val nonIncompilati = dettagliList.filter { !it.saltato && (it.pesoKg <= 0.0 || it.serieEseguite <= 0 || it.repsEseguite <= 0) }
                if (nonIncompilati.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Compila tutti i pesi, serie e reps per gli esercizi svolti oppure segna 'Non fatto'",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                val allenamentoCompletato = AllenamentoCompletato(
                    id = "",
                    nomeUtente = nomeUtente,
                    titoloScheda = scheda.titoloScheda,
                    data = dataSelezionataStr,
                    durataSecondi = secondiTrascorsi,
                    dettagliEsercizi = dettagliList.toList()
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
                        dataSelezionataStr = tempInputData
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
