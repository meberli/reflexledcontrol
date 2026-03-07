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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

const val LANBOX_PORT = 777
const val LAYER_A = "01"

class LanBoxViewModel : ViewModel() {
    var connectionStatus by mutableStateOf("Disconnected")
    var isConnecting by mutableStateOf(false)

    // UI States for the new features
    var availableCueLists by mutableStateOf<List<String>>(emptyList())
    var currentSpeed by mutableStateOf(127f) // Default 100% speed

    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null

    fun connect(ip: String, password: String) {
        if (isConnecting) return

        viewModelScope.launch(Dispatchers.IO) {
            isConnecting = true
            connectionStatus = "Connecting to $ip..."

            try {
                closeConnection()
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(ip, LANBOX_PORT), 10000)

                outStream = newSocket.getOutputStream()
                inStream = newSocket.getInputStream()

                // 1. Authenticate with password and carriage return
                outStream?.write("$password\r".toByteArray(Charsets.US_ASCII))
                outStream?.flush()

                socket = newSocket
                connectionStatus = "Connected to $ip"

                // 2. Initialize 16-bit mode to unlock full LCX memory
                sendCommand("65FF")

                // 3. Start the continuous read loop to parse LanBox replies
                startReadLoop()

                // 4. Request Cue List Directory (Start at index 1)
                sendCommand("A70001")

            } catch (e: Exception) {
                closeConnection()
                connectionStatus = "Connection Failed: ${e.localizedMessage}"
            } finally {
                isConnecting = false
            }
        }
    }

    private fun startReadLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var buffer = ""
                var char: Int

                // Read byte-by-byte from the incoming stream
                while (inStream?.read().also { char = it ?: -1 } != -1) {
                    val c = char.toChar()
                    if (c == '*') {
                        buffer = "" // Start of new LanBox message
                    } else if (c == '#') {
                        // End of message, process the payload
                        processLanBoxMessage(buffer.trim())
                    } else if (c != '>') {
                        buffer += c
                    }
                }
            } catch (e: Exception) {
                if (socket?.isClosed != true) {
                    connectionStatus = "Disconnected (Read Error)"
                    closeConnection()
                }
            }
        }
    }

    private fun processLanBoxMessage(message: String) {
        val cleanMsg = message.replace(" ", "")

        // Parse the A7 CueListGetDirectory reply.
        // It returns chunks of 6 hex chars: 4 for the Cue ID, 2 for the Step Count.
        if (cleanMsg.length % 6 == 0 && cleanMsg.matches(Regex("[0-9A-Fa-f]+"))) {
            val lists = mutableListOf<String>()
            for (i in 0 until cleanMsg.length step 6) {
                val cueHex = cleanMsg.substring(i, i + 4)
                val cueDec = cueHex.toIntOrNull(16)
                if (cueDec != null) {
                    lists.add(cueDec.toString())
                }
            }
            if (lists.isNotEmpty()) {
                // Update UI state
                availableCueLists = lists
            }
        }
    }

    // Controls the Layer Chase Speed (0 = 50%, 127 = 100%, 255 = Infinite)
    fun setSpeed(speed: Float) {
        currentSpeed = speed
        val speedHex = speed.toInt().toString(16).padStart(2, '0').uppercase()
        sendCommand("4C${LAYER_A}$speedHex")
    }

    // Starts a specific Cue List in Layer A
    fun goCueList(cueListDec: String) {
        val cueHex = cueListDec.toIntOrNull()?.toString(16)?.padStart(4, '0')?.uppercase() ?: return
        sendCommand("56${LAYER_A}$cueHex")
    }

    fun sendCommand(commandHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outStream?.apply {
                    write("*$commandHex#".toByteArray(Charsets.US_ASCII))
                    flush()
                } ?: throw IllegalStateException("Socket not connected")
            } catch (e: Exception) {
                connectionStatus = "Send Error: Please reconnect."
                closeConnection()
            }
        }
    }

    private fun closeConnection() {
        try {
            inStream?.close()
            outStream?.close()
            socket?.close()
        } catch (e: Exception) {}
        finally {
            inStream = null
            outStream = null
            socket = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeConnection()
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

    // Re-run connection anytime the IP or password successfully updates
    LaunchedEffect(ipAddress, password) {
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

            // --- CUE LIST SELECTOR ---
            var expanded by remember { mutableStateOf(false) }
            var selectedCue by remember { mutableStateOf("Select Cue List") }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedCue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Available Cue Lists") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (viewModel.availableCueLists.isEmpty()) {
                        DropdownMenuItem(text = { Text("No Cue Lists Found") }, onClick = { expanded = false })
                    } else {
                        viewModel.availableCueLists.forEach { cue ->
                            DropdownMenuItem(
                                text = { Text("Cue List $cue") },
                                onClick = {
                                    selectedCue = "Cue List $cue"
                                    expanded = false
                                    viewModel.goCueList(cue)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- SPEED SLIDER ---
            Text(text = "Chase Speed: ${(viewModel.currentSpeed / 127f * 100).toInt()}%")
            Slider(
                value = viewModel.currentSpeed,
                onValueChange = { viewModel.setSpeed(it) },
                valueRange = 0f..255f, // 0 = 50%, 127 = 100%, 255 = Infinite
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

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
        Text(text = buttonText, style = MaterialTheme.typography.titleMedium)
    }
}