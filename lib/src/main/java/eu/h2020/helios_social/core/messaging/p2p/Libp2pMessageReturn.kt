package eu.h2020.helios_social.core.messaging.p2p

import com.google.gson.annotations.Expose

class Libp2pMessageReturn : Libp2pMessage {
    override var type: Libp2pMessageType? = Libp2pMessageType.RETURN
    override var id: String?
    override var name: String?
    override var data: Any? = null
    override var error: Any? = null

    @Expose(serialize = false, deserialize = false)
    override var close: Boolean? = null

    override fun toJson(): String =
        Libp2pMessage.toJson(this)

    override fun isValid(): Boolean =
        (type == Libp2pMessageType.RETURN || type == Libp2pMessageType.RETURN_ERROR) &&
                !id.isNullOrEmpty()

    fun hasError(): Boolean =
        type == Libp2pMessageType.RETURN_ERROR

    constructor(id: String? = null, name: String? = null, data: Any? = null, error: Any? = null) {
        this.id = id
        this.name = name
        this.data = data
        this.error = error
    }
}
