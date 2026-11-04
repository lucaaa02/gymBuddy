package com.example.gymtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.MisurazionePeso
import com.example.gymtracker.models.ParametriMetabolismo
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class PeriodoStatistiche(val titolo: String) {
    GIORNALIERE("Giorno"),
    SETTIMANALI("Settimana"),
    MENSILI("Mese")
}

private data class GruppoStatistiche(
    val etichetta: String,
    val misurazioni: List<MisurazionePeso>,
    val inizioPeriodo: LocalDate? = null
)

private val formatoDataPeso = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALIAN)
    .withResolverStyle(ResolverStyle.STRICT)
private val formatoChiavePeso = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
private fun TabPesoAdminLegacy() {
    var misurazioni by remember { mutableStateOf(emptyList<MisurazionePeso>()) }
    var erroreCaricamento by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf(LocalDate.now().format(formatoDataPeso)) }
    var peso by remember { mutableStateOf("") }
    var calorieAssunte by remember { mutableStateOf("") }
    var calorieOrologio by remember { mutableStateOf("") }
    var proteine by remember { mutableStateOf("") }
    var parametriMetabolismo by remember { mutableStateOf<ParametriMetabolismo?>(null) }
    var etaInput by remember { mutableStateOf("") }
    var altezzaInput by remember { mutableStateOf("") }
    var sessoInput by remember { mutableStateOf("") }
    var messaggioParametri by remember { mutableStateOf<String?>(null) }
    var erroreParametri by remember { mutableStateOf(false) }
    var salvataggioParametriInCorso by remember { mutableStateOf(false) }
    var mostraDialogParametri by remember { mutableStateOf(false) }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var erroreSalvataggio by remember { mutableStateOf(false) }
    var salvataggioInCorso by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaMisurazioniPeso(
            onUpdate = {
                misurazioni = it
                erroreCaricamento = null
            },
            onErrore = { erroreCaricamento = it }
        )
        FirebaseManager.ascoltaParametriMetabolismo(
            onUpdate = { parametri ->
                parametriMetabolismo = parametri
                if (parametri != null) {
                    etaInput = parametri.eta.toString()
                    altezzaInput = parametri.altezzaCm.toString()
                    sessoInput = parametri.sesso
                }
                erroreCaricamento = null
            },
            onErrore = { erroreCaricamento = it }
        )
    }

    val oggi = LocalDate.now()
    val inizioSettimana = oggi.with(java.time.DayOfWeek.MONDAY)
    val misurazioniSettimana = misurazioni.filter {
        runCatching { LocalDate.parse(it.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno ->
                it.pesoKg != null && !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi)
            } == true
    }
    val misurazioniMese = misurazioni.filter {
        runCatching { LocalDate.parse(it.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno ->
                it.pesoKg != null && giorno.year == oggi.year && giorno.month == oggi.month
            } == true
    }
    val misurazioniPeso = misurazioni.filter { it.pesoKg != null }
    val primaMisurazione = misurazioniPeso.firstOrNull()
    val ultimaMisurazione = misurazioniPeso.lastOrNull()
    val variazioneTotale = if (primaMisurazione != null && ultimaMisurazione != null) {
        ultimaMisurazione.pesoKg!! - primaMisurazione.pesoKg!!
    } else null
    val misurazioniNutrizioneSettimana = misurazioni.filter {
        runCatching { LocalDate.parse(it.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno -> !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi) } == true
    }
    val misurazioniNutrizioneMese = misurazioni.filter {
        runCatching { LocalDate.parse(it.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno -> giorno.year == oggi.year && giorno.month == oggi.month } == true
    }
    val mediaProteineGKgSettimanale = remember(misurazioni, inizioSettimana, oggi) {
        val pesiValidiSettimana = misurazioni.filter { misurazione ->
            val giorno = runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }.getOrNull()
            misurazione.pesoKg != null && giorno != null &&
                !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi)
        }.mapNotNull { it.pesoKg }
        val pesoMedio = pesiValidiSettimana.takeIf { it.isNotEmpty() }?.average()
        val medieProteineGiorno = misurazioniNutrizioneSettimana.mapNotNull { it.proteineGrammi }
        if (pesoMedio == null || medieProteineGiorno.isEmpty()) null
        else medieProteineGiorno.average() / pesoMedio
    }
    val statisticheGiornaliere = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.GIORNALIERE)
    }
    val statisticheSettimanali = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.SETTIMANALI)
    }
    val statisticheMensili = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.MENSILI)
    }
    val pesoMedioSettimanaleAttuale = remember(misurazioni, data, peso, inizioSettimana, oggi) {
        val misurazioniSettimanaConPeso = misurazioni.filter {
            runCatching { LocalDate.parse(it.data, formatoChiavePeso) }
                .getOrNull()?.let { giorno ->
                    it.pesoKg != null && !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi)
                } == true
        }
        val giornoInserito = runCatching { LocalDate.parse(data, formatoDataPeso) }.getOrNull()
        val pesoInserito = peso.replace(',', '.').toDoubleOrNull()
        if (
            giornoInserito != null &&
            !giornoInserito.isBefore(inizioSettimana) &&
            !giornoInserito.isAfter(oggi) &&
            pesoInserito != null && pesoInserito in 1.0..500.0
        ) {
            (misurazioniSettimanaConPeso.filter { it.data != giornoInserito.format(formatoChiavePeso) }
                .mapNotNull { it.pesoKg } + pesoInserito).average()
        } else {
            mediaPeso(misurazioniSettimanaConPeso)
        }
    }
    val parametriValidi = parametriMetabolismo
    val metabolismoBasaleStimato = pesoMedioSettimanaleAttuale?.let { pesoMedio ->
        parametriValidi?.let { metabolismoHarrisBenedict(pesoMedio, it) }
    }
    val calorieDaOrologio = calorieOrologio.toIntOrNull()
    val totaleCalorieSpeseStimato = if (metabolismoBasaleStimato != null && calorieDaOrologio != null) {
        (metabolismoBasaleStimato + calorieDaOrologio * 0.8).roundToInt()
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Diario del peso ⚖️", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Peso, nutrizione e progressi", fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = { mostraDialogParametri = true }) {
                    Text("⚙ Metriche", fontSize = 12.sp)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Nuova misurazione", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = data,
                        onValueChange = { data = it.filter { carattere -> carattere.isDigit() || carattere == '/' } },
                        label = { Text("Data (GG/MM/AAAA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = peso,
                        onValueChange = { peso = it.filter { carattere -> carattere.isDigit() || carattere == ',' || carattere == '.' } },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = calorieAssunte,
                        onValueChange = { calorieAssunte = it.filter(Char::isDigit) },
                        label = { Text("Calorie mangiate (kcal)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = calorieOrologio,
                        onValueChange = { calorieOrologio = it.filter(Char::isDigit) },
                        label = { Text("Calorie attive orologio (kcal)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = when {
                            calorieOrologio.isBlank() -> "Inserisci le calorie attive indicate dall'orologio."
                            parametriValidi == null ->
                                "Inserisci e salva una volta i parametri del metabolismo qui sopra."
                            metabolismoBasaleStimato == null ->
                                "Per calcolare il totale registra almeno un peso in questa settimana."
                            else ->
                                "Stima Harris-Benedict: metabolismo ${formattaPeso(metabolismoBasaleStimato)} + " +
                                    "(${calorieOrologio} × 0,8) = ${totaleCalorieSpeseStimato ?: "—"} kcal"
                        },
                        fontSize = 11.sp,
                        color = if (calorieOrologio.isNotBlank() && metabolismoBasaleStimato == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Gray
                        }
                    )
                    OutlinedTextField(
                        value = proteine,
                        onValueChange = { proteine = it.filter { carattere -> carattere.isDigit() || carattere == ',' || carattere == '.' } },
                        label = { Text("Proteine mangiate (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    messaggio?.let {
                        Text(
                            it,
                            color = if (erroreSalvataggio) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            val giorno = try {
                                LocalDate.parse(data, formatoDataPeso)
                            } catch (_: DateTimeParseException) {
                                messaggio = "Inserisci una data valida nel formato GG/MM/AAAA."
                                erroreSalvataggio = true
                                return@Button
                            }
                            if (giorno.isAfter(LocalDate.now())) {
                                messaggio = "La data non può essere futura."
                                erroreSalvataggio = true
                                return@Button
                            }
                            val valorePeso = peso.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
                            val calorieMangiate = calorieAssunte.takeIf { it.isNotBlank() }?.toIntOrNull()
                            val calorieWatch = calorieOrologio.takeIf { it.isNotBlank() }?.toIntOrNull()
                            val proteineGrammi = proteine.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
                            if (
                                (peso.isNotBlank() && (valorePeso == null || !valorePeso.isFinite() || valorePeso !in 1.0..500.0)) ||
                                (calorieAssunte.isNotBlank() && (calorieMangiate == null || calorieMangiate !in 0..20_000)) ||
                                (calorieOrologio.isNotBlank() && (calorieWatch == null || calorieWatch !in 0..20_000)) ||
                                (proteine.isNotBlank() && (proteineGrammi == null || !proteineGrammi.isFinite() || proteineGrammi !in 0.0..1_000.0))
                            ) {
                                messaggio = "Controlla i valori: peso 1–500 kg, calorie 0–20.000, proteine 0–1.000 g."
                                erroreSalvataggio = true
                                return@Button
                            }
                            if (valorePeso == null && calorieMangiate == null && calorieWatch == null && proteineGrammi == null) {
                                messaggio = "Inserisci almeno un dato per questa giornata."
                                erroreSalvataggio = true
                                return@Button
                            }
                            val pesoMedioUsato = if (
                                !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi) && valorePeso != null
                            ) {
                                (misurazioniSettimana.filter { it.data != giorno.format(formatoChiavePeso) }
                                    .mapNotNull { it.pesoKg } + valorePeso).average()
                            } else {
                                pesoMedioSettimanaleAttuale
                            }
                            if (calorieWatch != null && (pesoMedioUsato == null || parametriValidi == null)) {
                                messaggio = if (parametriValidi == null) {
                                    "Inserisci e salva i parametri del metabolismo prima delle calorie dell'orologio."
                                } else {
                                    "Registra almeno un peso nella settimana corrente prima delle calorie dell'orologio."
                                }
                                erroreSalvataggio = true
                                return@Button
                            }
                            val metabolismoUsato = pesoMedioUsato?.let { pesoMedio ->
                                parametriValidi?.let { metabolismoHarrisBenedict(pesoMedio, it) }
                            }
                            val calorieSpeseTotali = calorieWatch?.let { watch ->
                                metabolismoUsato?.plus(watch * 0.8)?.roundToInt()
                            }

                            salvataggioInCorso = true
                            messaggio = null
                            FirebaseManager.salvaMisurazionePeso(
                                MisurazionePeso(
                                    data = giorno.format(formatoChiavePeso),
                                    pesoKg = valorePeso,
                                    calorieAssunte = calorieMangiate,
                                    calorieBruciate = calorieSpeseTotali,
                                    calorieOrologio = calorieWatch,
                                    metabolismoBasaleKcal = metabolismoUsato,
                                    pesoMedioSettimanaleUsatoKg = pesoMedioUsato?.takeIf { calorieWatch != null },
                                    proteineGrammi = proteineGrammi,
                                    aggiornatoAlle = System.currentTimeMillis()
                                )
                            ) { errore ->
                                salvataggioInCorso = false
                                erroreSalvataggio = errore != null
                                messaggio = errore ?: "Misurazione salvata."
                                if (errore == null) {
                                    peso = ""
                                    calorieAssunte = ""
                                    calorieOrologio = ""
                                    proteine = ""
                                }
                            }
                        },
                        enabled = !salvataggioInCorso,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (salvataggioInCorso) "SALVATAGGIO..." else "SALVA MISURAZIONE", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Stima calorie spese = metabolismo basale Harris-Benedict rivista + 80% delle calorie attive dell'orologio. I dati già salvati restano invariati se lasci un campo vuoto.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        erroreCaricamento?.let { errore ->
            item {
                Text("Impossibile caricare le misurazioni: $errore", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPesoCard(
                    titolo = "Media settimana",
                    valore = mediaPeso(misurazioniSettimana),
                    dettaglio = "${misurazioniSettimana.size} misurazioni",
                    modifier = Modifier.weight(1f)
                )
                StatPesoCard(
                    titolo = "Media mese",
                    valore = mediaPeso(misurazioniMese),
                    dettaglio = "${misurazioniMese.size} misurazioni",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Proteine per peso corporeo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        mediaProteineGKgSettimanale?.let { "${formattaPeso(it)} g/kg al giorno" }
                            ?: "Registra peso e proteine nella settimana per calcolare il dato.",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Media delle proteine giornaliere divisa per il peso medio settimanale.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Nutrizione media giornaliera", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Periodo", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray)
                        Text("Settimana", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray)
                        Text("Mese", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray)
                    }
                    RigaMediaNutrizionale(
                        titolo = "Calorie mangiate",
                        settimana = mediaNutrizionale(misurazioniNutrizioneSettimana) { it.calorieAssunte?.toDouble() },
                        mese = mediaNutrizionale(misurazioniNutrizioneMese) { it.calorieAssunte?.toDouble() },
                        unita = "kcal"
                    )
                    RigaMediaNutrizionale(
                        titolo = "Spesa totale stimata",
                        settimana = mediaNutrizionale(misurazioniNutrizioneSettimana) { it.calorieBruciate?.toDouble() },
                        mese = mediaNutrizionale(misurazioniNutrizioneMese) { it.calorieBruciate?.toDouble() },
                        unita = "kcal"
                    )
                    RigaMediaNutrizionale(
                        titolo = "Calorie orologio",
                        settimana = mediaNutrizionale(misurazioniNutrizioneSettimana) { it.calorieOrologio?.toDouble() },
                        mese = mediaNutrizionale(misurazioniNutrizioneMese) { it.calorieOrologio?.toDouble() },
                        unita = "kcal"
                    )
                    RigaMediaNutrizionale(
                        titolo = "Proteine",
                        settimana = mediaNutrizionale(misurazioniNutrizioneSettimana) { it.proteineGrammi },
                        mese = mediaNutrizionale(misurazioniNutrizioneMese) { it.proteineGrammi },
                        unita = "g"
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Progressi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = ultimaMisurazione?.let { ultima ->
                            when {
                                variazioneTotale == null || primaMisurazione == null ->
                                    "Peso attuale: ${formattaPeso(ultima.pesoKg!!)} kg"
                                else -> {
                                    val segno = if (variazioneTotale > 0) "+" else ""
                                    "Da ${formatoDataPeso.format(LocalDate.parse(primaMisurazione.data, formatoChiavePeso))}: " +
                                        "$segno${formattaPeso(variazioneTotale)} kg • Ultimo: ${formattaPeso(ultima.pesoKg!!)} kg"
                                }
                            }
                        } ?: "Aggiungi la prima misurazione per iniziare.",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    ultimaMisurazione?.let {
                        Text(
                            "Ultima registrazione: ${formatoDataPeso.format(LocalDate.parse(it.data, formatoChiavePeso))}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        if (misurazioniPeso.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Andamento del peso", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        GraficoAndamentoPeso(misurazioniPeso)
                    }
                }
            }
        }

        item { TitoloSezioneStatistiche("Statistiche giornaliere", "Valori registrati per ciascun giorno") }
        if (statisticheGiornaliere.isEmpty()) {
            item { TestoStatisticheVuote() }
        } else {
            items(statisticheGiornaliere, key = { "giorno-${it.etichetta}" }) { gruppo ->
                DettaglioStatisticheCard(gruppo, PeriodoStatistiche.GIORNALIERE)
            }
        }

        item { TitoloSezioneStatistiche("Statistiche settimanali", "Settimane da lunedì a domenica") }
        if (statisticheSettimanali.isEmpty()) {
            item { TestoStatisticheVuote() }
        } else {
            items(statisticheSettimanali, key = { "settimana-${it.etichetta}" }) { gruppo ->
                DettaglioStatisticheCard(gruppo, PeriodoStatistiche.SETTIMANALI)
            }
        }

        item { TitoloSezioneStatistiche("Statistiche mensili", "Riepilogo per mese") }
        if (statisticheMensili.isEmpty()) {
            item { TestoStatisticheVuote() }
        } else {
            items(statisticheMensili, key = { "mese-${it.etichetta}" }) { gruppo ->
                DettaglioStatisticheCard(gruppo, PeriodoStatistiche.MENSILI)
            }
        }
    }

    if (mostraDialogParametri) {
        AlertDialog(
            onDismissRequest = { mostraDialogParametri = false },
            title = { Text("Parametri metabolismo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Inseriscili una volta: saranno riutilizzati nei calcoli di ogni giornata.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = etaInput,
                        onValueChange = { etaInput = it.filter(Char::isDigit) },
                        label = { Text("Età (anni)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = altezzaInput,
                        onValueChange = { altezzaInput = it.filter(Char::isDigit) },
                        label = { Text("Altezza (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sessoInput == "Uomo",
                            onClick = { sessoInput = "Uomo" },
                            label = { Text("Uomo") }
                        )
                        FilterChip(
                            selected = sessoInput == "Donna",
                            onClick = { sessoInput = "Donna" },
                            label = { Text("Donna") }
                        )
                    }
                    messaggioParametri?.let {
                        Text(
                            it,
                            color = if (erroreParametri) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val eta = etaInput.toIntOrNull()
                        val altezza = altezzaInput.toIntOrNull()
                        if (
                            eta == null || eta !in 18..100 ||
                            altezza == null || altezza !in 100..250 ||
                            sessoInput !in setOf("Uomo", "Donna")
                        ) {
                            messaggioParametri = "Inserisci età (18–100), altezza (100–250 cm) e sesso."
                            erroreParametri = true
                            return@Button
                        }
                        salvataggioParametriInCorso = true
                        messaggioParametri = null
                        FirebaseManager.salvaParametriMetabolismo(
                            ParametriMetabolismo(
                                eta = eta,
                                altezzaCm = altezza,
                                sesso = sessoInput,
                                aggiornatoAlle = System.currentTimeMillis()
                            )
                        ) { errore ->
                            salvataggioParametriInCorso = false
                            erroreParametri = errore != null
                            messaggioParametri = errore ?: "Parametri salvati."
                            if (errore == null) {
                                parametriMetabolismo = ParametriMetabolismo(
                                    eta = eta,
                                    altezzaCm = altezza,
                                    sesso = sessoInput,
                                    aggiornatoAlle = System.currentTimeMillis()
                                )
                                mostraDialogParametri = false
                            }
                        }
                    },
                    enabled = !salvataggioParametriInCorso
                ) {
                    Text(if (salvataggioParametriInCorso) "SALVATAGGIO..." else "SALVA")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogParametri = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

private fun raggruppaStatistiche(
    misurazioni: List<MisurazionePeso>,
    periodo: PeriodoStatistiche
): List<GruppoStatistiche> {
    val misurazioniConData = misurazioni.mapNotNull { misurazione ->
        runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno -> giorno to misurazione }
    }

    return when (periodo) {
        PeriodoStatistiche.GIORNALIERE -> misurazioniConData
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it })
            .map { (giorno, records) ->
                GruppoStatistiche(formatoDataPeso.format(giorno), records, giorno)
            }

        PeriodoStatistiche.SETTIMANALI -> misurazioniConData
            .groupBy({ it.first.with(java.time.DayOfWeek.MONDAY) }, { it.second })
            .toSortedMap(compareByDescending { it })
            .map { (inizio, records) ->
                val fine = inizio.plusDays(6)
                GruppoStatistiche(
                    "${formatoDataPeso.format(inizio)} - ${formatoDataPeso.format(fine)}",
                    records,
                    inizio
                )
            }

        PeriodoStatistiche.MENSILI -> misurazioniConData
            .groupBy({ YearMonth.from(it.first) }, { it.second })
            .toSortedMap(compareByDescending { it })
            .map { (mese, records) ->
                GruppoStatistiche(
                    mese.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
                        .replaceFirstChar { it.uppercase(Locale.ITALIAN) },
                    records,
                    mese.atDay(1)
                )
            }
    }
}

@Composable
private fun TitoloSezioneStatistiche(titolo: String, descrizione: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titolo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(descrizione, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun TestoStatisticheVuote() {
    Text("Non ci sono ancora dati da riepilogare.", color = Color.Gray, fontSize = 12.sp)
}

@Composable
private fun DettaglioStatisticheCard(
    gruppo: GruppoStatistiche,
    periodo: PeriodoStatistiche,
    gruppiPeriodo: List<GruppoStatistiche> = emptyList()
) {
    val records = gruppo.misurazioni
    val pesi = records.mapNotNull { it.pesoKg }
    val proteinePerKg = records.mapNotNull { record ->
        val proteine = record.proteineGrammi
        val peso = record.pesoKg
        if (proteine != null && peso != null && peso > 0.0) proteine / peso else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(gruppo.etichetta, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                "${records.size} giorni registrati • ${pesi.size} misurazioni di peso",
                fontSize = 10.sp,
                color = Color.Gray
            )
            RowStatistiche(
                etichetta = "Dato",
                valoreMedio = if (periodo == PeriodoStatistiche.GIORNALIERE) "Valore" else "Media/g",
                valoreTotale = if (periodo == PeriodoStatistiche.GIORNALIERE) "" else "Totale",
                intestazione = true
            )
            RigaStatisticaDettagliata(
                "Peso medio",
                mediaPeso(records),
                null,
                "kg",
                periodo
            )
            val pesoMinimo = pesoMin(records)
            val pesoMassimo = records.mapNotNull { it.pesoKg }.maxOrNull()
            if (pesoMinimo != null && pesoMassimo != null && pesoMinimo != pesoMassimo) {
                RigaIntervalloPeso(pesoMinimo, pesoMassimo)
            }
            RigaStatisticaDettagliata(
                "Calorie mangiate",
                mediaNutrizionale(records) { it.calorieAssunte?.toDouble() },
                sommaNutrizionale(records) { it.calorieAssunte?.toDouble() },
                "kcal",
                periodo
            )
            RigaStatisticaDettagliata(
                "Spesa totale stimata",
                mediaNutrizionale(records) { it.calorieBruciate?.toDouble() },
                sommaNutrizionale(records) { it.calorieBruciate?.toDouble() },
                "kcal",
                periodo
            )
            RigaStatisticaDettagliata(
                "Calorie attive orologio",
                mediaNutrizionale(records) { it.calorieOrologio?.toDouble() },
                sommaNutrizionale(records) { it.calorieOrologio?.toDouble() },
                "kcal",
                periodo
            )
            RigaStatisticaDettagliata(
                "Proteine",
                mediaNutrizionale(records) { it.proteineGrammi },
                sommaNutrizionale(records) { it.proteineGrammi },
                "g",
                periodo
            )
            RigaStatisticaDettagliata(
                "Proteine per peso",
                proteinePerKg.takeIf { it.isNotEmpty() }?.average(),
                null,
                "g/kg",
                periodo
            )
            stimaVariazionePeso(records)?.let { stima ->
                RigaStatisticaDettagliata(
                    "Peso atteso",
                    stima,
                    null,
                    "kg",
                    periodo
                )
                if (periodo != PeriodoStatistiche.GIORNALIERE) {
                    val inizioPrecedente = when (periodo) {
                        PeriodoStatistiche.SETTIMANALI -> gruppo.inizioPeriodo?.minusWeeks(1)
                        PeriodoStatistiche.MENSILI -> gruppo.inizioPeriodo?.minusMonths(1)
                        PeriodoStatistiche.GIORNALIERE -> null
                    }
                    val gruppoPrecedente = inizioPrecedente?.let { inizio ->
                        val chiavePeriodo = if (periodo == PeriodoStatistiche.SETTIMANALI) inizio
                        else inizio.withDayOfMonth(1)
                        gruppiPeriodo.find { it.inizioPeriodo == chiavePeriodo }
                    }
                    gruppoPrecedente?.let { precedente ->
                        val differenzaPesoMedio = mediaPeso(records)?.let { mediaAttuale ->
                            mediaPeso(precedente.misurazioni)?.let { mediaAttuale - it }
                        }
                        differenzaPesoMedio?.let {
                            RigaStatisticaDettagliata(
                                "Differenza peso medio",
                                it,
                                null,
                                "kg",
                                PeriodoStatistiche.GIORNALIERE
                            )
                        }
                        stimaVariazionePeso(precedente.misurazioni)?.let { stimaPrecedente ->
                            RigaStatisticaDettagliata(
                                "Diff. perdita attesa",
                                differenzaStimaPeso(stima, stimaPrecedente),
                                null,
                                "kg",
                                PeriodoStatistiche.GIORNALIERE
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RigaIntervalloPeso(minimo: Double, massimo: Double) {
    RowStatistiche(
        etichetta = "Intervallo peso",
        valoreMedio = "",
        valoreTotale = "${formattaValoreStatistica(minimo)}–${formattaValoreStatistica(massimo)} kg"
    )
}

@Composable
private fun RigaStatisticaDettagliata(
    titolo: String,
    media: Double?,
    totale: Double?,
    unita: String,
    periodo: PeriodoStatistiche
) {
    if (media == null) return
    RowStatistiche(
        etichetta = titolo,
        valoreMedio = "${formattaValoreStatistica(media)} $unita",
        valoreTotale = if (periodo == PeriodoStatistiche.GIORNALIERE || totale == null) {
            ""
        } else {
            "${formattaValoreStatistica(totale)} $unita"
        }
    )
}

@Composable
private fun RowStatistiche(
    etichetta: String,
    valoreMedio: String,
    valoreTotale: String,
    intestazione: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            etichetta,
            modifier = Modifier.weight(1.25f),
            fontSize = if (intestazione) 9.sp else 10.sp,
            fontWeight = if (intestazione) FontWeight.Bold else FontWeight.Normal,
            color = if (intestazione) Color.Gray else Color.White
        )
        Text(
            valoreMedio,
            modifier = Modifier.weight(0.9f),
            fontSize = if (intestazione) 9.sp else 10.sp,
            fontWeight = if (intestazione) FontWeight.Bold else FontWeight.Medium,
            color = if (intestazione) Color.Gray else MaterialTheme.colorScheme.secondary
        )
        Text(
            valoreTotale,
            modifier = Modifier.weight(0.9f),
            fontSize = if (intestazione) 9.sp else 10.sp,
            fontWeight = if (intestazione) FontWeight.Bold else FontWeight.Medium,
            color = if (intestazione) Color.Gray else MaterialTheme.colorScheme.secondary
        )
    }
}

private fun sommaNutrizionale(
    misurazioni: List<MisurazionePeso>,
    selettore: (MisurazionePeso) -> Double?
): Double? = misurazioni.mapNotNull(selettore).takeIf { it.isNotEmpty() }?.sum()

private fun pesoMin(misurazioni: List<MisurazionePeso>): Double? =
    misurazioni.mapNotNull { it.pesoKg }.takeIf { it.isNotEmpty() }?.minOrNull()

private fun formattaValoreStatistica(valore: Double): String =
    String.format(Locale.ITALIAN, if (valore % 1.0 == 0.0) "%.0f" else "%.1f", valore)

private fun stimaVariazionePeso(misurazioni: List<MisurazionePeso>): Double? {
    val bilanciCalorici = misurazioni.mapNotNull { misurazione ->
        val calorieAssunte = misurazione.calorieAssunte?.toDouble()
        val calorieSpese = misurazione.calorieBruciate?.toDouble()
        if (calorieAssunte != null && calorieSpese != null) calorieSpese - calorieAssunte else null
    }
    return bilanciCalorici.takeIf { it.isNotEmpty() }?.sum()?.div(7_700.0)
}

private fun bilancioCaloricoMedio(misurazioni: List<MisurazionePeso>): Double? =
    misurazioni.mapNotNull { misurazione ->
        val calorieAssunte = misurazione.calorieAssunte?.toDouble()
        val calorieSpese = misurazione.calorieBruciate?.toDouble()
        if (calorieAssunte != null && calorieSpese != null) calorieSpese - calorieAssunte else null
    }.takeIf { it.isNotEmpty() }?.average()

private fun differenzaStimaPeso(attuale: Double?, precedente: Double?): Double? =
    if (attuale != null && precedente != null) attuale - precedente else null

private fun formattaVariazionePeso(valore: Double?): String = when {
    valore == null -> "— (dati insufficienti)"
    valore > 0.0 -> "${formattaValoreStatistica(valore)} kg persi"
    valore < 0.0 -> "${formattaValoreStatistica(kotlin.math.abs(valore))} kg di aumento"
    else -> "0 kg"
}

private fun formattaVariazionePesoDifferenza(valore: Double?): String = when {
    valore == null -> "—"
    valore > 0.0 -> "+${formattaValoreStatistica(valore)} kg di perdita attesa"
    valore < 0.0 -> "${formattaValoreStatistica(valore)} kg di perdita attesa"
    else -> "nessuna variazione"
}

private fun finePeriodoSettimana(
    inizioSettimana: LocalDate,
    settimanaCorrente: LocalDate,
    oggi: LocalDate
): LocalDate {
    val domenica = inizioSettimana.plusDays(6)
    return if (inizioSettimana == settimanaCorrente) minOf(domenica, oggi) else domenica
}

private fun formattaDifferenzaPesoReale(valore: Double): String {
    val segno = if (valore > 0.0) "+" else ""
    return "$segno${formattaValoreStatistica(valore)} kg"
}

@Composable
private fun StatPesoCard(titolo: String, valore: Double?, dettaglio: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(titolo, fontSize = 11.sp, color = Color.Gray)
            Text(valore?.let { "${formattaPeso(it)} kg" } ?: "—", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(dettaglio, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun GraficoAndamentoPeso(misurazioni: List<MisurazionePeso>) {
    val coloreLinea = MaterialTheme.colorScheme.primary
    val colorePunto = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val margine = 20.dp.toPx()
        val larghezza = size.width - margine * 2
        val altezza = size.height - margine * 2
        val misurazioniConPeso = misurazioni
            .filter { it.pesoKg != null }
            .sortedBy { it.data }
        val pesi = misurazioniConPeso.mapNotNull { it.pesoKg }
        val minPeso = pesi.minOrNull() ?: return@Canvas
        val maxPeso = pesi.maxOrNull() ?: return@Canvas
        val intervallo = (maxPeso - minPeso).takeIf { it > 0.0 } ?: 1.0
        val punti = misurazioniConPeso.mapIndexed { index, misurazione ->
            val x = if (misurazioniConPeso.size == 1) size.width / 2f
            else margine + larghezza * index / (misurazioniConPeso.size - 1)
            val pesoMisurato = misurazione.pesoKg ?: return@mapIndexed Offset.Zero
            val y = margine + altezza * (1f - ((pesoMisurato - minPeso) / intervallo).toFloat())
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(punti.first().x, punti.first().y)
            punti.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, coloreLinea, style = Stroke(width = 3.dp.toPx()))
        punti.forEach { punto ->
            drawCircle(colorePunto, radius = 5.dp.toPx(), center = punto)
        }
    }
    val misurazioniConPeso = misurazioni
        .filter { it.pesoKg != null }
        .sortedBy { it.data }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            misurazioniConPeso.firstOrNull()?.let { formatoDataPeso.format(LocalDate.parse(it.data, formatoChiavePeso)) }
                ?: "—",
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text("${misurazioniConPeso.size} rilevazioni", fontSize = 10.sp, color = Color.Gray)
        Text(
            misurazioniConPeso.lastOrNull()?.let { formatoDataPeso.format(LocalDate.parse(it.data, formatoChiavePeso)) }
                ?: "—",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "Min ${formattaPeso(misurazioniConPeso.mapNotNull { it.pesoKg }.minOrNull() ?: 0.0)} kg",
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            "Max ${formattaPeso(misurazioniConPeso.mapNotNull { it.pesoKg }.maxOrNull() ?: 0.0)} kg",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

private fun mediaPeso(misurazioni: List<MisurazionePeso>): Double? =
    misurazioni.mapNotNull { it.pesoKg }.takeIf { it.isNotEmpty() }?.average()

private fun mediaNutrizionale(
    misurazioni: List<MisurazionePeso>,
    selettore: (MisurazionePeso) -> Double?
): Double? = misurazioni.mapNotNull(selettore).takeIf { it.isNotEmpty() }?.average()

private fun metabolismoHarrisBenedict(pesoMedioKg: Double, parametri: ParametriMetabolismo): Double {
    return when (parametri.sesso) {
        "Uomo" -> 88.362 + 13.397 * pesoMedioKg +
            4.799 * parametri.altezzaCm - 5.677 * parametri.eta
        "Donna" -> 447.593 + 9.247 * pesoMedioKg +
            3.098 * parametri.altezzaCm - 4.330 * parametri.eta
        else -> 0.0
    }
}

@Composable
private fun RigaMediaNutrizionale(titolo: String, settimana: Double?, mese: Double?, unita: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titolo, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.White)
        Text(
            settimana?.let { "${formattaPeso(it)} $unita" } ?: "—",
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            mese?.let { "${formattaPeso(it)} $unita" } ?: "—",
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun formattaPeso(peso: Double): String = String.format(Locale.ITALIAN, "%.1f", peso)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TabPesoAdmin() {
    var misurazioni by remember { mutableStateOf(emptyList<MisurazionePeso>()) }
    var erroreCaricamento by remember { mutableStateOf<String?>(null) }
    var parametriMetabolismo by remember { mutableStateOf<ParametriMetabolismo?>(null) }
    var etaInput by remember { mutableStateOf("") }
    var altezzaInput by remember { mutableStateOf("") }
    var sessoInput by remember { mutableStateOf("") }
    var mostraDialogParametri by remember { mutableStateOf(false) }
    var messaggioParametri by remember { mutableStateOf<String?>(null) }
    var erroreParametri by remember { mutableStateOf(false) }
    var salvataggioParametriInCorso by remember { mutableStateOf(false) }
    var schermata by remember { mutableStateOf<PeriodoStatistiche?>(null) }
    var settimanaConfrontoA by remember {
        mutableStateOf(LocalDate.now().with(java.time.DayOfWeek.MONDAY))
    }
    var settimanaConfrontoB by remember {
        mutableStateOf(LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1))
    }
    var mostraForm by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(LocalDate.now().format(formatoDataPeso)) }
    var peso by remember { mutableStateOf("") }
    var calorieAssunte by remember { mutableStateOf("") }
    var calorieOrologio by remember { mutableStateOf("") }
    var proteine by remember { mutableStateOf("") }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var erroreSalvataggio by remember { mutableStateOf(false) }
    var salvataggioInCorso by remember { mutableStateOf(false) }

    BackHandler(enabled = schermata != null) {
        schermata = null
    }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaMisurazioniPeso(
            onUpdate = {
                misurazioni = it
                erroreCaricamento = null
            },
            onErrore = { erroreCaricamento = it }
        )
        FirebaseManager.ascoltaParametriMetabolismo(
            onUpdate = { parametri ->
                parametriMetabolismo = parametri
                if (parametri != null) {
                    etaInput = parametri.eta.toString()
                    altezzaInput = parametri.altezzaCm.toString()
                    sessoInput = parametri.sesso
                }
                erroreCaricamento = null
            },
            onErrore = { erroreCaricamento = it }
        )
    }

    val oggi = LocalDate.now()
    val inizioSettimana = oggi.with(java.time.DayOfWeek.MONDAY)
    val misurazioniSettimana = misurazioni.filter { misurazione ->
        runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno ->
                !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi)
            } == true
    }
    val misurazioniSettimanaConPeso = misurazioniSettimana.filter { it.pesoKg != null }
    val mediaPesoSettimanale = mediaPeso(misurazioniSettimanaConPeso)
    val mediaProteineGKgSettimanale = mediaPesoSettimanale?.let { pesoMedio ->
        mediaNutrizionale(misurazioniSettimana) { it.proteineGrammi }?.div(pesoMedio)
    }
    val statisticheGiornaliere = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.GIORNALIERE)
    }
    val statisticheSettimanali = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.SETTIMANALI)
    }
    val statisticheMensili = remember(misurazioni) {
        raggruppaStatistiche(misurazioni, PeriodoStatistiche.MENSILI)
    }
    val settimaneSelezionabili = remember(misurazioni, inizioSettimana) {
        val primeDate = misurazioni.mapNotNull { misurazione ->
            runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }.getOrNull()
        }
        val primaSettimana = primeDate.minOrNull()
            ?.with(java.time.DayOfWeek.MONDAY)
            ?: inizioSettimana.minusWeeks(1)
        buildList {
            add(inizioSettimana)
            add(inizioSettimana.minusWeeks(1))
            var settimana = inizioSettimana
            while (!settimana.isBefore(primaSettimana)) {
                add(settimana)
                settimana = settimana.minusWeeks(1)
            }
        }.distinct().sortedDescending()
    }
    LaunchedEffect(settimaneSelezionabili) {
        if (settimanaConfrontoA !in settimaneSelezionabili) {
            settimanaConfrontoA = settimaneSelezionabili.first()
        }
        if (settimanaConfrontoB !in settimaneSelezionabili) {
            settimanaConfrontoB = settimaneSelezionabili.getOrElse(1) {
                settimaneSelezionabili.first()
            }
        }
    }
    val misurazioniSettimanaA = misurazioni.filter { misurazione ->
        runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno ->
                val fineSettimana = settimanaConfrontoA.plusDays(6)
                    .let { fine -> if (settimanaConfrontoA == inizioSettimana) minOf(fine, oggi) else fine }
                !giorno.isBefore(settimanaConfrontoA) && !giorno.isAfter(fineSettimana)
            } == true
    }
    val misurazioniSettimanaB = misurazioni.filter { misurazione ->
        runCatching { LocalDate.parse(misurazione.data, formatoChiavePeso) }
            .getOrNull()?.let { giorno ->
                val fineSettimana = settimanaConfrontoB.plusDays(6)
                    .let { fine -> if (settimanaConfrontoB == inizioSettimana) minOf(fine, oggi) else fine }
                !giorno.isBefore(settimanaConfrontoB) && !giorno.isAfter(fineSettimana)
            } == true
    }
    val misurazioniPesoMese = misurazioni.filter { misurazione ->
        misurazione.pesoKg != null && runCatching {
            YearMonth.from(LocalDate.parse(misurazione.data, formatoChiavePeso)) == YearMonth.from(oggi)
        }.getOrDefault(false)
    }
    val giornoInserito = runCatching { LocalDate.parse(data, formatoDataPeso) }.getOrNull()
    val pesoInserito = peso.replace(',', '.').toDoubleOrNull()
    val pesoMedioPerStima = if (
        giornoInserito != null &&
        !giornoInserito.isBefore(inizioSettimana) &&
        !giornoInserito.isAfter(oggi) &&
        pesoInserito != null && pesoInserito in 1.0..500.0
    ) {
        (misurazioniSettimanaConPeso.filter {
            it.data != giornoInserito.format(formatoChiavePeso)
        }.mapNotNull { it.pesoKg } + pesoInserito).average()
    } else {
        mediaPesoSettimanale
    }
    val metabolismoStimato = pesoMedioPerStima?.let { media ->
        parametriMetabolismo?.let { metabolismoHarrisBenedict(media, it) }
    }
    val totaleCalorieStimato = calorieOrologio.toIntOrNull()?.let { calorie ->
        metabolismoStimato?.plus(calorie * 0.8)?.roundToInt()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    data = LocalDate.now().format(formatoDataPeso)
                    peso = ""
                    calorieAssunte = ""
                    calorieOrologio = ""
                    proteine = ""
                    messaggio = null
                    erroreSalvataggio = false
                    mostraForm = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Text("+", fontSize = 28.sp)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (schermata != null) {
                            TextButton(onClick = { schermata = null }, contentPadding = PaddingValues(0.dp)) {
                                Text("‹ Riepilogo")
                            }
                        }
                        Text(
                            schermata?.let {
                                when (it) {
                                    PeriodoStatistiche.GIORNALIERE -> "Misurazioni giornaliere"
                                    PeriodoStatistiche.SETTIMANALI -> "Riepilogo settimanale"
                                    PeriodoStatistiche.MENSILI -> "Riepilogo mensile"
                                }
                            } ?: "Diario del peso ⚖️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            schermata?.let {
                                when (it) {
                                    PeriodoStatistiche.GIORNALIERE -> "Tutti i giorni registrati"
                                    PeriodoStatistiche.SETTIMANALI -> "Tutte le settimane, da lunedì a domenica"
                                    PeriodoStatistiche.MENSILI -> "Tutti i mesi registrati"
                                }
                            } ?: "Riepilogo della settimana corrente",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = { mostraDialogParametri = true }) {
                        Text("⚙ Metriche", fontSize = 12.sp)
                    }
                }
            }

            erroreCaricamento?.let { errore ->
                item {
                    Text(
                        "Impossibile caricare le misurazioni: $errore",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            if (schermata == null) {
                item {
                    TitoloSezioneStatistiche(
                        "Questa settimana",
                        "${formatoDataPeso.format(inizioSettimana)} – ${formatoDataPeso.format(oggi)}"
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPesoCard(
                            titolo = "Peso medio",
                            valore = mediaPesoSettimanale,
                            dettaglio = "${misurazioniSettimanaConPeso.size} misurazioni",
                            modifier = Modifier.weight(1f)
                        )
                        StatPesoCard(
                            titolo = "Proteine / peso",
                            valore = mediaProteineGKgSettimanale,
                            dettaglio = "g/kg al giorno",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Medie giornaliere", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            RigaRiepilogoSettimanale(
                                "Calorie mangiate",
                                mediaNutrizionale(misurazioniSettimana) { it.calorieAssunte?.toDouble() },
                                "kcal"
                            )
                            RigaRiepilogoSettimanale(
                                "Spesa totale stimata",
                                mediaNutrizionale(misurazioniSettimana) { it.calorieBruciate?.toDouble() },
                                "kcal"
                            )
                            RigaRiepilogoSettimanale(
                                "Calorie attive orologio",
                                mediaNutrizionale(misurazioniSettimana) { it.calorieOrologio?.toDouble() },
                                "kcal"
                            )
                            RigaRiepilogoSettimanale(
                                "Proteine",
                                mediaNutrizionale(misurazioniSettimana) { it.proteineGrammi },
                                "g"
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Peso atteso e confronto",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Scegli le settimane da confrontare",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            SelettoreSettimanaConfronto(
                                titolo = "Prima settimana",
                                settimanaSelezionata = settimanaConfrontoA,
                                settimane = settimaneSelezionabili.filter { it != settimanaConfrontoB },
                                onSeleziona = { settimanaConfrontoA = it }
                            )
                            SelettoreSettimanaConfronto(
                                titolo = "Seconda settimana",
                                settimanaSelezionata = settimanaConfrontoB,
                                settimane = settimaneSelezionabili.filter { it != settimanaConfrontoA },
                                onSeleziona = { settimanaConfrontoB = it }
                            )
                            IntestazioneConfrontoPeso()
                            RigaConfrontoPeso(
                                "Peso medio rilevato",
                                mediaPeso(misurazioniSettimanaA.filter { it.pesoKg != null }),
                                mediaPeso(misurazioniSettimanaB.filter { it.pesoKg != null }),
                                "kg"
                            )
                            RigaConfrontoPeso(
                                "Calorie mangiate · media/g",
                                mediaNutrizionale(misurazioniSettimanaA) { it.calorieAssunte?.toDouble() },
                                mediaNutrizionale(misurazioniSettimanaB) { it.calorieAssunte?.toDouble() },
                                "kcal"
                            )
                            RigaConfrontoPeso(
                                "Calorie spese · media/g",
                                mediaNutrizionale(misurazioniSettimanaA) { it.calorieBruciate?.toDouble() },
                                mediaNutrizionale(misurazioniSettimanaB) { it.calorieBruciate?.toDouble() },
                                "kcal"
                            )
                            RigaConfrontoPeso(
                                "Bilancio · media/g",
                                bilancioCaloricoMedio(misurazioniSettimanaA),
                                bilancioCaloricoMedio(misurazioniSettimanaB),
                                "kcal"
                            )
                            RigaConfrontoPeso(
                                "Proteine · media/g",
                                mediaNutrizionale(misurazioniSettimanaA) { it.proteineGrammi },
                                mediaNutrizionale(misurazioniSettimanaB) { it.proteineGrammi },
                                "g"
                            )
                            RigaConfrontoPeso(
                                "Perdita attesa nel periodo",
                                stimaVariazionePeso(misurazioniSettimanaA),
                                stimaVariazionePeso(misurazioniSettimanaB),
                                "kg",
                                perditaAttesa = true
                            )
                            Text(
                                "Prima: ${formatoDataPeso.format(settimanaConfrontoA)} – " +
                                    "${formatoDataPeso.format(finePeriodoSettimana(settimanaConfrontoA, inizioSettimana, oggi))}. " +
                                    "Seconda: ${formatoDataPeso.format(settimanaConfrontoB)} – " +
                                    "${formatoDataPeso.format(finePeriodoSettimana(settimanaConfrontoB, inizioSettimana, oggi))}.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Perdita attesa = (calorie spese − calorie mangiate) ÷ 7.700; valori positivi indicano perdita, negativi aumento.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                "Andamento del peso · ${
                                    oggi.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
                                        .replaceFirstChar { it.uppercase(Locale.ITALIAN) }
                                }",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            if (misurazioniPesoMese.isEmpty()) {
                                Text(
                                    "Nessuna misurazione di peso in questo mese.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            } else {
                                GraficoAndamentoPeso(misurazioniPesoMese)
                            }
                        }
                    }
                }

                item { TitoloSezioneStatistiche("Altre sezioni", "Apri un riepilogo completo") }
                item {
                    PulsanteSezioneStatistiche("Tutte le misurazioni giornaliere") {
                        schermata = PeriodoStatistiche.GIORNALIERE
                    }
                }
                item {
                    PulsanteSezioneStatistiche("Tutte le settimane") {
                        schermata = PeriodoStatistiche.SETTIMANALI
                    }
                }
                item {
                    PulsanteSezioneStatistiche("Tutti i mesi") {
                        schermata = PeriodoStatistiche.MENSILI
                    }
                }
            } else {
                val statistiche = when (schermata) {
                    PeriodoStatistiche.GIORNALIERE -> statisticheGiornaliere
                    PeriodoStatistiche.SETTIMANALI -> statisticheSettimanali
                    PeriodoStatistiche.MENSILI -> statisticheMensili
                    null -> emptyList()
                }
                if (schermata == PeriodoStatistiche.GIORNALIERE && misurazioni.any { it.pesoKg != null }) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Andamento del peso", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                GraficoAndamentoPeso(misurazioni.filter { it.pesoKg != null })
                            }
                        }
                    }
                }
                if (statistiche.isEmpty()) {
                    item { TestoStatisticheVuote() }
                } else {
                    items(statistiche, key = { "${schermata?.name}-${it.etichetta}" }) { gruppo ->
                        DettaglioStatisticheCard(
                            gruppo,
                            schermata ?: PeriodoStatistiche.GIORNALIERE,
                            statistiche
                        )
                    }
                }
            }
        }
    }

    if (mostraForm) {
        Dialog(
            onDismissRequest = { if (!salvataggioInCorso) mostraForm = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Nuova misurazione", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = data,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Data") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { mostraCalendario = true }) {
                            Text("Calendario")
                        }
                    }
                    OutlinedTextField(
                        value = peso,
                        onValueChange = { peso = it.filter { carattere -> carattere.isDigit() || carattere == ',' || carattere == '.' } },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = calorieAssunte,
                        onValueChange = { calorieAssunte = it.filter(Char::isDigit) },
                        label = { Text("Calorie mangiate (kcal)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = calorieOrologio,
                        onValueChange = { calorieOrologio = it.filter(Char::isDigit) },
                        label = { Text("Calorie attive orologio (kcal)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = when {
                            calorieOrologio.isBlank() -> "Inserisci le calorie attive indicate dall'orologio."
                            parametriMetabolismo == null ->
                                "Inserisci e salva una volta i parametri del metabolismo dal pulsante Metriche."
                            metabolismoStimato == null ->
                                "Per calcolare il totale registra almeno un peso in questa settimana."
                            else ->
                                "Stima Harris-Benedict: metabolismo ${formattaPeso(metabolismoStimato)} + " +
                                    "(${calorieOrologio} × 0,8) = ${totaleCalorieStimato ?: "—"} kcal"
                        },
                        fontSize = 11.sp,
                        color = if (calorieOrologio.isNotBlank() && metabolismoStimato == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Gray
                        }
                    )
                    OutlinedTextField(
                        value = proteine,
                        onValueChange = {
                            proteine = it.filter { carattere ->
                                carattere.isDigit() || carattere == ',' || carattere == '.'
                            }
                        },
                        label = { Text("Proteine mangiate (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    messaggio?.let {
                        Text(
                            it,
                            color = if (erroreSalvataggio) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            val giorno = try {
                                LocalDate.parse(data, formatoDataPeso)
                            } catch (_: DateTimeParseException) {
                                messaggio = "Seleziona una data valida."
                                erroreSalvataggio = true
                                return@Button
                            }
                            if (giorno.isAfter(LocalDate.now())) {
                                messaggio = "La data non può essere futura."
                                erroreSalvataggio = true
                                return@Button
                            }
                            val valorePeso = peso.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
                            val calorieMangiate = calorieAssunte.takeIf { it.isNotBlank() }?.toIntOrNull()
                            val calorieWatch = calorieOrologio.takeIf { it.isNotBlank() }?.toIntOrNull()
                            val proteineGrammi = proteine.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
                            if (
                                (peso.isNotBlank() && (valorePeso == null || !valorePeso.isFinite() || valorePeso !in 1.0..500.0)) ||
                                (calorieAssunte.isNotBlank() && (calorieMangiate == null || calorieMangiate !in 0..20_000)) ||
                                (calorieOrologio.isNotBlank() && (calorieWatch == null || calorieWatch !in 0..20_000)) ||
                                (proteine.isNotBlank() && (proteineGrammi == null || !proteineGrammi.isFinite() || proteineGrammi !in 0.0..1_000.0))
                            ) {
                                messaggio = "Controlla i valori: peso 1–500 kg, calorie 0–20.000, proteine 0–1.000 g."
                                erroreSalvataggio = true
                                return@Button
                            }
                            if (valorePeso == null && calorieMangiate == null && calorieWatch == null && proteineGrammi == null) {
                                messaggio = "Inserisci almeno un dato per questa giornata."
                                erroreSalvataggio = true
                                return@Button
                            }
                            val pesoMedioUsato = if (
                                !giorno.isBefore(inizioSettimana) && !giorno.isAfter(oggi) && valorePeso != null
                            ) {
                                (misurazioniSettimanaConPeso.filter {
                                    it.data != giorno.format(formatoChiavePeso)
                                }.mapNotNull { it.pesoKg } + valorePeso).average()
                            } else {
                                mediaPesoSettimanale
                            }
                            if (calorieWatch != null && (pesoMedioUsato == null || parametriMetabolismo == null)) {
                                messaggio = if (parametriMetabolismo == null) {
                                    "Inserisci e salva i parametri del metabolismo prima delle calorie dell'orologio."
                                } else {
                                    "Registra almeno un peso nella settimana corrente prima delle calorie dell'orologio."
                                }
                                erroreSalvataggio = true
                                return@Button
                            }
                            val metabolismoUsato = pesoMedioUsato?.let { media ->
                                parametriMetabolismo?.let { metabolismoHarrisBenedict(media, it) }
                            }
                            val calorieSpeseTotali = calorieWatch?.let { watch ->
                                metabolismoUsato?.plus(watch * 0.8)?.roundToInt()
                            }
                            salvataggioInCorso = true
                            messaggio = null
                            FirebaseManager.salvaMisurazionePeso(
                                MisurazionePeso(
                                    data = giorno.format(formatoChiavePeso),
                                    pesoKg = valorePeso,
                                    calorieAssunte = calorieMangiate,
                                    calorieBruciate = calorieSpeseTotali,
                                    calorieOrologio = calorieWatch,
                                    metabolismoBasaleKcal = metabolismoUsato,
                                    pesoMedioSettimanaleUsatoKg = pesoMedioUsato?.takeIf { calorieWatch != null },
                                    proteineGrammi = proteineGrammi,
                                    aggiornatoAlle = System.currentTimeMillis()
                                )
                            ) { errore ->
                                salvataggioInCorso = false
                                erroreSalvataggio = errore != null
                                messaggio = errore ?: "Misurazione salvata."
                                if (errore == null) mostraForm = false
                            }
                        },
                        enabled = !salvataggioInCorso,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (salvataggioInCorso) "SALVATAGGIO..." else "SALVA MISURAZIONE", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { mostraForm = false },
                        enabled = !salvataggioInCorso,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Annulla")
                    }
                    Text(
                        "Stima calorie spese = metabolismo basale Harris-Benedict rivista + 80% delle calorie attive dell'orologio. I campi vuoti non modificano i dati già salvati.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    if (mostraCalendario) {
        val dataInizialeMillis = runCatching {
            LocalDate.parse(data, formatoDataPeso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val statoCalendario = rememberDatePickerState(initialSelectedDateMillis = dataInizialeMillis)
        DatePickerDialog(
            onDismissRequest = { mostraCalendario = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        statoCalendario.selectedDateMillis?.let { millis ->
                            data = formatoDataPeso.format(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        mostraCalendario = false
                    }
                ) {
                    Text("Conferma")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraCalendario = false }) {
                    Text("Annulla")
                }
            }
        ) {
            DatePicker(state = statoCalendario)
        }
    }

    if (mostraDialogParametri) {
        AlertDialog(
            onDismissRequest = { mostraDialogParametri = false },
            title = { Text("Parametri metabolismo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Inseriscili una volta: saranno riutilizzati nei calcoli di ogni giornata.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = etaInput,
                        onValueChange = { etaInput = it.filter(Char::isDigit) },
                        label = { Text("Età (anni)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = altezzaInput,
                        onValueChange = { altezzaInput = it.filter(Char::isDigit) },
                        label = { Text("Altezza (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sessoInput == "Uomo",
                            onClick = { sessoInput = "Uomo" },
                            label = { Text("Uomo") }
                        )
                        FilterChip(
                            selected = sessoInput == "Donna",
                            onClick = { sessoInput = "Donna" },
                            label = { Text("Donna") }
                        )
                    }
                    messaggioParametri?.let {
                        Text(
                            it,
                            color = if (erroreParametri) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val eta = etaInput.toIntOrNull()
                        val altezza = altezzaInput.toIntOrNull()
                        if (
                            eta == null || eta !in 18..100 ||
                            altezza == null || altezza !in 100..250 ||
                            sessoInput !in setOf("Uomo", "Donna")
                        ) {
                            messaggioParametri = "Inserisci età (18–100), altezza (100–250 cm) e sesso."
                            erroreParametri = true
                            return@Button
                        }
                        salvataggioParametriInCorso = true
                        messaggioParametri = null
                        val nuoviParametri = ParametriMetabolismo(
                            eta = eta,
                            altezzaCm = altezza,
                            sesso = sessoInput,
                            aggiornatoAlle = System.currentTimeMillis()
                        )
                        FirebaseManager.salvaParametriMetabolismo(nuoviParametri) { errore ->
                            salvataggioParametriInCorso = false
                            erroreParametri = errore != null
                            messaggioParametri = errore ?: "Parametri salvati."
                            if (errore == null) {
                                parametriMetabolismo = nuoviParametri
                                mostraDialogParametri = false
                            }
                        }
                    },
                    enabled = !salvataggioParametriInCorso
                ) {
                    Text(if (salvataggioParametriInCorso) "SALVATAGGIO..." else "SALVA")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraDialogParametri = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun RigaRiepilogoSettimanale(titolo: String, valore: Double?, unita: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titolo, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.White)
        Text(
            valore?.let { "${formattaPeso(it)} $unita" } ?: "—",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun IntestazioneConfrontoPeso() {
    Row(Modifier.fillMaxWidth()) {
        Text("Settimana 1", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color.Gray)
        Text("Settimana 2", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color.Gray)
        Text("Differenza", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreSettimanaConfronto(
    titolo: String,
    settimanaSelezionata: LocalDate,
    settimane: List<LocalDate>,
    onSeleziona: (LocalDate) -> Unit
) {
    var menuEspanso by remember { mutableStateOf(false) }
    val formatoEtichetta = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN) }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(titolo, fontSize = 10.sp, color = Color.Gray)
        ExposedDropdownMenuBox(
            expanded = menuEspanso,
            onExpandedChange = { menuEspanso = !menuEspanso }
        ) {
            OutlinedTextField(
                value = "${formatoEtichetta.format(settimanaSelezionata)} – " +
                    "${formatoEtichetta.format(settimanaSelezionata.plusDays(6))}",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuEspanso)
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = menuEspanso,
                onDismissRequest = { menuEspanso = false }
            ) {
                settimane.forEach { settimana ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${formatoEtichetta.format(settimana)} – " +
                                    "${formatoEtichetta.format(settimana.plusDays(6))}"
                            )
                        },
                        onClick = {
                            onSeleziona(settimana)
                            menuEspanso = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RigaConfrontoPeso(
    titolo: String,
    attuale: Double?,
    precedente: Double?,
    unita: String,
    perditaAttesa: Boolean = false
) {
    val differenza = if (attuale != null && precedente != null) attuale - precedente else null
    val formatta: (Double?) -> String = { valore ->
        if (valore == null) {
            "—"
        } else {
            val segno = if (perditaAttesa && valore > 0.0) "+" else ""
            "$segno${formattaValoreStatistica(valore)} $unita"
        }
    }
    val differenzaFormattata = differenza?.let {
        val segno = if (it > 0.0) "+" else ""
        "$segno${formattaValoreStatistica(it)} $unita"
    } ?: "—"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(titolo, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatta(attuale),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    formatta(precedente),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                Text(
                    differenzaFormattata,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        differenza == null -> Color.Gray
                        differenza > 0.0 -> Color(0xFF81C784)
                        differenza < 0.0 -> Color(0xFFFF8A80)
                        else -> Color.Gray
                    }
                )
            }
        }
    }
}

@Composable
private fun PulsanteSezioneStatistiche(titolo: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titolo)
            Text("›", fontSize = 22.sp)
        }
    }
}
