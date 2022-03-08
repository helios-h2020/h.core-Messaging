package eu.h2020.helios_social.core.messaging.streamr

import com.google.gson.annotations.Expose
import java.nio.charset.StandardCharsets

class StreamrMessageEvent : StreamrMessage {
    override var type: StreamrMessageType? = StreamrMessageType.EVENT
    override var id: String?
    override var name: String?
    override var data: ByteArray? = null
    override var error: Any? = null

    @Expose(serialize = false, deserialize = false)
    override var close: Boolean? = null

    override fun toJson(): String =
        StreamrMessage.toJson(this)

    override fun isValid(): Boolean =
            type == StreamrMessageType.EVENT &&
                    !name.isNullOrEmpty() &&
                    !id.isNullOrEmpty()

    constructor(id: String? = null, name: String? = null, data: ByteArray? = null) {
        this.id = id
        this.name = name
        this.data = data
    }

    constructor(id: String? = null, name: String? = null, data: String) {
        this.id = id
        this.name = name
        this.data = data.toByteArray(StandardCharsets.UTF_8)
    }

    var stringData : String
        get() = String(data ?: ByteArray(0), StandardCharsets.UTF_8)
        set(str) {
            data = str.toByteArray(StandardCharsets.UTF_8)
        }

    override fun toString(): String = "NodejsMessageEvent{id=$id, name=$name, data=${data?.asList()}}"
}
