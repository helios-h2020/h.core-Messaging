package eu.h2020.helios_social.core.messaging.streamr

class StreamrMessageStream : StreamrMessage {
    override var type: StreamrMessageType? = StreamrMessageType.STREAM
    override var id: String?
    override var name: String?
    override var data: ByteArray? = null
    override var close: Boolean? = null
    override var error: Any? = null

    override fun toJson(): String =
        StreamrMessage.toJson(this)

    override fun isValid(): Boolean =
        type == StreamrMessageType.STREAM &&
                !id.isNullOrEmpty() &&
                data != null

    constructor(id: String? = null, name: String? = null, close: Boolean? = null, data: ByteArray? = null) {
        this.id = id
        this.name = name
        this.data = data ?: emptyArray<Byte>().toByteArray()
        this.close = close
    }
}

