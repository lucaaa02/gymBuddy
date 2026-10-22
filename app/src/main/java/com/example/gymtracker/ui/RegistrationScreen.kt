package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtracker.data.FirebaseManager

@Composable
fun RegistrationScreen(onRegistrationSuccess: (String) -> Unit, onBack: () -> Unit = {}) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confermaPassword by remember { mutableStateOf("") }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var inCorso by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("GYM BUDDY 🏋️‍♂️", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Crea il tuo profilo", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Scegli un nickname unico e una password.", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it; messaggio = null },
            label = { Text("Nickname") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !inCorso,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; messaggio = null },
            label = { Text("Password (almeno 8 caratteri)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            enabled = !inCorso,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confermaPassword,
            onValueChange = { confermaPassword = it; messaggio = null },
            label = { Text("Conferma password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            enabled = !inCorso,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Hai già un profilo? Usa lo stesso nickname per collegare la password ai tuoi dati esistenti.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        messaggio?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (nickname.isBlank()) {
                    messaggio = "Inserisci un nickname."
                } else if (password.length < 8) {
                    messaggio = "La password deve contenere almeno 8 caratteri."
                } else if (password != confermaPassword) {
                    messaggio = "Le password non corrispondono."
                } else {
                    inCorso = true
                    FirebaseManager.registraProfilo(nickname, password) { registrato, errore ->
                        inCorso = false
                        if (registrato) onRegistrationSuccess(nickname.trim()) else messaggio = errore
                    }
                }
            },
            enabled = !inCorso,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (inCorso) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("CREA PROFILO", fontWeight = FontWeight.Bold, color = Color.Black)
        }
        TextButton(onClick = onBack, enabled = !inCorso) { Text("Indietro") }
    }
}
