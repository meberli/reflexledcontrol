package com.flowxperts.reflexledcontrol

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

const val LAYER_A = "01"
private const val LANBOX_PORT = 777

class LanBoxViewModel(application: Application) : AndroidViewModel(application) {
    private val res = application.resources
    var connectionStatus by mutableStateOf(res.getString(R.string.status_disconnected))
    var isConnected by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)

    var availableCueLists by mutableStateOf<List<String>>(emptyList())
    var currentSpeed by mutableFloatStateOf(127f)

    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null

    fun connect(ip: String, password: String) {
        if (isConnecting) return

        viewModelScope.launch(Dispatchers.IO) {
            isConnecting = true
            connectionStatus = res.getString(R.string.status_connecting, ip)

            try {
                closeConnection()
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(ip, LANBOX_PORT), 10000)

                outStream = newSocket.getOutputStream()
                inStream = newSocket.getInputStream()

                outStream?.write("$password\r".toByteArray(Charsets.US_ASCII))
                outStream?.flush()

                socket = newSocket
                connectionStatus = res.getString(R.string.status_connected, ip)
                isConnected = true

                sendCommand("65FF")
                startReadLoop()
                sendCommand("A70001")

            } catch (e: Exception) {
                closeConnection()
                connectionStatus = res.getString(R.string.status_failed, e.localizedMessage ?: "")
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
                while (inStream?.read().also { char = it ?: -1 } != -1) {
                    val c = char.toChar()
                    if (c == '*') {
                        buffer = ""
                    } else if (c == '#') {
                        processLanBoxMessage(buffer.trim())
                    } else if (c != '>') {
                        buffer += c
                    }
                }
            } catch (e: Exception) {
                if (socket?.isClosed != true) {
                    closeConnection()
                    connectionStatus = res.getString(R.string.status_read_error)
                }
            }
        }
    }

    private fun processLanBoxMessage(message: String) {
        val cleanMsg = message.replace(" ", "")
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

    fun sendCommand(commandHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outStream?.apply {
                    write("*$commandHex#".toByteArray(Charsets.US_ASCII))
                    flush()
                } ?: throw IllegalStateException("Socket not connected")
            } catch (e: Exception) {
                closeConnection()
                connectionStatus = res.getString(R.string.status_send_error)
            }
        }
    }

    fun sendDmx(startChannel: Int, values: List<Int>) {
        val startHex = (startChannel - 1).toString(16).padStart(4, '0').uppercase()
        val countHex = values.size.toString(16).padStart(4, '0').uppercase()
        val valuesHex = values.joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
        sendCommand("CA$startHex$countHex$valuesHex")
    }

    private fun closeConnection() {
        isConnected = false
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
