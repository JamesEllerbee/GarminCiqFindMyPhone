package com.joey.findmyphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    // Refreshed in onResume (the user may come back from the battery settings screen).
    private var batteryUnrestricted by mutableStateOf(true)

    override fun onResume() {
        super.onResume()
        batteryUnrestricted =
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    @Composable
    private fun MainScreen() {
        var running by remember { mutableStateOf(FindMyPhoneService.running) }
        var ringSeconds by remember { mutableStateOf(AppPrefs.ringSeconds(this)) }

        fun startService() {
            try {
                ContextCompat.startForegroundService(
                    this, Intent(this, FindMyPhoneService::class.java)
                )
                AppPrefs.setServiceEnabled(this, true)
                running = true
            } catch (t: Throwable) {
                running = false
                ErrorReporter.report(this, "Failed to start FindMyPhoneService", t)
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { startService() } // start either way; the notification just won't be visible if denied

        ErrorReporter.latest?.let { error ->
            running = false
            ErrorDialog(error)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (running) "Listening for your watch" else "Service stopped",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(
                modifier = Modifier.padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(enabled = !running, onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startService()
                    else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Start") }

                OutlinedButton(enabled = running, onClick = {
                    AppPrefs.setServiceEnabled(this@MainActivity, false)
                    stopService(Intent(this@MainActivity, FindMyPhoneService::class.java))
                    running = false
                }) { Text("Stop") }

                if (!batteryUnrestricted) {
                    TextButton(onClick = {
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:$packageName"),
                                )
                            )
                        } catch (t: Throwable) {
                            ErrorReporter.report(this@MainActivity, "Battery settings unavailable", t)
                        }
                    }) { Text("Allow background activity") }
                }

                Text("Ring for", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPrefs.RING_SECONDS_OPTIONS.forEach { seconds ->
                        FilterChip(
                            selected = ringSeconds == seconds,
                            onClick = {
                                ringSeconds = seconds
                                AppPrefs.setRingSeconds(this@MainActivity, seconds)
                            },
                            label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}m") },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ErrorDialog(error: String) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { ErrorReporter.clear(this) },
            title = { Text("Error") },
            text = {
                SelectionContainer {
                    Text(
                        text = error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ErrorReporter.clear(this) }) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(error)) }) { Text("Copy") }
            },
        )
    }
}
