package eu.h2020.helios_social.core.messaging

data class P2pPeerInfoMessage(
    var networkId: String,
    var networkAddr: List<String>,
    var proto: List<String>,
    var publicKey: String?
)
