package eu.h2020.helios_social.core.messaging

import eu.h2020.helios_social.core.messaging.HeliosMessage

class HeliosMessageLibp2pPubSub : HeliosMessage {
    var networkAddress: HeliosNetworkAddress?
        private set

    constructor(
        msg: String?,
        mediaFileName: String? = null,
        networkAddress: HeliosNetworkAddress? = null
    ) : super(msg, mediaFileName) {
        this.networkAddress = networkAddress
    }
}