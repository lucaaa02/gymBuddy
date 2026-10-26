package com.example.gymtracker.data

import android.content.Context
import android.util.Base64
import com.example.gymtracker.models.EsercizioEseguitoLog
import com.example.gymtracker.models.EsercizioInScheda
import com.example.gymtracker.models.SchedaAllenamento
import com.example.gymtracker.models.SerieLog
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class WorkoutDraft(
    val nickname: String,
    val idAllenamento: String = UUID.randomUUID().toString(),
    val scheda: SchedaAllenamento,
    val dettagli: List<EsercizioEseguitoLog>,
    val iniziatoAlle: Long,
    val dataAllenamento: String,
    val pesiDigitati: Map<String, String> = emptyMap(),
    val ripetizioniDigitate: Map<String, String> = emptyMap(),
    val timerFineMillis: Long = 0L,
    val secondiTimerRimanenti: Int = 0,
    val timerAttivo: Boolean = false
)

object WorkoutDraftStore {
    private const val PREFS = "GymBuddyWorkoutDrafts"

    fun salva(context: Context, bozza: WorkoutDraft) {
        val root = JSONObject()
            .put("nickname", bozza.nickname)
            .put("idAllenamento", bozza.idAllenamento)
            .put("iniziatoAlle", bozza.iniziatoAlle)
            .put("dataAllenamento", bozza.dataAllenamento)
            .put("timerFineMillis", bozza.timerFineMillis)
            .put("secondiTimerRimanenti", bozza.secondiTimerRimanenti)
            .put("timerAttivo", bozza.timerAttivo)
            .put("scheda", bozza.scheda.toJson())
            .put("dettagli", JSONArray().apply { bozza.dettagli.forEach { put(it.toJson()) } })
            .put("pesiDigitati", bozza.pesiDigitati.toJson())
            .put("ripetizioniDigitate", bozza.ripetizioniDigitate.toJson())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(chiave(bozza.nickname), root.toString()).commit()
    }

    fun carica(context: Context, nickname: String): WorkoutDraft? {
        val testo = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(chiave(nickname), null) ?: return null
        return runCatching {
            val root = JSONObject(testo)
            WorkoutDraft(
                nickname = root.optString("nickname", nickname),
                idAllenamento = root.optString("idAllenamento").ifBlank { UUID.randomUUID().toString() },
                scheda = root.getJSONObject("scheda").toScheda(),
                dettagli = root.getJSONArray("dettagli").let { array ->
                    (0 until array.length()).map { array.getJSONObject(it).toDettaglio() }
                },
                iniziatoAlle = root.optLong("iniziatoAlle", System.currentTimeMillis()),
                dataAllenamento = root.optString("dataAllenamento"),
                pesiDigitati = root.optJSONObject("pesiDigitati").toStringMap(),
                ripetizioniDigitate = root.optJSONObject("ripetizioniDigitate").toStringMap(),
                timerFineMillis = root.optLong("timerFineMillis", 0L),
                secondiTimerRimanenti = root.optInt("secondiTimerRimanenti", 0),
                timerAttivo = root.optBoolean("timerAttivo", false)
            )
        }.getOrNull()
    }

    fun elimina(context: Context, nickname: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(chiave(nickname)).apply()
    }

    private fun chiave(nickname: String): String = "bozza_" + Base64.encodeToString(
        nickname.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun SchedaAllenamento.toJson() = JSONObject()
        .put("idScheda", idScheda)
        .put("nomeUtente", nomeUtente)
        .put("titoloScheda", titoloScheda)
        .put("esercizi", JSONArray().apply {
            esercizi.forEach { esercizio ->
                put(JSONObject()
                    .put("idEsercizio", esercizio.idEsercizio)
                    .put("nomeEsercizio", esercizio.nomeEsercizio)
                    .put("serieTarget", esercizio.serieTarget)
                    .put("repsTarget", esercizio.repsTarget)
                    .put("note", esercizio.note))
            }
        })

    private fun JSONObject.toScheda(): SchedaAllenamento {
        val eserciziJson = optJSONArray("esercizi") ?: JSONArray()
        return SchedaAllenamento(
            idScheda = optString("idScheda"),
            nomeUtente = optString("nomeUtente"),
            titoloScheda = optString("titoloScheda"),
            esercizi = (0 until eserciziJson.length()).map { index ->
                val esercizio = eserciziJson.getJSONObject(index)
                EsercizioInScheda(
                    idEsercizio = esercizio.optString("idEsercizio"),
                    nomeEsercizio = esercizio.optString("nomeEsercizio"),
                    serieTarget = esercizio.optInt("serieTarget", 3),
                    repsTarget = esercizio.optInt("repsTarget", 10),
                    note = esercizio.optString("note")
                )
            }
        )
    }

    private fun EsercizioEseguitoLog.toJson() = JSONObject()
        .put("idEsercizio", idEsercizio)
        .put("nomeEsercizio", nomeEsercizio)
        .put("pesoKg", pesoKg)
        .put("repsEseguite", repsEseguite)
        .put("serieEseguite", serieEseguite)
        .put("noteEsercizio", noteEsercizio)
        .put("saltato", saltato)
        .put("serieDettaglio", JSONArray().apply {
            serieDettaglio.forEach { serie ->
                put(JSONObject()
                    .put("numeroSerie", serie.numeroSerie)
                    .put("pesoKg", serie.pesoKg)
                    .put("reps", serie.reps)
                    .put("tipo", serie.tipo))
            }
        })

    private fun JSONObject.toDettaglio(): EsercizioEseguitoLog {
        val serieJson = optJSONArray("serieDettaglio") ?: JSONArray()
        return EsercizioEseguitoLog(
            idEsercizio = optString("idEsercizio"),
            nomeEsercizio = optString("nomeEsercizio"),
            pesoKg = optDouble("pesoKg"),
            repsEseguite = optInt("repsEseguite"),
            serieEseguite = optInt("serieEseguite"),
            noteEsercizio = optString("noteEsercizio"),
            saltato = optBoolean("saltato"),
            serieDettaglio = (0 until serieJson.length()).map { index ->
                val serie = serieJson.getJSONObject(index)
                SerieLog(
                    numeroSerie = serie.optInt("numeroSerie", index + 1),
                    pesoKg = serie.optDouble("pesoKg"),
                    reps = serie.optInt("reps"),
                    tipo = serie.optString("tipo", "Normale")
                )
            }
        )
    }

    private fun Map<String, String>.toJson() = JSONObject().apply {
        forEach { (chiave, valore) -> put(chiave, valore) }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optString(it) }
    }
}
