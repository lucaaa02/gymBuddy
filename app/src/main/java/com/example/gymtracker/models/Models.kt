package com.example.gymtracker.models

data class EsercizioCatalogo(
    val id: String = "",
    val nome: String = "",
    val gruppoMuscolare: String = ""
)

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
    val saltato: Boolean = false
)

data class AllenamentoCompletato(
    val id: String = "",
    val nomeUtente: String = "",
    val titoloScheda: String = "",
    val data: String = "",
    val durataSecondi: Int = 0,
    val notaGeneraleSessione: String = "",
    val dettagliEsercizi: List<EsercizioEseguitoLog> = emptyList()
)
