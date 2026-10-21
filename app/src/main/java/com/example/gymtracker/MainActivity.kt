package com.example.gymtracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.gymtracker.ui.LoginScreen
import com.example.gymtracker.ui.MainDashboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6D00),
                    secondary = Color(0xFFFFAB40),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GymBuddyPrefs", Context.MODE_PRIVATE) }
    var utenteLoggato by remember { mutableStateOf(sharedPref.getString("NOME_UTENTE", null)) }

    if (utenteLoggato.isNullOrBlank()) {
        LoginScreen(onLoginSuccess = { nuovoNome ->
            sharedPref.edit().putString("NOME_UTENTE", nuovoNome).apply()
            utenteLoggato = nuovoNome
        })
    } else {
        MainDashboard(
            nomeUtente = utenteLoggato!!,
            onLogout = {
                sharedPref.edit().remove("NOME_UTENTE").apply()
                utenteLoggato = null
            }
        )
    }
}
