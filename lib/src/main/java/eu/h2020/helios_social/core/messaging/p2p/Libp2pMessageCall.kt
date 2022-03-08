package eu.h2020.helios_social.core.messaging.p2p

import com.google.gson.annotations.Expose


class Libp2pMessageCall : Libp2pMessage {
    override var type: Libp2pMessageType? = Libp2pMessageType.CALL
    override var id: String?
    override var name: String?
    override var data: List<Any?>? = null
    override var error: Any? = null

    @Expose(serialize = false, deserialize = false)
    @Transient
    override var close: Boolean? = null

    override fun toJson(): String =
        Libp2pMessage.toJson(this)

    override fun isValid(): Boolean =
        type == Libp2pMessageType.CALL &&
                !name.isNullOrEmpty() &&
                !id.isNullOrEmpty() &&
                data != null

    constructor(id: String? = null, name: String? = null, data: List<Any?>? = null) {
        this.id = id
        this.name = name
        this.data = data ?: emptyList()
    }
}
