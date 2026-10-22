package com.example.gymtracker.data

import com.example.gymtracker.models.AllenamentoCompletato
import com.example.gymtracker.models.EsercizioCatalogo
import com.example.gymtracker.models.SchedaAllenamento
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Transaction
import android.util.Base64
import android.os.Handler
import android.os.Looper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.concurrent.Executors

object FirebaseManager {
    private val authExecutor = Executors.newSingleThreadExecutor()

    private val db: FirebaseDatabase
        get() = try {
            FirebaseDatabase.getInstance("https://gym-buddy-9fe45-default-rtdb.europe-west1.firebasedatabase.app")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }

    private const val NODO_ESERCIZI = "esercizi_db"
    private const val NODO_SCHEDE = "schede_utenti"
    private const val NODO_ALLENAMENTI = "allenamenti_completati"
    private const val NODO_PROFILI = "profili"

    /** Prenota un nickname in modo atomico, confrontandolo senza distinzione tra maiuscole e minuscole. */
    fun registraProfilo(nickname: String, password: String, onResult: (registrato: Boolean, errore: String?) -> Unit) {
        val nome = nickname.trim()
        if (nome.isBlank()) {
            onResult(false, "Inserisci un nickname.")
            return
        }
        if (password.length < 8) {
            onResult(false, "La password deve contenere almeno 8 caratteri.")
            return
        }

        val chiave = chiaveNickname(nome)
        val profiloRef = db.getReference(NODO_PROFILI).child(chiave)
        authExecutor.execute {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = hashPassword(password, salt)
            profiloRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val valore = currentData.value as? Map<*, *>
                    if (valore != null && valore["passwordHash"] != null) return Transaction.abort()
                    currentData.value = mapOf(
                        "nickname" to (valore?.get("nickname") as? String ?: nome),
                        "passwordSalt" to Base64.encodeToString(salt, Base64.NO_WRAP),
                        "passwordHash" to Base64.encodeToString(hash, Base64.NO_WRAP)
                    )
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    when {
                        error != null -> onResult(false, "Registrazione non riuscita. Controlla la connessione e riprova.")
                        committed -> onResult(true, null)
                        else -> onResult(false, "Questo nickname è già associato a una password. Prova ad accedere.")
                    }
                }
            })
        }
    }

    fun accediProfilo(nickname: String, password: String, onResult: (String?, String?) -> Unit) {
        val nome = nickname.trim()
        db.getReference(NODO_PROFILI).child(chiaveNickname(nome)).get()
            .addOnSuccessListener { snapshot ->
                val nicknameSalvato = snapshot.child("nickname").getValue(String::class.java)
                val saltTesto = snapshot.child("passwordSalt").getValue(String::class.java)
                val hashAttesoTesto = snapshot.child("passwordHash").getValue(String::class.java)
                if (nicknameSalvato.isNullOrBlank()) {
                    onResult(null, "Profilo non trovato. Controlla il nickname o registrati.")
                } else if (saltTesto == null || hashAttesoTesto == null) {
                    onResult(null, "Questo è un profilo precedente senza password. Vai su Registrati e imposta una password con lo stesso nickname: i tuoi dati resteranno associati.")
                } else {
                    authExecutor.execute {
                        val salt = Base64.decode(saltTesto, Base64.NO_WRAP)
                        val hashInserito = hashPassword(password, salt)
                        val hashAtteso = Base64.decode(hashAttesoTesto, Base64.NO_WRAP)
                        val risultato = if (MessageDigest.isEqual(hashAtteso, hashInserito)) nicknameSalvato to null
                        else null to "Password non corretta."
                        Handler(Looper.getMainLooper()).post { onResult(risultato.first, risultato.second) }
                    }
                }
            }
            .addOnFailureListener {
                onResult(null, "Accesso non riuscito. Controlla la connessione e riprova.")
            }
    }

    private fun chiaveNickname(nickname: String): String = Base64.encodeToString(
        nickname.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

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

    fun aggiungiEsercizioCatalogo(nome: String, gruppi: List<String>) {
        val gruppoStr = if (gruppi.isEmpty()) "Altro" else gruppi.joinToString(", ")
        aggiungiEsercizioCatalogo(nome, gruppoStr)
    }

    fun aggiornaEsercizioCatalogo(id: String, nuovoNome: String, nuovoGruppo: String) {
        if (id.isNotBlank()) {
            val ref = db.getReference(NODO_ESERCIZI).child(id)
            ref.setValue(EsercizioCatalogo(id = id, nome = nuovoNome, gruppoMuscolare = nuovoGruppo))
        }
    }

    fun aggiornaEsercizioCatalogo(id: String, nuovoNome: String, nuoviGruppi: List<String>) {
        val gruppoStr = if (nuoviGruppi.isEmpty()) "Altro" else nuoviGruppi.joinToString(", ")
        aggiornaEsercizioCatalogo(id, nuovoNome, gruppoStr)
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

    fun aggiornaAllenamentoSeProprietario(
        allenamento: AllenamentoCompletato,
        proprietario: String,
        onCompletato: (Boolean) -> Unit
    ) {
        if (allenamento.id.isBlank()) {
            onCompletato(false)
            return
        }
        db.getReference(NODO_ALLENAMENTI).child(allenamento.id)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val proprietarioSalvato = currentData.child("nomeUtente").getValue(String::class.java)
                    if (!proprietarioSalvato.equals(proprietario, ignoreCase = true)) return Transaction.abort()
                    currentData.value = allenamento.copy(nomeUtente = proprietarioSalvato ?: proprietario)
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    onCompletato(error == null && committed)
                }
            })
    }

    fun eliminaAllenamentoSeProprietario(idAllenamento: String, proprietario: String, onCompletato: (Boolean) -> Unit) {
        if (idAllenamento.isBlank()) {
            onCompletato(false)
            return
        }
        db.getReference(NODO_ALLENAMENTI).child(idAllenamento)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val proprietarioSalvato = currentData.child("nomeUtente").getValue(String::class.java)
                    if (!proprietarioSalvato.equals(proprietario, ignoreCase = true)) return Transaction.abort()
                    currentData.value = null
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    onCompletato(error == null && committed)
                }
            })
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

    fun ascoltaProfili(onDataChanged: (List<String>) -> Unit) {
        db.getReference(NODO_PROFILI)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nomi = snapshot.children.mapNotNull { profilo ->
                        profilo.child("nickname").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    }
                    onDataChanged(nomi)
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
                db.getReference(NODO_PROFILI).child(chiaveNickname(nomeUtente)).removeValue()
                    .addOnCompleteListener { onCompletato() }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
