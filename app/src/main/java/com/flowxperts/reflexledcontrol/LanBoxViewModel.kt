package com.flowxperts.reflexledcontrol

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LAYER_A = "01"
private const val LANBOX_PORT = 777

data class LogEntry(
    val timestamp: String,
    val direction: Direction,
    val message: String
) {
    enum class Direction { SENT, RECEIVED, ERROR }
}

/**
 * ViewModel for managing connection and communication with the LanBox DMX controller.
 */
class LanBoxViewModel(application: Application) : AndroidViewModel(application) {
    private val res = application.resources
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // Observable UI State
    var connectionStatus by mutableStateOf(res.getString(R.string.status_disconnected))
        private set
    var isConnected by mutableStateOf(false)
        private set
    var isConnecting by mutableStateOf(false)
        private set

    var availableCueLists by mutableStateOf<List<String>>(emptyList())
        private set
    var currentSpeed by mutableFloatStateOf(127f)
        private set

    val commandLog = mutableStateListOf<LogEntry>()

    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private var readJob: Job? = null

    private fun addLog(direction: LogEntry.Direction, message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            commandLog.add(0, LogEntry(timeFormat.format(Date()), direction, message))
            if (commandLog.size > 100) {
                commandLog.removeAt(commandLog.lastIndex)
            }
        }
    }

    /**
     * Connects to the LanBox at the specified IP address.
     */
    fun connect(ip: String, password: String) {
        if (isConnecting) return

        viewModelScope.launch(Dispatchers.IO) {
            isConnecting = true
            connectionStatus = res.getString(R.string.status_connecting, ip)
            addLog(LogEntry.Direction.SENT, "Connecting to $ip...")

            try {
                closeConnection()
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(ip, LANBOX_PORT), 5000)

                outStream = newSocket.getOutputStream()
                inStream = newSocket.getInputStream()

                // Send login password
                sendCommandInternal("$password\r")

                socket = newSocket
                connectionStatus = res.getString(R.string.status_connected, ip)
                isConnected = true
                addLog(LogEntry.Direction.RECEIVED, "Connected")

                // Initial setup commands
                sendCommand("65FF") // Set transparent mode
                startReadLoop()
                sendCommand("A70001") // Request cue list

            } catch (e: Exception) {
                closeConnection()
                connectionStatus = res.getString(R.string.status_failed, e.localizedMessage ?: "")
                addLog(LogEntry.Direction.ERROR, "Connection failed: ${e.message}")
            } finally {
                isConnecting = false
            }
        }
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = inStream?.bufferedReader(Charsets.US_ASCII) ?: return@launch
                val buffer = StringBuilder()
                
                while (isConnected) {
                    val charInt = reader.read()
                    if (charInt == -1) break

                    val c = charInt.toChar()
                    when (c) {
                        '*' -> buffer.setLength(0) // Start of message
                        '#' -> { // End of message
                            val msg = buffer.toString().trim()
                            addLog(LogEntry.Direction.RECEIVED, "*$msg#")
                            processLanBoxMessage(msg)
                            buffer.setLength(0)
                        }
                        '>' -> Unit // Ignore prompt
                        else -> buffer.append(c)
                    }
                }
            } catch (_: Exception) {
                if (isConnected) {
                    handleError(res.getString(R.string.status_read_error))
                }
            }
        }
    }

    private fun processLanBoxMessage(message: String) {
        val cleanMsg = message.replace(" ", "")
        // Simple heuristic for cue list messages (multiple of 6 hex chars)
        if (cleanMsg.length >= 6 && cleanMsg.length % 6 == 0 && cleanMsg.matches(Regex("[0-9A-Fa-f]+"))) {
            val lists = mutableListOf<String>()
            for (i in 0 until cleanMsg.length step 6) {
                val cueHex = cleanMsg.substring(i, i + 4)
                cueHex.toIntOrNull(16)?.let { lists.add(it.toString()) }
            }
            if (lists.isNotEmpty()) {
                availableCueLists = lists
            }
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        val speedHex = speed.toInt().toString(16).padStart(2, '0').uppercase()
        sendCommand("4C${LAYER_A}$speedHex")
    }

    fun goCueList(cueListDec: String) {
        val cueHex = cueListDec.toIntOrNull()?.toString(16)?.padStart(4, '0')?.uppercase() ?: return
        sendCommand("56${LAYER_A}$cueHex")
    }

    /**
     * Sends a raw hex command to the LanBox wrapped in * and # protocol markers.
     */
    fun sendCommand(commandHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendCommandInternal("*$commandHex#")
            } catch (_: Exception) {
                handleError(res.getString(R.string.status_send_error))
            }
        }
    }

    private fun sendCommandInternal(fullCommand: String) {
        outStream?.apply {
            write(fullCommand.toByteArray(Charsets.US_ASCII))
            flush()
            addLog(LogEntry.Direction.SENT, fullCommand)
        } ?: throw IllegalStateException("Not connected")
    }

    /**
     * Updates DMX channels starting from [startChannel].
     */
    fun sendDmx(startChannel: Int, values: List<Int>) {
        val startHex = (startChannel - 1).toString(16).padStart(4, '0').uppercase()
        val countHex = values.size.toString(16).padStart(4, '0').uppercase()
        val valuesHex = values.joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
        sendCommand("CA$startHex$countHex$valuesHex")
    }

    private fun handleError(message: String) {
        connectionStatus = message
        addLog(LogEntry.Direction.ERROR, message)
        closeConnection()
    }

    private fun closeConnection() {
        isConnected = false
        readJob?.cancel()
        try {
            inStream?.close()
            outStream?.close()
            socket?.close()
        } catch (_: Exception) {}
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
