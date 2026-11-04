package com.example.gymtracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.models.EsercizioEseguitoLog
import com.example.gymtracker.models.SchedaAllenamento
import com.example.gymtracker.models.SerieLog
import com.example.gymtracker.models.TipoSerie
import com.example.gymtracker.models.repsPerSerieEffettive
import com.example.gymtracker.data.WorkoutDraft
import com.example.gymtracker.data.WorkoutDraftStore
import com.example.gymtracker.services.WorkoutNotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(nomeUtente: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val bozzaIniziale = remember(nomeUtente) { WorkoutDraftStore.carica(context, nomeUtente) }
    var selectedScreen by remember { mutableStateOf(0) }
    var schedaInModifica by remember { mutableStateOf<SchedaAllenamento?>(null) }
    var mostraAiuto by remember { mutableStateOf(false) }

    var allenamentoInCorso by remember(nomeUtente) { mutableStateOf(bozzaIniziale?.scheda) }
    var idAllenamentoInCorso by remember(nomeUtente) {
        mutableStateOf(bozzaIniziale?.idAllenamento ?: UUID.randomUUID().toString())
    }
    var mostraSchermataAllenamento by remember(nomeUtente) { mutableStateOf(bozzaIniziale != null) }
    
    var dettagliEserciziInCorso = remember(nomeUtente) {
        mutableStateListOf<EsercizioEseguitoLog>().apply { addAll(bozzaIniziale?.dettagli.orEmpty()) }
    }

    var timestampInizioSessione by remember(nomeUtente) { mutableStateOf(bozzaIniziale?.iniziatoAlle ?: 0L) }
    var secondiTrascorsiCronometro by remember { mutableStateOf(0) }
    var dataAllenamento by remember(nomeUtente) {
        mutableStateOf(bozzaIniziale?.dataAllenamento?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(Date()))
    }
    var pesiDigitatiBozza by remember(nomeUtente) { mutableStateOf(bozzaIniziale?.pesiDigitati.orEmpty()) }
    var ripetizioniDigitateBozza by remember(nomeUtente) { mutableStateOf(bozzaIniziale?.ripetizioniDigitate.orEmpty()) }

    var timestampFineTimer by remember(nomeUtente) { mutableStateOf(bozzaIniziale?.timerFineMillis ?: 0L) }
    var secondiRimanentiTimer by remember(nomeUtente) {
        mutableStateOf(
            if (bozzaIniziale?.timerAttivo == true && bozzaIniziale.timerFineMillis > System.currentTimeMillis()) {
                ((bozzaIniziale.timerFineMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            } else bozzaIniziale?.secondiTimerRimanenti ?: 0
        )
    }
    var timerRecuperoAttivo by remember(nomeUtente) {
        mutableStateOf(bozzaIniziale?.timerAttivo == true && bozzaIniziale.timerFineMillis > System.currentTimeMillis())
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val eAdmin = nomeUtente.trim().equals("Luca", ignoreCase = true)
    val (titoloAiuto, righeAiuto) = when {
        mostraSchermataAllenamento && allenamentoInCorso != null -> "Allenamento in corso" to listOf(
            "Registra peso e ripetizioni per ogni serie; rispetta i target configurati nella scheda.",
            "Se un esercizio non viene svolto, segnalo come non fatto per poter completare la sessione.",
            "Avvia il timer di recupero tra le serie; cronometro e timer restano visibili anche minimizzando la schermata.",
            "Al termine salva e condividi la sessione nel Feed. Puoi annullarla se non vuoi conservarla."
        )
        selectedScreen == 0 -> "Allenati Ora" to listOf(
            "Scegli una scheda personale e avvia l’allenamento.",
            "Inserisci carico e ripetizioni per ciascuna serie; puoi modificare i target durante la sessione.",
            "Segna come non fatto un esercizio saltato. Usa il timer recupero tra le serie.",
            "Completa e condividi per salvare la sessione e renderla visibile nel Feed amici."
        )
        selectedScreen == 1 -> (if (schedaInModifica == null) "Le Mie Schede" else "Modifica scheda") to listOf(
            "Crea una scheda con titolo e scegli gli esercizi dal pool condiviso.",
            "Per ogni esercizio imposta serie, ripetizioni complessive o target diversi per serie, e note.",
            "Salva la scheda; dall’elenco puoi riaprirla per modificarla o eliminarla."
        )
        selectedScreen == 2 -> "Pool Esercizi" to listOf(
            "Cerca nel catalogo condiviso per nome e gruppo muscolare.",
            "Aggiungi un esercizio indicando nome e uno o più distretti muscolari.",
            "Puoi modificare le voci esistenti: gli esercizi del pool sono riutilizzabili nelle schede."
        )
        selectedScreen == 3 -> "Resoconto Personale" to listOf(
            "Scorri le settimane con Prec. e Succ.; il riepilogo include sessioni e volume.",
            "Esamina volume giornaliero e distribuzione per distretto muscolare.",
            "Consulta i migliori carichi; tocca un esercizio per aprirne il grafico dei progressi."
        )
        selectedScreen == 4 -> "Analisi Esercizi" to listOf(
            "Seleziona un esercizio per confrontare le prestazioni registrate dagli utenti.",
            "Le sessioni sono ordinate dalla più recente; carichi, serie e stato dell’esercizio aiutano il confronto.",
            "I dati dipendono dagli allenamenti salvati e condivisi."
        )
        selectedScreen == 5 -> "Community & Presenze" to listOf(
            "La mappa mostra le presenze del gruppo nel mese selezionato; cambia mese con Prec. e Succ.",
            "La classifica riassume sessioni e volume mensile degli utenti.",
            "Consulta anche i record per esercizio riferiti al mese visualizzato."
        )
        selectedScreen == 6 -> "Feed Amici" to listOf(
            "All’apertura vedi gli allenamenti degli ultimi 7 giorni; usa il filtro per consultare quelli precedenti.",
            "Tocca una sessione per vedere esercizi, serie, carichi e note.",
            "Puoi modificare o eliminare solo i tuoi allenamenti."
        )
        selectedScreen == 7 -> "Amici" to listOf(
            "Cerca una persona tramite nickname e inviale una richiesta di amicizia.",
            "Accetta o rifiuta le richieste ricevute; consulta l’elenco degli amici.",
            "La visibilità dei tuoi allenamenti nel Feed dipende dalle impostazioni di condivisione."
        )
        selectedScreen == 8 && eAdmin -> "Gestione Admin" to listOf(
            "Cerca gli utenti registrati e consulta il numero di allenamenti associati.",
            "L’eliminazione di un utente richiede conferma e password admin; rimuove i relativi dati."
        )
        selectedScreen == 9 && eAdmin -> "Peso corporeo" to listOf(
            "Il pulsante + apre l’inserimento: scegli la data dal calendario e registra peso e dati nutrizionali.",
            "La schermata iniziale riassume la settimana e mostra il grafico del peso del mese corrente.",
            "Nel confronto scegli due settimane: confronta peso medio, calorie, bilancio, proteine e variazione attesa.",
            "La perdita attesa è (calorie spese − calorie mangiate) ÷ 7.700; richiede entrambi i dati.",
            "Imposta una volta età, altezza e sesso in Metriche per stimare il metabolismo con Harris-Benedict."
        )
        else -> "GymTracker" to listOf("Apri il menu ☰ per scegliere una sezione.")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun aggiornaNotifica(cronStr: String, timerStr: String) {
        if (allenamentoInCorso != null) {
            val intent = Intent(context, WorkoutNotificationService::class.java).apply {
                action = WorkoutNotificationService.ACTION_START_OR_UPDATE
                putExtra(WorkoutNotificationService.EXTRA_CRONOMETRO_STR, cronStr)
                putExtra(WorkoutNotificationService.EXTRA_TIMER_STR, timerStr)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun fermaNotifica() {
        val intent = Intent(context, WorkoutNotificationService::class.java).apply {
            action = WorkoutNotificationService.ACTION_STOP
        }
        context.stopService(intent)
    }

    val minCron = secondiTrascorsiCronometro / 60
    val secCron = secondiTrascorsiCronometro % 60
    val cronometroStr = String.format("%02d:%02d", minCron, secCron)
    val timerStr = if (timerRecuperoAttivo) "${secondiRimanentiTimer}s" else if (secondiRimanentiTimer > 0) "Pausa (${secondiRimanentiTimer}s)" else "Fermo"

    LaunchedEffect(
        allenamentoInCorso,
        dettagliEserciziInCorso.toList(),
        timestampInizioSessione,
        dataAllenamento,
        pesiDigitatiBozza,
        ripetizioniDigitateBozza,
        timerRecuperoAttivo,
        timestampFineTimer,
        if (timerRecuperoAttivo) 0 else secondiRimanentiTimer
    ) {
        val scheda = allenamentoInCorso
        if (scheda != null && timestampInizioSessione > 0L) {
            WorkoutDraftStore.salva(
                context,
                WorkoutDraft(
                    nickname = nomeUtente,
                    idAllenamento = idAllenamentoInCorso,
                    scheda = scheda,
                    dettagli = dettagliEserciziInCorso.toList(),
                    iniziatoAlle = timestampInizioSessione,
                    dataAllenamento = dataAllenamento,
                    pesiDigitati = pesiDigitatiBozza,
                    ripetizioniDigitate = ripetizioniDigitateBozza,
                    timerFineMillis = timestampFineTimer,
                    secondiTimerRimanenti = secondiRimanentiTimer,
                    timerAttivo = timerRecuperoAttivo
                )
            )
        }
    }

    LaunchedEffect(allenamentoInCorso, timestampInizioSessione) {
        if (allenamentoInCorso != null && timestampInizioSessione > 0L) {
            while (allenamentoInCorso != null) {
                val diffSec = (System.currentTimeMillis() - timestampInizioSessione) / 1000L
                secondiTrascorsiCronometro = diffSec.toInt()
                
                val cStr = String.format("%02d:%02d", diffSec / 60, diffSec % 60)
                val tStr = if (timerRecuperoAttivo) "${secondiRimanentiTimer}s" else if (secondiRimanentiTimer > 0) "Pausa (${secondiRimanentiTimer}s)" else "Fermo"
                aggiornaNotifica(cStr, tStr)

                delay(1000L)
            }
        } else {
            fermaNotifica()
        }
    }

    LaunchedEffect(timerRecuperoAttivo, timestampFineTimer) {
        if (timerRecuperoAttivo) {
            while (timerRecuperoAttivo) {
                val rimanenti = (timestampFineTimer - System.currentTimeMillis()) / 1000L
                if (rimanenti <= 0) {
                    secondiRimanentiTimer = 0
                    timerRecuperoAttivo = false
                } else {
                    secondiRimanentiTimer = rimanenti.toInt()
                }
                delay(200L)
            }
        }
    }

    val menuItems = remember(eAdmin) {
        val lista = mutableListOf(
            Pair("Allenati Ora 🏋️‍♂️", "Seleziona e avvia la tua sessione"),
            Pair("Le Mie Schede 📋", "Crea o modifica le tue schede"),
            Pair("Pool Esercizi ➕", "Database condiviso degli esercizi"),
            Pair("Resoconto Personale 📅", "Record, carichi e volume della settimana"),
            Pair("Analisi Esercizi 📊", "Filtra e controlli i pesi degli utenti"),
            Pair("Community & Presenze 🏆", "Classifica e calendario presenze"),
            Pair("Feed Amici 🔥", "Guarda gli allenamenti del gruppo"),
            Pair("Amici 🤝", "Cerca utenti e gestisci le richieste")
        )

        if (eAdmin) {
            lista.add(Pair("Gestione Admin 👑", "Pannello riservato per eliminare utenti"))
            lista.add(Pair("Peso corporeo ⚖️", "Diario e progressi del peso"))
        }
        lista
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E1E1E),
                drawerContentColor = Color.White,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "GYM TRACKER",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (eAdmin) "Ciao, Luca! 👑 (Admin)" else "Ciao, $nomeUtente! 👋",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))

                menuItems.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(item.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(item.second, fontSize = 10.sp, color = Color.Gray)
                            }
                        },
                        selected = selectedScreen == index && !mostraSchermataAllenamento,
                        onClick = {
                            selectedScreen = index
                            mostraSchermataAllenamento = false
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = Color.DarkGray)

                TextButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚪", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Logout", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "GYM TRACKER",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { mostraAiuto = true }) {
                            Text("?", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (allenamentoInCorso != null && !mostraSchermataAllenamento) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { mostraSchermataAllenamento = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "🏋️‍♂️ Sessione: ${allenamentoInCorso!!.titoloScheda}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "⏱️ $cronometroStr • ⏳ $timerStr (Tocca per tornare)",
                                    color = Color(0xFF222222),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text("➔", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 18.sp)
                        }
                    }

                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (mostraSchermataAllenamento && allenamentoInCorso != null) {
                        SchermataEsecuzioneAllenamento(
                            nomeUtente = nomeUtente,
                            scheda = allenamentoInCorso!!,
                            idAllenamento = idAllenamentoInCorso,
                            dettagliList = dettagliEserciziInCorso,
                            secondiTrascorsi = secondiTrascorsiCronometro,
                            secondiTimer = secondiRimanentiTimer,
                            timerAttivo = timerRecuperoAttivo,
                            dataAllenamento = dataAllenamento,
                            onDataAllenamentoCambiata = { dataAllenamento = it },
                            pesiDigitatiIniziali = pesiDigitatiBozza,
                            ripetizioniDigitateIniziali = ripetizioniDigitateBozza,
                            riprendeBozza = bozzaIniziale != null,
                            onInputParzialiCambiati = { pesi, ripetizioni ->
                                pesiDigitatiBozza = pesi
                                ripetizioniDigitateBozza = ripetizioni
                            },
                            onAvviaTimer = { secondi ->
                                timestampFineTimer = System.currentTimeMillis() + (secondi * 1000L)
                                secondiRimanentiTimer = secondi
                                timerRecuperoAttivo = true
                            },
                            onPausaTimer = {
                                timerRecuperoAttivo = false
                            },
                            onRiprendiTimer = {
                                if (secondiRimanentiTimer > 0) {
                                    timestampFineTimer = System.currentTimeMillis() + (secondiRimanentiTimer * 1000L)
                                    timerRecuperoAttivo = true
                                }
                            },
                            onMinimizza = { mostraSchermataAllenamento = false },
                            onAnnullaSessione = {
                                WorkoutDraftStore.elimina(context, nomeUtente)
                                allenamentoInCorso = null
                                mostraSchermataAllenamento = false
                                dettagliEserciziInCorso.clear()
                                timestampInizioSessione = 0L
                                secondiTrascorsiCronometro = 0
                                timerRecuperoAttivo = false
                                secondiRimanentiTimer = 0
                                fermaNotifica()
                            },
                            onTerminato = {
                                WorkoutDraftStore.elimina(context, nomeUtente)
                                allenamentoInCorso = null
                                mostraSchermataAllenamento = false
                                dettagliEserciziInCorso.clear()
                                timestampInizioSessione = 0L
                                secondiTrascorsiCronometro = 0
                                timerRecuperoAttivo = false
                                secondiRimanentiTimer = 0
                                fermaNotifica()
                            }
                        )
                    } else {
                        when (selectedScreen) {
                            0 -> TabAllenatiOra(
                                nomeUtente = nomeUtente,
                                onAvviaAllenamento = { nuovaScheda ->
                                    allenamentoInCorso = nuovaScheda
                                    idAllenamentoInCorso = UUID.randomUUID().toString()
                                    dataAllenamento = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(Date())
                                    pesiDigitatiBozza = emptyMap()
                                    ripetizioniDigitateBozza = emptyMap()
                                    dettagliEserciziInCorso.clear()
                                    dettagliEserciziInCorso.addAll(
                                        nuovaScheda.esercizi.map { ex ->
                                            val numSerie = if (ex.serieTarget > 0) ex.serieTarget else 3
                                            val repsPerSerie = ex.repsPerSerieEffettive()
                                            val repsDef = repsPerSerie.firstOrNull()?.takeIf { it > 0 }
                                                ?: if (ex.repsTarget > 0) ex.repsTarget else 10
                                            val serieInit = (1..numSerie).map { n ->
                                                SerieLog(
                                                    numeroSerie = n,
                                                    pesoKg = 0.0,
                                                    reps = repsPerSerie.getOrNull(n - 1)?.takeIf { it > 0 } ?: repsDef,
                                                    tipo = TipoSerie.NORMALE
                                                )
                                            }
                                            EsercizioEseguitoLog(
                                                idEsercizio = ex.idEsercizio,
                                                nomeEsercizio = ex.nomeEsercizio,
                                                serieEseguite = numSerie,
                                                repsEseguite = repsDef,
                                                pesoKg = 0.0,
                                                noteEsercizio = ex.note,
                                                saltato = false,
                                                serieDettaglio = serieInit
                                            )
                                        }
                                    )
                                    mostraSchermataAllenamento = true
                                    timestampInizioSessione = System.currentTimeMillis()
                                    secondiTrascorsiCronometro = 0
                                    timerRecuperoAttivo = false
                                    secondiRimanentiTimer = 0
                                }
                            )
                            1 -> TabGestisciCreaScheda(
                                nomeUtente = nomeUtente,
                                schedaDaModificare = schedaInModifica,
                                onModificaCompletata = { schedaInModifica = null }
                            )
                            2 -> TabDatabaseEsercizi()
                            3 -> TabResocontoSettimanale(nomeUtente)
                            4 -> TabStatisticheEsercizi(nomeUtente)
                            5 -> TabCommunityDashboard(nomeUtente)
                            6 -> TabFeedAmici(nomeUtente)
                            7 -> TabAmici(nomeUtente)
                            8 -> TabGestioneAdmin()
                            9 -> if (eAdmin) TabPesoAdmin() else Unit
                        }
                    }

                    if (mostraAiuto) {
                        AlertDialog(
                            onDismissRequest = { mostraAiuto = false },
                            title = { Text("Aiuto · $titoloAiuto", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    righeAiuto.forEach { riga ->
                                        Text("• $riga", fontSize = 13.sp, color = Color.White)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { mostraAiuto = false }) {
                                    Text("Ho capito")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
