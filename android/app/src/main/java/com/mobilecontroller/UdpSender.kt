package com.mobilecontroller

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles packing controller state into the binary protocol and sending it over UDP.
 * Supports dynamic network re-binding and auto-recovery on Wi-Fi reconnect.
 */
class UdpSender(
    val targetIp: String,
    val targetPort: Int,
    val deviceId: Int,
    val localBindIp: String? = null,
    var boundNetwork: android.net.Network? = null,
    val networkProvider: (() -> android.net.Network?)? = null
) {

    @Volatile var isHealthy: Boolean = true
    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var sequenceNumber = 0

    // Protocol constants
    private val PROTOCOL_MAGIC = 0x4D435031 // 'MCP1'
    private val PROTOCOL_VERSION: Byte = 1

    private var isListening = true

    init {
        reopenSocket()
        startReceiveThread()
    }

    @Synchronized
    fun reopenSocket() {
        try {
            sendSocket?.close()
        } catch (e: Exception) {}

        try {
            val net = networkProvider?.invoke() ?: boundNetwork
            boundNetwork = net
            address = InetAddress.getByName(targetIp)

            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                try {
                    net?.bindSocket(this)
                } catch (e: Exception) {
                    Log.w("UdpSender", "Failed to bind socket to network", e)
                }
                if (localBindIp != null) {
                    try {
                        bind(java.net.InetSocketAddress(InetAddress.getByName(localBindIp), 0))
                    } catch (e: Exception) {
                        try { bind(null) } catch (e2: Exception) {}
                    }
                } else {
                    try { bind(null) } catch (e: Exception) {}
                }
            }
            sendSocket = sock
            isHealthy = true
        } catch (e: Exception) {
            isHealthy = false
            Log.e("UdpSender", "Failed to initialize sendSocket", e)
        }
    }

    private fun startReceiveThread() {
        Thread {
            try {
                receiveSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(14570))
                }
                val receiveData = ByteArray(128)
                while (isListening) {
                    try {
                        val receivePacket = DatagramPacket(receiveData, receiveData.size)
                        receiveSocket?.receive(receivePacket)
                        val bytesRead = receivePacket.length
                        val buffer = receivePacket.data
                        if (bytesRead >= 6 && buffer[0] == 'R'.code.toByte() && buffer[1] == 'U'.code.toByte() &&
                            buffer[2] == 'M'.code.toByte() && buffer[3] == 'B'.code.toByte()) {
                            val largeMotor = buffer[4].toInt() and 0xFF
                            val smallMotor = buffer[5].toInt() and 0xFF
                            VibrationManager.vibrateRumble(largeMotor, smallMotor)
                        }
                    } catch (e: Exception) {
                        if (isListening) {
                            Log.e("UdpSender", "Failed to receive rumble packet", e)
                            Thread.sleep(200)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UdpSender", "Failed to bind rumble socket", e)
            }
        }.start()
    }

    fun sendState(
        buttons: Short,
        leftTrigger: Byte, rightTrigger: Byte,
        leftStickX: Short, leftStickY: Short,
        rightStickX: Short, rightStickY: Short,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        accelX: Float, accelY: Float, accelZ: Float,
        flags: Int = 0
    ) {
        var sock = sendSocket
        var addr = address
        if (sock == null || sock.isClosed || addr == null) {
            reopenSocket()
            sock = sendSocket
            addr = address
            if (sock == null || addr == null) return
        }

        try {
            val packedBuffer = ByteBuffer.allocate(61)
            packedBuffer.order(ByteOrder.LITTLE_ENDIAN)

            packedBuffer.putInt(PROTOCOL_MAGIC)
            packedBuffer.put(PROTOCOL_VERSION)
            packedBuffer.putInt(sequenceNumber++)
            packedBuffer.putInt(deviceId)
            packedBuffer.putLong(System.currentTimeMillis())
            packedBuffer.putShort(buttons)
            packedBuffer.put(leftTrigger)
            packedBuffer.put(rightTrigger)
            packedBuffer.putShort(leftStickX)
            packedBuffer.putShort(leftStickY)
            packedBuffer.putShort(rightStickX)
            packedBuffer.putShort(rightStickY)
            
            packedBuffer.putFloat(gyroX)
            packedBuffer.putFloat(gyroY)
            packedBuffer.putFloat(gyroZ)
            packedBuffer.putFloat(accelX)
            packedBuffer.putFloat(accelY)
            packedBuffer.putFloat(accelZ)
            
            packedBuffer.putInt(flags) // flags

            val data = packedBuffer.array()
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            sock.send(packet)
            isHealthy = true
        } catch (e: Exception) {
            isHealthy = false
            Log.e("UdpSender", "Failed to send packet, reopening socket", e)
            reopenSocket()
        }
    }

    fun sendDisconnect() {
        try {
            sendState(0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0f, 0f, 0f, 0f, flags = 0x8000)
        } catch (e: Exception) {}
    }

    fun close() {
        isListening = false
        try {
            sendSocket?.close()
        } catch (e: Exception) {}
        try {
            receiveSocket?.close()
        } catch (e: Exception) {}
        VibrationManager.cancelRumble()
    }
}
