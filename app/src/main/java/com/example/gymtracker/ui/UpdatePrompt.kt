package com.example.gymtracker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gymtracker.update.AppUpdate
import com.example.gymtracker.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var apkFile by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) {
            runCatching { AppUpdater.checkForUpdate(context) }.getOrNull()
        }
    }

    val latest = update ?: return
    AlertDialog(
        onDismissRequest = { if (!downloading) update = null },
        title = { Text("Aggiornamento disponibile") },
        text = {
            Column {
                Text("È disponibile la versione ${latest.version} di Gym Tracker.")
                if (latest.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(latest.notes)
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                    Text("Download dell'APK in corso…")
                }
                if (needsInstallPermission) {
                    Text("Al ritorno da Impostazioni, premi Installa per continuare.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = {
                    error = null
                    val file = apkFile
                    if (file == null) {
                        downloading = true
                        scope.launch {
                            val risultato = withContext(Dispatchers.IO) {
                                runCatching { AppUpdater.downloadUpdate(context, latest) }
                            }
                            downloading = false
                            risultato.onSuccess { scaricato ->
                                apkFile = scaricato
                                avviaInstallazione(
                                    context,
                                    scaricato,
                                    onPermissionNeeded = { needsInstallPermission = true },
                                    onError = { error = it }
                                )
                            }.onFailure { error = it.message ?: "Download non riuscito. Riprova più tardi." }
                        }
                    } else {
                        avviaInstallazione(
                            context,
                            file,
                            onPermissionNeeded = { needsInstallPermission = true },
                            onError = { error = it }
                        )
                    }
                }
            ) {
                Text(if (apkFile == null) "Scarica e installa" else "Installa")
            }
        },
        dismissButton = {
            TextButton(onClick = { update = null }, enabled = !downloading) { Text("Più tardi") }
        }
    )
}

private fun avviaInstallazione(
    context: android.content.Context,
    apkFile: File,
    onPermissionNeeded: () -> Unit,
    onError: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        onPermissionNeeded()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    runCatching { context.startActivity(AppUpdater.createInstallIntent(context, apkFile)) }
        .onFailure { onError("Android non è riuscito ad aprire l'installazione. Riprova dal pulsante Installa.") }
}
