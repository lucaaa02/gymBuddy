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
fun LoginScreen(onLoginSuccess: (String) -> Unit, onBack: () -> Unit = {}) {
    var inputNome by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var inCorso by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("GYM BUDDY 🏋️‍♂️", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Accedi con nickname e password", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = inputNome,
            onValueChange = { inputNome = it; messaggio = null },
            label = { Text("Nome / Nickname") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; messaggio = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            enabled = !inCorso,
            modifier = Modifier.fillMaxWidth()
        )
        messaggio?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (inputNome.isBlank()) messaggio = "Inserisci il tuo nickname."
                else if (password.isBlank()) messaggio = "Inserisci la password."
                else {
                    inCorso = true
                    FirebaseManager.accediProfilo(inputNome, password) { nome, errore ->
                        inCorso = false
                        if (nome != null) onLoginSuccess(nome) else messaggio = errore
                    }
                }
            },
            enabled = !inCorso,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (inCorso) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("ACCEDI", fontWeight = FontWeight.Bold, color = Color.Black)
        }
        TextButton(onClick = onBack, enabled = !inCorso) { Text("Indietro") }
    }
}
