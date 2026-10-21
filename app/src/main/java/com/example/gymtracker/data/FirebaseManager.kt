package com.example.gymtracker.data

import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo
import com.example.gymtracker.models.SchedaAllenamento
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseManager {

    private val db: FirebaseDatabase
        get() = try {
            FirebaseDatabase.getInstance("https://gym-buddy-9fe45-default-rtdb.europe-west1.firebasedatabase.app")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }

    private const val NODO_ESERCIZI = "esercizi_db"
    private const val NODO_SCHEDE = "schede_utenti"
    private const val NODO_ALLENAMENTI = "allenamenti_completati"

    fun ascoltaCatalogoEsercizi(onDataChanged: (List<EsercizioCatalogo>) -> Unit) {
        db.getReference(NODO_ESERCIZI)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lista = mutableListOf<EsercizioCatalogo>()
                    for (child in snapshot.children) {
                        child.getValue(EsercizioCatalogo::class.java)?.let { lista.add(it) }
                    }
                    onDataChanged(lista)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun aggiungiEsercizioCatalogo(nome: String, gruppoMuscolare: String) {
        val ref = db.getReference(NODO_ESERCIZI).push()
        val nuovoEx = EsercizioCatalogo(id = ref.key ?: "", nome = nome, gruppoMuscolare = gruppoMuscolare)
        ref.setValue(nuovoEx)
    }

    fun aggiornaEsercizioCatalogo(id: String, nuovoNome: String, nuovoGruppo: String) {
        if (id.isNotBlank()) {
            val ref = db.getReference(NODO_ESERCIZI).child(id)
            ref.setValue(EsercizioCatalogo(id = id, nome = nuovoNome, gruppoMuscolare = nuovoGruppo))
        }
    }

    fun ascoltaMieSchede(nomeUtente: String, onDataChanged: (List<SchedaAllenamento>) -> Unit) {
        db.getReference(NODO_SCHEDE)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lista = mutableListOf<SchedaAllenamento>()
                    for (child in snapshot.children) {
                        val scheda = child.getValue(SchedaAllenamento::class.java)
                        if (scheda != null && scheda.nomeUtente.equals(nomeUtente, ignoreCase = true)) {
                            lista.add(scheda)
                        }
                    }
                    onDataChanged(lista)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun salvaOAggiornaScheda(scheda: SchedaAllenamento) {
        val ref = if (scheda.idScheda.isBlank()) {
            db.getReference(NODO_SCHEDE).push()
        } else {
            db.getReference(NODO_SCHEDE).child(scheda.idScheda)
        }
        val schedaDaSalvare = scheda.copy(idScheda = ref.key ?: scheda.idScheda)
        ref.setValue(schedaDaSalvare)
    }

    fun eliminaScheda(idScheda: String) {
        db.getReference(NODO_SCHEDE).child(idScheda).removeValue()
    }

    fun salvaAllenamentoEseguito(allenamento: AllenamentoCompletato) {
        val ref = db.getReference(NODO_ALLENAMENTI).push()
        val logSalvato = allenamento.copy(id = ref.key ?: "")
        ref.setValue(logSalvato)
    }

    fun salvaOAggiornaAllenamentoEseguito(allenamento: AllenamentoCompletato) {
        val ref = if (allenamento.id.isBlank()) {
            db.getReference(NODO_ALLENAMENTI).push()
        } else {
            db.getReference(NODO_ALLENAMENTI).child(allenamento.id)
        }
        val logSalvato = allenamento.copy(id = ref.key ?: allenamento.id)
        ref.setValue(logSalvato)
    }

    fun eliminaAllenamentoCompletato(idAllenamento: String) {
        if (idAllenamento.isNotBlank()) {
            db.getReference(NODO_ALLENAMENTI).child(idAllenamento).removeValue()
        }
    }

    fun ascoltaFeedAllenamenti(onDataChanged: (List<AllenamentoCompletato>) -> Unit) {
        db.getReference(NODO_ALLENAMENTI)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lista = mutableListOf<AllenamentoCompletato>()
                    for (child in snapshot.children) {
                        child.getValue(AllenamentoCompletato::class.java)?.let { lista.add(it) }
                    }
                    onDataChanged(lista.reversed())
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun eliminaUtenteEDataCompleti(nomeUtente: String, onCompletato: () -> Unit) {
        db.getReference(NODO_SCHEDE).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val s = child.getValue(SchedaAllenamento::class.java)
                    if (s != null && s.nomeUtente.equals(nomeUtente, ignoreCase = true)) {
                        child.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference(NODO_ALLENAMENTI).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val a = child.getValue(AllenamentoCompletato::class.java)
                    if (a != null && a.nomeUtente.equals(nomeUtente, ignoreCase = true)) {
                        child.ref.removeValue()
                    }
                }
                onCompletato()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
