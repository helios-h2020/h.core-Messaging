/*jshint esversion: 9 */
'use string';

const {StreamrClient, StreamPermission} = require('streamr-client').StreamrClient;

const { BufferList, finished } = require('bl');
const ospipe = require("./pipe");
const events = require("events");
const node_stream = require("stream");
const util = require("util");
const fs = require("fs");

class ApiController {
    constructor(io) {
        this.client = null;
	this.streamrClient = null;
        this.io = io;

        this.calls = io.calls;
        this.events = io.events;
        this.event = io.event.bind(io);

        this.subscriptionId = 1;
        this.subscriptions = new Map();

        io.calls.on('start', this._wrapAsyncToCallback(this.start));
        // stop is intended to "crash" nodejs as an unhandled exception
        io.calls.on('stop', this.stop.bind(this));
        //io.calls.on('stop', this._wrapAsyncToCallback(this.stop));
        io.calls.on('find-service', this._wrapAsyncToCallback(this.findService));
        io.calls.on('find-peer', this._wrapAsyncToCallback(this.findPeer));
        io.calls.on('provide-service', this._wrapAsyncToCallback(this.provideService));
        io.calls.on('publish', this._wrapAsyncToCallback(this.publish));
        io.calls.on('subscribe', this._wrapAsyncToCallback(this.subscribe));
        io.calls.on('unsubscribe', this._wrapAsyncToCallback(this.unsubscribe));

        io.calls.on('resolve', this._wrapAsyncToCallback(this.resolve));
        io.calls.on('send-to', this._wrapAsyncToCallback(this.sendTo));
        io.calls.on('add-receiver', this._wrapAsyncToCallback(this.addProtocolReceiver));
        io.calls.on('remove-receiver', this._wrapAsyncToCallback(this.removeProtocolReceiver));
    }

    _wrapAsyncToCallback(method) {
        method = method.bind(this);
        let fn = (args, cb) => method(...args)
            .then(rv => cb(rv))
            .catch(err => {
                //console.error("Error in call:", err);
                cb(null, err);
            });

        return fn;
    }

    _formatPeerId(peer) {
        let peerId = peer.toJSON();
        let multiaddrs = this.client.getPeerAddr(peer);

        return {
            networkId: peerId.id,
            networkAddr: multiaddrs.map((addr) => addr.toString()),
            proto: Array.from(peer.protocols || []),
            pubkey: peerId.pubKey,
        };
    }

    _formatNetworkAddress(peerData, egoId = null) {
        if (!peerData || !peerData.id) {
            log("Format empty peer", peerData);
        }

        let peerId = (peerData && peerData.id) ? peerData.id.toJSON() : null;

        return {
            egoId: egoId || null,
            networkId: peerId.id || null,
            networkAddress: (peerData.multiaddrs || []).map((addr) => addr.toString()),
            publicKey: peerId.pubKey || null,
            privateKey: peerId.privateKey || null,
        };
    }

    _dataToArray(data) {
        let arrayBuf;

        if (Buffer.isBuffer(data)) {
            arrayBuf = Array.from(data);
        } else if (data instanceof Uint8Array) {
            arrayBuf = Array.from(data);
        } else if (typeof data === "string") {
            // encode string in utf8
            arrayBuf = Array.from(Buffer.from(data));
        } else if (Array.isArray(data)) {
            arrayBuf = data;
        } else {
            arrayBuf = Array.from(data);
        }

        return arrayBuf;
    }

    _buildStreamAddress(topic) {
	let arr = topic.split("/");
	let name = arr[arr.length - 1];
	/* TODO: Give address of the public streamr.network stream */
	let streamAddress = '0x0000000000000000000000000000000000000000/' + name;
	return streamAddress;
    }

    async stop() {
        if (this.client) {
            await this.client.stop();
        }

        this.io.destroy();

        throw "Stopping client.";
    }

    async start(identity, options) {
	console.error("STREAMR/start");
	options = options || {};
	let walletAddress = options.walletAddress;
	let walletPrivateKey = options.walletPrivateKey;
	if ((walletAddress == "empty") || (walletPrivateKey == "empty")) {
	    const wallet = StreamrClient.generateEthereumAccount();
	    walletAddress = wallet.address;
	    walletPrivateKey = wallet.privateKey;
	}
	/* TODO: Replace USERNAME, PASSWORD, HOSTNAME and PORT with real values */
	/* TODO: Contact streamr.network to get this information */
	this.streamrClient = new StreamrClient({
	    auth: { privateKey: walletPrivateKey },
	    network: {
		stunUrls: [
                    "stun:HOSTNAME:PORT",
                    "turn:USERNAME:PASSWORD@HOSTNAME:PORT"
		]
            }
	});
	console.error("STREAMR/address: " + walletAddress);
        return {
            networkId: walletAddress,
            privateNetworkId: walletAddress,
            egoId: "0xffff0000",
	    walletAddress: walletAddress,
	    walletPrivateKey: walletPrivateKey,
        };
    }

    async findService(srvName) {
        let eps = [];
        for await (let peerData of this.client.findService(srvName)) {
            let peerStr = peerData.id ? peerData.id.toB58String() : null;

            for (let ma of (peerData.multiaddrs || [])) {
                if (peerStr && ma.getPeerId() !== peerStr) {
                    ma = ma.encapsulate('/p2p/' + peerStr);
                }

                let maStr = ma.toString();
                eps.push(maStr);
            }
        }

        return eps;
    }

    async findPeer(peer, options) {
        let peerData = await this.client.findPeer(peer, options);

        if (!peerData || !peerData.multiaddrs) {
            return [];
        }

        return peerData.multiaddrs.map(ma => ma.toString());
    }

    async provideService(srcName) {
	console.error("STREAMR/provideService (no-op now): " + srcName);
	// Low-level stuff disabled 25.2. 2022
        // await this.client.provideService(srcName);
    }

    async publish(topic, message) {
	console.error("STREAMR/publish: " + topic + " " + message);
	if (topic === "/helios/pubsub/helios/tag/helios") {
	    console.error("EgoId tag announcement not sent");
	} else {
	    const streamAddress = this._buildStreamAddress(topic);
	    console.error("Stream address is " + streamAddress);
	    const heliosStream = await this.streamrClient.getStream(streamAddress);
	    await heliosStream.publish(message);
	}
     }

    async subscribe(topics) {
	console.error("STREAMR/subscribe: " + topics);
        let api = this;
        let subId = String(api.subscriptionId++);
        let handler = (msg) => {
            api.event('pubsub:message', {
                networkId: msg.from,
                subcriptionId: subId,
                topic: msg.topicIDs,
                data: api._dataToArray(msg.data),
            }, subId);
        };

        handler.close = () => {
	    let arr =  Array.isArray(topics) ? topics : [topics];
            api.streamrClient.unsubscribe(arr[0]);
            api.subscriptions.delete(subId);
        };

        handler.topics = Array.isArray(topics) ? topics : [topics];
        api.subscriptions.set(subId, handler);
        // await api.client.subscribe(topics, handler);
	const streamAddress = this._buildStreamAddress(handler.topics[0]);
	const heliosSubscription = await api.streamrClient.subscribe({
	    streamId: streamAddress,
	    resend: {
		last: 10,
	    },
	}, (message) => {
            console.error(message);
	    console.error("Topics: " + topics);
	    console.error("SubscriptionId " + subId);
	    api.event('pubsub:message', {
                networkId: "0xdeadbeef",
                subscriptionId: subId,
                topic: handler.topics[0],
                data: message,
            }, subId);
	    console.error("Message event generated");
	});
        return subId;
    }

    async unsubscribe(subId) {
        let handler = this.subscriptions.get(subId);
        if (!handler) {
            return false;
        }

        handler.close();
        return true;
    }

    // Direct connection and messaging parts
    async sendTo(heliosNetworkAddress, proto, data) {
        let target;
        let networkId = heliosNetworkAddress.networkId;
        let addrs = heliosNetworkAddress.networkAddress || [];
        let buf;

        if (Buffer.isBuffer(data)) {
            buf = data;
        } else if (data instanceof Uint8Array) {
            buf = Buffer.from(data);
        } else if (Array.isArray(data) || typeof data === "string") {
            buf = Buffer.from(data);
        }

        if (networkId) {
            // We should have resolved this recently
            target = PeerId.createFromB58String(networkId);
        } else if (addrs.length === 1) {
            target = new Multiaddr(addrs[0]);
        } else {
            throw `Only one address supported without network id, called with ${addrs.length}`;
        }

        try {
            await this.client.sendTo(target, proto, buf);
        } catch (e) {
            throw `Error sending message: ${e}`;
        }

        return true;
    }

    async addProtocolReceiver(proto, ctxId) {
	console.error("STREAMR/addProtocolReceiver: " + proto + " " + ctxId);
        let handler = async ({connection, stream, protocol}) => {
            let addr = this._formatNetworkAddress({
                id: connection.remotePeer,
                multiaddrs: [connection.remoteAddr],
            });
            let writeStream = null;

            try {
                let pipefd = await ospipe.create();
                writeStream = fs.createWriteStream("/dev/null", {
                    fd: pipefd.writable,
                    encoding: "binary",
                });

                let collect = async function* (source) {
                    for await (let chunk of source) {
                        let buf;

                        if (BufferList.isBufferList(chunk)) {
                            buf = chunk.slice();
                        } else if (Buffer.isBuffer(chunk)) {
                            buf = chunk;
                        } else if (chunk instanceof Uint8Array) {
                            buf = Buffer.from(chunk);
                        } else if (typeof chunk === 'string') {
                            buf = Buffer.from(chunk);
                        }

                        if (!writeStream.write(buf)) {
                            await events.once(writeStream, 'drain');
                        }

                        yield Buffer.from([0]);
                    }
                };

                // Unfortunate, that BufferList does not
                // correctly convert to an array of numbers on Array.from
                this.io.call('receive-message', [
                    addr,
                    protocol,
                    pipefd.readable,
                    ctxId,
                ]);

                await pipe(stream.source, collect, stream.sink);
            } catch (e) {
                console.error("Error while handling protocol stream:", e);
            }

            if (writeStream) {
                writeStream.end();
                let cleanup = node_stream.finished(writeStream, (err) => {
                    if (err) {
                        console.error("Error closing writable pipe stream", err);
                    }

                    cleanup();
                });
            }

            // FIXME: should we close or not? Currently not closing
            if (connection.close) {
                //connection.close().catch(e => null);
            }
        };
	// Disable this for a while 25.2.2022
        // this.client.handleService(proto, handler);
    }

    async removeProtocolReceiver(proto) {
        this.client.removeService(proto);
    }

    async resolve(egoId) {
        log("Resolving", egoId);
        try {
            for await (let peerData of this.client.findEgo(egoId)) {
                let addr = this._formatNetworkAddress(peerData, egoId);
                log("Resolved", addr);

                return addr;
            }
        } catch (e) {
            log("Error resolving:", e);
            throw e;
        }

        return {
            egoId: egoId,
        };
    }
}

module.exports.ApiController = ApiController;
