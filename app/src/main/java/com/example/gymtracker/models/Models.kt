package com.example.gymtracker.models

object TipoSerie {
    const val NORMALE = "Normale"
    const val RISCALDAMENTO = "Riscaldamento"
    const val BACKOFF = "Backoff"
    const val SUPERSET = "Superset"

    val TUTTI = listOf(NORMALE, RISCALDAMENTO, BACKOFF, SUPERSET)

    fun icona(tipo: String): String = when (tipo) {
        RISCALDAMENTO -> "🔥"
        BACKOFF -> "⬇️"
        SUPERSET -> "⚡"
        else -> "🎯"
    }

    fun etichettaCorta(tipo: String): String = when (tipo) {
        RISCALDAMENTO -> "Risc."
        BACKOFF -> "Back."
        SUPERSET -> "Super."
        else -> "Norm."
    }
}

data class SerieLog(
    val numeroSerie: Int = 1,
    val pesoKg: Double = 0.0,
    val reps: Int = 0,
    val tipo: String = TipoSerie.NORMALE
)

data class EsercizioCatalogo(
    val id: String = "",
    val nome: String = "",
    val gruppoMuscolare: String = ""
)

fun EsercizioCatalogo.listaGruppiMuscolari(): List<String> {
    if (gruppoMuscolare.isBlank()) return emptyList()
    return gruppoMuscolare.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

fun EsercizioCatalogo.primoGruppoMuscolare(): String {
    return listaGruppiMuscolari().firstOrNull() ?: "Altro"
}

data class EsercizioInScheda(
    val idEsercizio: String = "",
    val nomeEsercizio: String = "",
    val serieTarget: Int = 3,
    val repsTarget: Int = 10,
    val note: String = ""
)

data class SchedaAllenamento(
    val idScheda: String = "",
    val nomeUtente: String = "",
    val titoloScheda: String = "",
    val esercizi: List<EsercizioInScheda> = emptyList()
)

data class EsercizioEseguitoLog(
    val idEsercizio: String = "",
    val nomeEsercizio: String = "",
    val pesoKg: Double = 0.0,
    val repsEseguite: Int = 0,
    val serieEseguite: Int = 0,
    val noteEsercizio: String = "",
    val saltato: Boolean = false,
    val serieDettaglio: List<SerieLog> = emptyList()
)

fun EsercizioEseguitoLog.getSerieEffettive(): List<SerieLog> {
    if (serieDettaglio.isNotEmpty()) return serieDettaglio
    if (serieEseguite > 0 && (pesoKg > 0 || repsEseguite > 0)) {
        return (1..serieEseguite).map { num ->
            SerieLog(numeroSerie = num, pesoKg = pesoKg, reps = repsEseguite, tipo = TipoSerie.NORMALE)
        }
    }
    return emptyList()
}

fun EsercizioEseguitoLog.tonnellaggioTotale(): Double {
    val serie = getSerieEffettive()
    if (serie.isNotEmpty()) {
        return serie.sumOf { it.pesoKg * it.reps }
    }
    return pesoKg * serieEseguite * repsEseguite
}

fun EsercizioEseguitoLog.pesoMax(): Double {
    val serie = getSerieEffettive()
    if (serie.isNotEmpty()) {
        return serie.maxOfOrNull { it.pesoKg } ?: 0.0
    }
    return pesoKg
}

fun EsercizioEseguitoLog.max1RMStimato(): Double {
    val serie = getSerieEffettive()
    if (serie.isNotEmpty()) {
        return serie
            .filter { it.pesoKg > 0 && it.reps > 0 }
            .maxOfOrNull { it.pesoKg * (1.0 + (it.reps / 30.0)) } ?: 0.0
    }
    if (pesoKg > 0 && repsEseguite > 0) {
        return pesoKg * (1.0 + (repsEseguite / 30.0))
    }
    return 0.0
}

fun EsercizioEseguitoLog.descrizioneSerieCompatta(): String {
    val serie = getSerieEffettive()
    if (serie.isEmpty()) return "Nessuna serie registrata"
    return serie.joinToString(", ") { s ->
        val prefix = when (s.tipo) {
            TipoSerie.RISCALDAMENTO -> "[Risc] "
            TipoSerie.BACKOFF -> "[Back] "
            TipoSerie.SUPERSET -> "[Super] "
            else -> ""
        }
        "$prefix${s.pesoKg}kg×${s.reps}"
    }
}

data class AllenamentoCompletato(
    val id: String = "",
    val nomeUtente: String = "",
    val titoloScheda: String = "",
    val data: String = "",
    val durataSecondi: Int = 0,
    val notaGeneraleSessione: String = "",
    val dettagliEsercizi: List<EsercizioEseguitoLog> = emptyList()
)

data class RichiestaAmicizia(
    val idUtente: String = "",
    val nickname: String = ""
)
