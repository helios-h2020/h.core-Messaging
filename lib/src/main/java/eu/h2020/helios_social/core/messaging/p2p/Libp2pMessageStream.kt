package eu.h2020.helios_social.core.messaging.p2p

class Libp2pMessageStream : Libp2pMessage {
    override var type: Libp2pMessageType? = Libp2pMessageType.STREAM
    override var id: String?
    override var name: String?
    override var data: ByteArray? = null
    override var close: Boolean? = null
    override var error: Any? = null

    override fun toJson(): String =
        Libp2pMessage.toJson(this)

    override fun isValid(): Boolean =
        type == Libp2pMessageType.STREAM &&
                !id.isNullOrEmpty() &&
                data != null

    constructor(id: String? = null, name: String? = null, close: Boolean? = null, data: ByteArray? = null) {
        this.id = id
        this.name = name
        this.data = data ?: emptyArray<Byte>().toByteArray()
        this.close = close
    }
}

