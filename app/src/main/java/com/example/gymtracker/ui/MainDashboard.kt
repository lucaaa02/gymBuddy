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
import com.example.gymtracker.services.WorkoutNotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(nomeUtente: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    var selectedScreen by remember { mutableStateOf(0) }
    var schedaInModifica by remember { mutableStateOf<SchedaAllenamento?>(null) }

    var allenamentoInCorso by remember { mutableStateOf<SchedaAllenamento?>(null) }
    var mostraSchermataAllenamento by remember { mutableStateOf(false) }
    
    var dettagliEserciziInCorso = remember { mutableStateListOf<EsercizioEseguitoLog>() }

    var timestampInizioSessione by remember { mutableStateOf(0L) }
    var secondiTrascorsiCronometro by remember { mutableStateOf(0) }

    var timestampFineTimer by remember { mutableStateOf(0L) }
    var secondiRimanentiTimer by remember { mutableStateOf(0) }
    var timerRecuperoAttivo by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val eAdmin = nomeUtente.trim().equals("Luca", ignoreCase = true)

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
            Pair("Feed Amici 🔥", "Guarda gli allenamenti del gruppo")
        )

        if (eAdmin) {
            lista.add(Pair("Gestione Admin 👑", "Pannello riservato per eliminare utenti"))
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
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (mostraSchermataAllenamento && allenamentoInCorso != null) {
                        SchermataEsecuzioneAllenamento(
                            nomeUtente = nomeUtente,
                            scheda = allenamentoInCorso!!,
                            dettagliList = dettagliEserciziInCorso,
                            secondiTrascorsi = secondiTrascorsiCronometro,
                            secondiTimer = secondiRimanentiTimer,
                            timerAttivo = timerRecuperoAttivo,
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
                                    dettagliEserciziInCorso.clear()
                                    dettagliEserciziInCorso.addAll(
                                        nuovaScheda.esercizi.map { ex ->
                                            val numSerie = if (ex.serieTarget > 0) ex.serieTarget else 3
                                            val repsDef = if (ex.repsTarget > 0) ex.repsTarget else 10
                                            val serieInit = (1..numSerie).map { n ->
                                                SerieLog(
                                                    numeroSerie = n,
                                                    pesoKg = 0.0,
                                                    reps = repsDef,
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
                            4 -> TabStatisticheEsercizi()
                            5 -> TabCommunityDashboard(nomeUtente)
                            6 -> TabFeedAmici(nomeUtente)
                            7 -> TabGestioneAdmin()
                        }
                    }
                }
            }
        }
    }
}
