package eu.h2020.helios_social.core.messaging.streamr

import com.google.gson.annotations.Expose

class StreamrMessageReturn : StreamrMessage {
    override var type: StreamrMessageType? = StreamrMessageType.RETURN
    override var id: String?
    override var name: String?
    override var data: Any? = null
    override var error: Any? = null

    @Expose(serialize = false, deserialize = false)
    override var close: Boolean? = null

    override fun toJson(): String =
        StreamrMessage.toJson(this)

    override fun isValid(): Boolean =
        (type == StreamrMessageType.RETURN || type == StreamrMessageType.RETURN_ERROR) &&
                !id.isNullOrEmpty()

    fun hasError(): Boolean =
        type == StreamrMessageType.RETURN_ERROR

    constructor(id: String? = null, name: String? = null, data: Any? = null, error: Any? = null) {
        this.id = id
        this.name = name
        this.data = data
        this.error = error
    }
}
