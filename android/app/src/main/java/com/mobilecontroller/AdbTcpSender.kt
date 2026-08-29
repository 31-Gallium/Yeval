package com.mobilecontroller

import android.util.Log
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles sending controller state over a local TCP socket, which is tunneled
 * over the USB cable via ADB Port Forwarding.
 */
class AdbTcpSender(
    val targetIp: String,
    val targetPort: Int,
    val deviceId: Int,
    val onReload: ((String) -> Unit)? = null,
    val boundNetwork: android.net.Network? = null
) {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var sequenceNumber = 0
    var isConnected = false

    private val PROTOCOL_MAGIC = 0x4D435031 // 'MCP1'
    private val PROTOCOL_VERSION: Byte = 1

    private var lastConnectAttempt = 0L

    private val packetQueue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(2)
    private var writerThread: Thread? = null
    private var readerThread: Thread? = null

    fun connect() {
        if (isConnected) return

        val now = System.currentTimeMillis()
        if (now - lastConnectAttempt < 2000) return // Throttle connection attempts
        lastConnectAttempt = now

        Thread {
            try {
                val s = Socket()
                try {
                    boundNetwork?.bindSocket(s)
                } catch (e: Exception) {}
                s.tcpNoDelay = true
                s.connect(java.net.InetSocketAddress(targetIp, targetPort), 1000)
                socket = s
                outputStream = s.getOutputStream()
                isConnected = true
                Log.i("AdbTcpSender", "Successfully connected to TCP server at $targetIp:$targetPort")
                startWriterThread()
                startReaderThread()
            } catch (e: Exception) {
                // Fails silently if the tunnel isn't established yet
                isConnected = false
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
        if (!isConnected || outputStream == null) {
            connect()
            return
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
            
            // Non-blocking write to queue. If queue is full, oldest packet is discarded.
            if (!packetQueue.offer(data)) {
                packetQueue.poll() // drop oldest
                packetQueue.offer(data)
            }

        } catch (e: Exception) {
            Log.e("AdbTcpSender", "Failed to queue packet for ADB tunnel", e)
        }
    }

    private fun startWriterThread() {
        writerThread = Thread {
            while (isConnected) {
                try {
                    val data = packetQueue.take()
                    outputStream?.write(data)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("AdbTcpSender", "Failed to send packet over ADB tunnel", e)
                    close()
                    break
                }
            }
        }
        writerThread?.start()
    }

    private fun startReaderThread() {
        readerThread = Thread {
            val input = socket?.getInputStream() ?: return@Thread
            val buffer = ByteArray(1024)
            while (isConnected) {
                try {
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        val str = String(buffer, 0, bytesRead)
                        if (str.contains("RELO:")) {
                            val slotId = str.substringAfter("RELO:").substringBefore("\n").substringBefore("\u0000")
                            onReload?.invoke(slotId)
                        } else {
                            for (i in 0 until bytesRead - 3) {
                                if (buffer[i] == 'R'.code.toByte() && buffer[i+1] == 'U'.code.toByte() &&
                                    buffer[i+2] == 'M'.code.toByte() && buffer[i+3] == 'B'.code.toByte() && i + 5 < bytesRead) {
                                    val large = buffer[i+4].toInt() and 0xFF
                                    val small = buffer[i+5].toInt() and 0xFF
                                    VibrationManager.vibrateRumble(large, small)
                                    break
                                }
                            }
                        }
                    } else if (bytesRead == -1) {
                        close()
                        break
                    }
                } catch (e: Exception) {
                    close()
                    break
                }
            }
        }
        readerThread?.start()
    }

    fun sendDisconnect() {
        if (isConnected) {
            try {
                sendState(0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0f, 0f, 0f, 0f, flags = 0x8000)
                Thread.sleep(20) // Give writer thread a moment to flush the disconnect packet
            } catch (e: Exception) {}
        }
    }

    fun close() {
        isConnected = false
        writerThread?.interrupt()
        writerThread = null
        readerThread = null
        try {
            outputStream?.close()
        } catch (e: Exception) {}
        outputStream = null

        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null
    }
}
