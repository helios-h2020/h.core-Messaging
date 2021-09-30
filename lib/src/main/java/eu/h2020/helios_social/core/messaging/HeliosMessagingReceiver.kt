package eu.h2020.helios_social.core.messaging

import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.util.concurrent.Executors

interface HeliosMessagingReceiver {
    fun receiveMessage(address: HeliosNetworkAddress, protocolId: String, data: ByteArray)
    fun receiveMessage(address: HeliosNetworkAddress, protocolId: String, fd: FileDescriptor) {
        val input = FileInputStream(fd)
        input.use {
            val bufStream = ByteArrayOutputStream()

            it.copyTo(bufStream)
            receiveMessage(address, protocolId, bufStream.toByteArray())
        }
    }
}
