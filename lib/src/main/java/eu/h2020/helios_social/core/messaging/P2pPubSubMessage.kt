package eu.h2020.helios_social.core.messaging

import java.nio.charset.StandardCharsets

data class P2pPubSubMessage(
    var networkId: String,
    var subscriptionId: String,
    var topic: List<String>,
    var data: ByteArray
) {
    fun getStringData(): String = String(data, StandardCharsets.UTF_8)
    fun setStringData(str: String) {
        data = str.toByteArray(StandardCharsets.UTF_8)
    }
}