package com.example.reflexledcontrol

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

const val LANBOX_PORT = 777
const val LAYER_A = "01"

class LanBoxViewModel : ViewModel() {
    var connectionStatus by mutableStateOf("Disconnected")
    var isConnecting by mutableStateOf(false)

    private var socket: Socket? = null
    private var outStream: OutputStream? = null

    fun connect(ip: String, password: String) {
        if (isConnecting) return

        viewModelScope.launch(Dispatchers.IO) {
            isConnecting = true
            connectionStatus = "Connecting to $ip..."

            try {
                socket?.close()
                socket = Socket()
                socket?.connect(InetSocketAddress(ip, LANBOX_PORT), 10000)
                outStream = socket?.getOutputStream()

                outStream?.write("$password\r".toByteArray(Charsets.US_ASCII))

                connectionStatus = "Connected to $ip"
            } catch (e: Exception) {
                connectionStatus = "Connection Failed: ${e.message}"
            } finally {
                isConnecting = false
            }
        }
    }

    fun sendCommand(commandHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullCommand = "*$commandHex#"
                outStream?.write(fullCommand.toByteArray(Charsets.US_ASCII))
            } catch (e: Exception) {
                connectionStatus = "Send Error: Please reconnect."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket?.close()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = LanBoxViewModel()

        setContent {
            MaterialTheme {
                LanBoxAppScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanBoxAppScreen(viewModel: LanBoxViewModel) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("LanBoxPrefs", Context.MODE_PRIVATE)

    var ipAddress by remember { mutableStateOf(sharedPrefs.getString("IP", "192.168.1.77") ?: "192.168.1.77") }
    var password by remember { mutableStateOf(sharedPrefs.getString("PASS", "777") ?: "777") }

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.connect(ipAddress, password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "LanBox Remote") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.isConnecting) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }

            // Extracted color logic for compiler safety
            val statusColor = if (viewModel.connectionStatus.startsWith("Connected")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            Text(
                text = viewModel.connectionStatus,
                color = statusColor,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            PlaybackButton("▶ Go (Cue 1)") { viewModel.sendCommand("56${LAYER_A}0001") }
            PlaybackButton("⏸ Pause") { viewModel.sendCommand("58${LAYER_A}") }
            PlaybackButton("⏵ Resume") { viewModel.sendCommand("59${LAYER_A}") }
            PlaybackButton("⏭ Next Step") { viewModel.sendCommand("5A${LAYER_A}") }
            PlaybackButton("⏮ Prev Step") { viewModel.sendCommand("5B${LAYER_A}") }
            PlaybackButton("⏹ Clear Layer") { viewModel.sendCommand("57${LAYER_A}") }
        }
    }

    if (showSettingsDialog) {
        var tempIp by remember { mutableStateOf(ipAddress) }
        var tempPass by remember { mutableStateOf(password) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(text = "Network Settings") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempIp,
                        onValueChange = { tempIp = it },
                        label = { Text(text = "LanBox IP Address") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPass,
                        onValueChange = { tempPass = it },
                        label = { Text(text = "Password") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit()
                            .putString("IP", tempIp)
                            .putString("PASS", tempPass)
                            .apply()

                        ipAddress = tempIp
                        password = tempPass
                        showSettingsDialog = false
                        viewModel.connect(ipAddress, password)
                    }
                ) {
                    Text(text = "Save & Reconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
fun PlaybackButton(buttonText: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(56.dp)
    ) {
        // Explicitly naming 'text =' fixes the compiler ambiguity
        Text(text = buttonText, style = MaterialTheme.typography.titleMedium)
    }
}