package eu.h2020.helios_social.core.messaging

import java.util.concurrent.Future

interface HeliosDirectMessaging {
    fun resolve(egoId: String): HeliosNetworkAddress
    fun resolveFuture(egoId: String): Future<HeliosNetworkAddress>

    fun sendTo(address: HeliosNetworkAddress, protocolId: String, data: ByteArray)
    fun sendToFuture(address: HeliosNetworkAddress, protocolId: String, data: ByteArray): Future<Unit>
    fun addReceiver(protocolId: String, receiver: HeliosMessagingReceiver)
    fun removeReceiver(protocolId: String)
}
