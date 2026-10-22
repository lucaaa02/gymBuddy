package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager
import com.example.gymtracker.models.AllenamentoCompletato

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGestioneAdmin() {
    var feedAllenamenti by remember { mutableStateOf(listOf<AllenamentoCompletato>()) }
    var profili by remember { mutableStateOf(listOf<String>()) }
    var utenteDaEliminare by remember { mutableStateOf<String?>(null) }
    var ricercaTesto by remember { mutableStateOf("") }

    var passwordInput by remember { mutableStateOf("") }
    var errorePassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseManager.ascoltaFeedAllenamenti { feedAllenamenti = it }
        FirebaseManager.ascoltaProfili { profili = it }
    }

    val listaUtentiInfo = remember(feedAllenamenti, profili, ricercaTesto) {
        val mappaUtenti = mutableMapOf<String, Pair<String, Int>>()
        profili.forEach { nickname ->
            mappaUtenti[nickname.lowercase()] = nickname to 0
        }
        feedAllenamenti.forEach { log ->
            val nome = log.nomeUtente.trim()
            val chiave = nome.lowercase()
            val utente = mappaUtenti[chiave]
            mappaUtenti[chiave] = (utente?.first ?: nome.uppercase()) to ((utente?.second ?: 0) + 1)
        }

        mappaUtenti.values
            .filter { it.first.contains(ricercaTesto.trim(), ignoreCase = true) }
            .sortedBy { it.first.uppercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑 Pannello Amministratore", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Area riservata a Luca per eliminare utenti e pulire il database.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = ricercaTesto,
                    onValueChange = { ricercaTesto = it },
                    label = { Text("Cerca utente per nome...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Utenti Registrati (${listaUtentiInfo.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))

        if (listaUtentiInfo.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "Nessun utente trovato.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listaUtentiInfo) { pair ->
                    val nomeUtente = pair.first
                    val numSessioni = pair.second

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(nomeUtente, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                Text("$numSessioni Allenamenti registrati", fontSize = 11.sp, color = Color.Gray)
                            }

                            Button(
                                onClick = {
                                    passwordInput = ""
                                    errorePassword = false
                                    utenteDaEliminare = nomeUtente
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("ELIMINA UTENTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // POPUP DI CONFERMA ELIMINAZIONE CON PASSWORD DI SICUREZZA
    if (utenteDaEliminare != null) {
        AlertDialog(
            onDismissRequest = {
                utenteDaEliminare = null
                passwordInput = ""
                errorePassword = false
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput == "Lugia1927") {
                            val target = utenteDaEliminare!!
                            utenteDaEliminare = null
                            passwordInput = ""
                            errorePassword = false
                            FirebaseManager.eliminaUtenteEDataCompleti(target) {
                                // Eliminazione avvenuta
                            }
                        } else {
                            errorePassword = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("CONFERMA E ELIMINA", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    utenteDaEliminare = null
                    passwordInput = ""
                    errorePassword = false
                }) {
                    Text("Annulla", color = Color.White)
                }
            },
            title = { Text("Conferma Eliminazione Admin 🔒", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Inserisci la password di sicurezza per eliminare definitivamente l'utente '$utenteDaEliminare' e tutti i suoi dati dal database:",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorePassword = false
                        },
                        label = { Text("Password Admin") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = errorePassword,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorePassword) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("❌ Password errata! Riprova.", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
