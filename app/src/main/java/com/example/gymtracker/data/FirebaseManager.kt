package com.example.gymtracker.data

import com.example.gymtracker.models.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object FirebaseManager {
    private val authExecutor = Executors.newSingleThreadExecutor()
    private const val DB_URL = "https://gym-buddy-9fe45-default-rtdb.europe-west1.firebasedatabase.app"
    // La persistenza va attivata prima di ottenere riferimenti ai nodi del database.
    private val db = FirebaseDatabase.getInstance(DB_URL).apply {
        setPersistenceEnabled(true)
    }

    fun registraProfilo(nickname: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val nome = nickname.trim()
        if (nome.isBlank()) return onResult(false, "Inserisci un nickname.")
        if (password.length < 8) return onResult(false, "La password deve contenere almeno 8 caratteri.")
        val profiloRef = db.getReference("profili").child(chiaveNickname(nome))
        authExecutor.execute {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = hashPassword(password, salt)
            profiloRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val profilo = currentData.value as? Map<*, *>
                    if (profilo?.get("passwordHash") != null) return Transaction.abort()
                    currentData.value = mapOf(
                        "nickname" to (profilo?.get("nickname") as? String ?: nome),
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
        db.getReference("profili").child(chiaveNickname(nickname)).get()
            .addOnSuccessListener { profilo ->
                val nome = profilo.child("nickname").getValue(String::class.java)
                val salt = profilo.child("passwordSalt").getValue(String::class.java)
                val hashSalvato = profilo.child("passwordHash").getValue(String::class.java)
                when {
                    nome.isNullOrBlank() -> onResult(null, "Profilo non trovato. Controlla il nickname o registrati.")
                    salt == null || hashSalvato == null -> onResult(null, "Profilo precedente senza password. Scegli Registrati e imposta una password usando lo stesso nickname: i dati resteranno associati.")
                    else -> authExecutor.execute {
                        val valido = MessageDigest.isEqual(
                            Base64.decode(hashSalvato, Base64.NO_WRAP),
                            hashPassword(password, Base64.decode(salt, Base64.NO_WRAP))
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (valido) onResult(nome, null) else onResult(null, "Password non corretta.")
                        }
                    }
                }
            }
            .addOnFailureListener { onResult(null, "Accesso non riuscito. Controlla la connessione e riprova.") }
    }

    private fun chiaveNickname(nickname: String): String = Base64.encodeToString(
        nickname.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    fun chiaveUtente(nickname: String): String = chiaveNickname(nickname)

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun aggiungiEsercizioCatalogo(nome: String, gruppo: String) {
        val ref = db.getReference("esercizi_db")
        val key = ref.push().key ?: return
        val esercizio = EsercizioCatalogo(id = key, nome = nome.trim(), gruppoMuscolare = gruppo.trim())
        ref.child(key).setValue(esercizio)
    }

    fun aggiungiEsercizioCatalogo(nome: String, gruppi: List<String>) {
        aggiungiEsercizioCatalogo(nome, gruppi.ifEmpty { listOf("Altro") }.joinToString(", "))
    }

    fun aggiornaEsercizioCatalogo(id: String, nuovoNome: String, gruppi: List<String>) {
        aggiornaEsercizioCatalogo(id, nuovoNome, gruppi.ifEmpty { listOf("Altro") }.joinToString(", "))
    }

    fun aggiornaEsercizioCatalogo(id: String, nuovoNome: String, gruppo: String) {
        if (id.isNotBlank()) {
            db.getReference("esercizi_db").child(id)
                .setValue(EsercizioCatalogo(id = id, nome = nuovoNome.trim(), gruppoMuscolare = gruppo.trim()))
        }
    }

    fun ascoltaCatalogoEsercizi(onUpdate: (List<EsercizioCatalogo>) -> Unit) {
        db.getReference("esercizi_db").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<EsercizioCatalogo>()
                for (snap in snapshot.children) {
                    val ex = snap.getValue(EsercizioCatalogo::class.java)
                    if (ex != null) lista.add(ex)
                }
                onUpdate(lista)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun salvaOAggiornaScheda(scheda: SchedaAllenamento) {
        val ref = db.getReference("schede_utenti")
        val key = if (scheda.idScheda.isBlank()) ref.push().key ?: return else scheda.idScheda
        val schedaFinale = scheda.copy(idScheda = key)
        ref.child(key).setValue(schedaFinale)
    }

    fun eliminaScheda(idScheda: String) {
        if (idScheda.isNotBlank()) {
            db.getReference("schede_utenti").child(idScheda).removeValue()
        }
    }

    fun ascoltaMieSchede(nomeUtente: String, onUpdate: (List<SchedaAllenamento>) -> Unit) {
        db.getReference("schede_utenti").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<SchedaAllenamento>()
                for (snap in snapshot.children) {
                    val s = snap.getValue(SchedaAllenamento::class.java)
                    if (s != null && s.nomeUtente.equals(nomeUtente, ignoreCase = true)) {
                        lista.add(s)
                    }
                }
                onUpdate(lista)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun salvaAllenamentoEseguito(log: AllenamentoCompletato) {
        val ref = db.getReference("allenamenti_completati")
        val key = log.id.ifBlank { ref.push().key ?: return }
        ref.child(key).setValue(log.copy(id = key))
    }

    fun ascoltaFeedAllenamenti(onUpdate: (List<AllenamentoCompletato>) -> Unit) {
        db.getReference("allenamenti_completati").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<AllenamentoCompletato>()
                for (snap in snapshot.children) {
                    val log = snap.getValue(AllenamentoCompletato::class.java)
                    if (log != null) lista.add(0, log)
                }
                onUpdate(lista)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun aggiornaAllenamentoSeProprietario(
        allenamento: AllenamentoCompletato,
        proprietario: String,
        onCompletato: (Boolean) -> Unit
    ) {
        if (allenamento.id.isBlank()) return onCompletato(false)
        db.getReference("allenamenti_completati").child(allenamento.id)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val salvato = currentData.child("nomeUtente").getValue(String::class.java)
                    if (!salvato.equals(proprietario, ignoreCase = true)) return Transaction.abort()
                    currentData.value = allenamento.copy(nomeUtente = salvato ?: proprietario)
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    onCompletato(error == null && committed)
                }
            })
    }

    fun eliminaAllenamentoSeProprietario(id: String, proprietario: String, onCompletato: (Boolean) -> Unit) {
        if (id.isBlank()) return onCompletato(false)
        db.getReference("allenamenti_completati").child(id)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                    val salvato = currentData.child("nomeUtente").getValue(String::class.java)
                    if (!salvato.equals(proprietario, ignoreCase = true)) return Transaction.abort()
                    currentData.value = null
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    onCompletato(error == null && committed)
                }
            })
    }

    fun ascoltaProfili(onUpdate: (List<String>) -> Unit) {
        db.getReference("profili").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onUpdate(snapshot.children.mapNotNull { it.child("nickname").getValue(String::class.java) })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun ascoltaAmici(nickname: String, onUpdate: (List<String>) -> Unit) {
        db.getReference("amicizie").child(chiaveNickname(nickname)).child("amici")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(snapshot.children.mapNotNull { it.getValue(String::class.java) }.sortedBy { it.lowercase() })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun ascoltaChiaviAmici(nickname: String, onUpdate: (Set<String>) -> Unit) {
        db.getReference("amicizie").child(chiaveNickname(nickname)).child("amici")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(snapshot.children.mapNotNull { it.key }.toSet())
                }
                override fun onCancelled(error: DatabaseError) { onUpdate(emptySet()) }
            })
    }

    fun ascoltaRichiesteAmicizia(nickname: String, onUpdate: (List<RichiestaAmicizia>) -> Unit) {
        db.getReference("amicizie").child(chiaveNickname(nickname)).child("richiesteRicevute")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(snapshot.children.mapNotNull { richiesta ->
                        val id = richiesta.key ?: return@mapNotNull null
                        val nome = richiesta.getValue(String::class.java) ?: return@mapNotNull null
                        RichiestaAmicizia(idUtente = id, nickname = nome)
                    }.sortedBy { it.nickname.lowercase() })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun ascoltaRichiesteInviate(nickname: String, onUpdate: (List<String>) -> Unit) {
        db.getReference("amicizie").child(chiaveNickname(nickname)).child("richiesteInviate")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(snapshot.children.mapNotNull { it.key })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun inviaRichiestaAmicizia(mittente: String, destinatario: String, onResult: (Boolean, String?) -> Unit) {
        val nomeMittente = mittente.trim()
        val chiaveMittente = chiaveNickname(nomeMittente)
        val chiaveDestinatario = chiaveNickname(destinatario)
        if (chiaveMittente == chiaveDestinatario) {
            onResult(false, "Non puoi inviare una richiesta a te stesso.")
            return
        }

        db.getReference("profili").child(chiaveDestinatario).get()
            .addOnSuccessListener profiloSuccess@ { profilo ->
                val nomeDestinatario = profilo.child("nickname").getValue(String::class.java)
                if (nomeDestinatario.isNullOrBlank()) {
                    onResult(false, "Questo profilo non è più disponibile.")
                    return@profiloSuccess
                }
                val root = db.getReference()
                root.child("amicizie").child(chiaveMittente).child("amici").child(chiaveDestinatario).get()
                    .addOnSuccessListener amiciziaSuccess@ { amicizia ->
                        if (amicizia.exists()) {
                            onResult(false, "Siete già amici.")
                            return@amiciziaSuccess
                        }
                        root.child("amicizie").child(chiaveMittente).child("richiesteInviate").child(chiaveDestinatario).get()
                            .addOnSuccessListener inviataSuccess@ { inviata ->
                                if (inviata.exists()) {
                                    onResult(false, "Hai già inviato una richiesta a questo utente.")
                                    return@inviataSuccess
                                }
                                root.child("amicizie").child(chiaveMittente).child("richiesteRicevute").child(chiaveDestinatario).get()
                                    .addOnSuccessListener ricevutaSuccess@ { ricevuta ->
                                        if (ricevuta.exists()) {
                                            onResult(false, "Hai già una richiesta ricevuta da questo utente: accettala dalla schermata Amici.")
                                            return@ricevutaSuccess
                                        }
                                        val aggiornamenti = mapOf(
                                            "/amicizie/$chiaveMittente/richiesteInviate/$chiaveDestinatario" to nomeDestinatario,
                                            "/amicizie/$chiaveDestinatario/richiesteRicevute/$chiaveMittente" to nomeMittente
                                        )
                                        root.updateChildren(aggiornamenti)
                                            .addOnSuccessListener { onResult(true, "Richiesta inviata a $nomeDestinatario.") }
                                            .addOnFailureListener { onResult(false, "Richiesta non inviata. Controlla la connessione e riprova.") }
                                    }
                                    .addOnFailureListener { onResult(false, "Impossibile verificare le richieste ricevute.") }
                            }
                            .addOnFailureListener { onResult(false, "Impossibile verificare le richieste inviate.") }
                    }
                    .addOnFailureListener { onResult(false, "Impossibile verificare l'amicizia.") }
            }
            .addOnFailureListener { onResult(false, "Impossibile trovare il profilo. Controlla la connessione.") }
    }

    fun accettaRichiestaAmicizia(ricevente: String, richiesta: RichiestaAmicizia, onResult: (Boolean, String?) -> Unit) {
        val chiaveRicevente = chiaveNickname(ricevente)
        val richiestaRef = db.getReference("amicizie").child(chiaveRicevente)
            .child("richiesteRicevute").child(richiesta.idUtente)
        richiestaRef.get().addOnSuccessListener { snapshot ->
            val nicknameMittente = snapshot.getValue(String::class.java)
            if (nicknameMittente.isNullOrBlank()) {
                onResult(false, "La richiesta non è più disponibile.")
                return@addOnSuccessListener
            }
            val root = db.getReference()
            val aggiornamenti = mapOf<String, Any?>(
                "/amicizie/$chiaveRicevente/amici/${richiesta.idUtente}" to nicknameMittente,
                "/amicizie/${richiesta.idUtente}/amici/$chiaveRicevente" to ricevente.trim(),
                "/amicizie/$chiaveRicevente/richiesteRicevute/${richiesta.idUtente}" to null,
                "/amicizie/${richiesta.idUtente}/richiesteInviate/$chiaveRicevente" to null,
                "/amicizie/$chiaveRicevente/richiesteInviate/${richiesta.idUtente}" to null,
                "/amicizie/${richiesta.idUtente}/richiesteRicevute/$chiaveRicevente" to null
            )
            root.updateChildren(aggiornamenti)
                .addOnSuccessListener { onResult(true, "$nicknameMittente è ora tra i tuoi amici.") }
                .addOnFailureListener { onResult(false, "Impossibile accettare la richiesta. Riprova.") }
        }.addOnFailureListener { onResult(false, "Impossibile caricare la richiesta. Controlla la connessione.") }
    }

    fun rifiutaRichiestaAmicizia(ricevente: String, richiesta: RichiestaAmicizia, onResult: (Boolean, String?) -> Unit) {
        val chiaveRicevente = chiaveNickname(ricevente)
        val aggiornamenti = mapOf<String, Any?>(
            "/amicizie/$chiaveRicevente/richiesteRicevute/${richiesta.idUtente}" to null,
            "/amicizie/${richiesta.idUtente}/richiesteInviate/$chiaveRicevente" to null
        )
        db.getReference().updateChildren(aggiornamenti)
            .addOnSuccessListener { onResult(true, "Richiesta rifiutata.") }
            .addOnFailureListener { onResult(false, "Impossibile rifiutare la richiesta. Riprova.") }
    }

    fun eliminaUtenteEDataCompleti(nomeUtente: String, onCompletato: () -> Unit) {
        db.getReference("schede_utenti").get().addOnSuccessListener { schede ->
            schede.children.forEach { child ->
                if (child.child("nomeUtente").getValue(String::class.java).equals(nomeUtente, ignoreCase = true)) child.ref.removeValue()
            }
            db.getReference("allenamenti_completati").get().addOnSuccessListener { allenamenti ->
                allenamenti.children.forEach { child ->
                    if (child.child("nomeUtente").getValue(String::class.java).equals(nomeUtente, ignoreCase = true)) child.ref.removeValue()
                }
                db.getReference("profili").child(chiaveNickname(nomeUtente)).removeValue()
                    .addOnCompleteListener { onCompletato() }
            }.addOnFailureListener { onCompletato() }
        }.addOnFailureListener { onCompletato() }
    }
    // Inserisci all'interno di FirebaseManager in FirebaseManager.kt:
fun eliminaAllenamentoCompletato(idAllenamento: String) {
    if (idAllenamento.isNotBlank()) {
        db.getReference("allenamenti_completati").child(idAllenamento).removeValue()
    }
}
}
