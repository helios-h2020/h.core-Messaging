'use strict';

const P2pNode = require('./p2p-node');
const PeerId = require('peer-id');
const CID = require('cids')
const multihashing = require('multihashing-async')
const pipe = require('it-pipe');
const { delay } = require('bluebird');
const { Multiaddr } = require('multiaddr');
const log = require("debug")("helios:p2p-client");
const defaultsDeep = require('@nodeutils/defaults-deep')
const { EventEmitter } = require('events');


const p2pBootstrapNodes = [
    //'/ip4/77.247.178.250/tcp/28118/ws/p2p/QmS4LDFAErdAHe2KBtpqtQ2eokP4mXf448XoZDtKH6r646',
    //'/ip4/77.247.178.250/tcp/28119/ws/p2p/QmWfU5D6bdmzKEpt6YxumaG9rZ5EGu7sbxiQYMuc5vkb3A',
    //'/ip4/127.0.0.1/tcp/28118/p2p/QmWfU5D6bdmzKEpt6YxumaG9rZ5EGu7sbxiQYMuc5vkb3A',
];

const p2pBootstrapCircuit = [
    //'/ip4/77.247.178.250/tcp/28118/ws/p2p/QmS4LDFAErdAHe2KBtpqtQ2eokP4mXf448XoZDtKH6r646',
    //'/ip4/77.247.178.250/tcp/28119/ws/p2p/QmWfU5D6bdmzKEpt6YxumaG9rZ5EGu7sbxiQYMuc5vkb3A',
    //'/ip4/127.0.0.1/tcp/28118/p2p/QmWfU5D6bdmzKEpt6YxumaG9rZ5EGu7sbxiQYMuc5vkb3A',
];


class HeP2pClient {
    constructor(options) {
        this.peerId = options.networkId;
        this.egoId = options.egoId;
        this.egos = new Set();
	this.swarmKeyProtocol = options.swarmKeyProtocol || null;
	this.swarmKeyEncoding = options.swarmKeyEncoding || null;
	this.swarmKeyData = options.swarmKeyData || null;

        this.listenAddrs = options.listenAddrs || [
            '/ip6/::/tcp/0',
            '/ip4/0.0.0.0/tcp/0',
            ...p2pBootstrapCircuit.map(ma => ma + "/p2p-circuit"),
        ];

        this.bootstrapAddrs = options.bootstrapAddrs || [
            ...p2pBootstrapNodes,
        ];

        this.starNodes = options.starNodes;

        this.node = null;
        this.events = new EventEmitter();
        this._peerId = null;
    }

    async start() {
        let peerId = await PeerId.createFromJSON(this.peerId);

        this._peerId = peerId;

        let node = new P2pNode({
            peerId: peerId,
            starNodes: this.starNodes,
            addresses: {
                listen: this.listenAddrs,
            },
            config: {
                peerDiscovery: {
                    bootstrap: {
                        enabled: this.bootstrapAddrs.length > 0,
                        list: this.bootstrapAddrs,
                    }
                }
            },
	    swarmKeyProtocol: this.swarmKeyProtocol,
	    swarmKeyEncoding: this.swarmKeyEncoding,
	    swarmKeyData: this.swarmKeyData,
        });

        node.handle('/helios-test/peer-status/0.0.1', this._handlePeerStatus.bind(this));
        let self = this;
        node.on("pubsub:messsage", function (...args) {
            self.events.emit("pubsub:message", ...args);
        });
        node.on("peer:discovery", function (...args) {
            self.events.emit("peer:discovery", ...args);
        });
        node.on("peer:connect", function (...args) {
            self.events.emit("peer:connect", ...args);
        });

        this.node = node;
        await this.node.start();
        log("Started node");
        this.node.startDiscovery();


        // Do not waitfor ego id providing
        this.addEgo(this.egoId).catch(e => {
            log(`Error providing ego id in startup: ${e}`);
        });
    }

    async stop() {
        await this.node.stopDiscovery();
        await this.node.stop()
    }

    // PubSub part
    async publish(topic, message) {
        await this.node.pubsub.publish(topic, message);
    }

    async subscribe(topics, handler) {
        await this.node.pubsub.subscribe(topics, handler);
    }

    async unsubscribe(topics) {
        await this.node.pubsub.unsubscribe(topics);
    }


    // Only return local known addresses
    getPeerAddr(peer) {
        return this.node.peerStore.addressBook.getMultiaddrsForPeer(peer);
    }

    findPeer(peer, options) {
        if (!PeerId.isPeerId(peer)) {
            peer = PeerId.createFromB58String(peer);
        }

        if (options && options.localOnly) {
            return Promise.resolve({
                id: peer,
                multiaddrs: this.getPeerAddr(peer),
            });
        }

        return this.node.peerRouting.findPeer(peer, options);
    }

    // Service and protocol provisioning
    async provideService(serviceDescriptor) {
        let dhtHash = await multihashing(Buffer.from(serviceDescriptor), 'sha2-256');
        // let pAll = [];

        for (let i = 5; i > 0; i--) {
            let cid = new CID(dhtHash);
            this.node.contentRouting.provide(cid).then(() => {
                // log("Provided", serviceDescriptor, cid);
                return cid;
            } ).catch((err) => log("Providing ignored or error", serviceDescriptor, cid, err))
            // log("Providing", serviceDescriptor, cid);
            dhtHash = await multihashing(dhtHash, 'sha2-256');
        }
        // await Promise.race(pAll);
        log("Warning: Not provided", serviceDescriptor);
    }
    /*
    // Service and protocol provisioning
    async provideService(serviceDescriptor) {
        let dhtHash = await multihashing(Buffer.from(serviceDescriptor), 'sha2-256');
        let pAll = [];

        for (let i = 5; i > 0; i--) {
            let cid = new CID(dhtHash);

            pAll.push(
                this.node.contentRouting.provide(cid).then(() => {
                    log("Provided", serviceDescriptor, cid);
                    return cid;
                }).catch((err) => log("Error providing/ignore", serviceDescriptor, cid, err))
            );

            log("Providing", serviceDescriptor, cid);
            dhtHash = await multihashing(dhtHash, 'sha2-256');
        }

        await Promise.race(pAll);
        log("Provided one", serviceDescriptor);
    }
    */
    handleService(serviceDescriptor, handler) {
        this.node.handle(serviceDescriptor, handler);
        return () => {
            this.removeService(serviceDescriptor);
        };
    }

    removeService(serviceDescriptor) {
        this.node.unhandle(serviceDescriptor);
    }

    async *findEgo(egoid) {
        yield *this.findService(`/helios/ego/peer/${egoid}`, {
            includeSelf: true,
            maxNumProviders: 1,
            maxServiceNum: 1,
        });
    }

    async *findService(serviceDescriptor, options = {}) {
        options = defaultsDeep(options, {
            stopOnSuccess: true,
            maxServiceNum: 120,
            includeSelf: false,
            timeout: 2000,
            retryTimeout: 60000,
        });

        let dhtHash = await multihashing(Buffer.from(serviceDescriptor), 'sha2-256');
        let includeSelf = options.includeSelf || false;
        let maxServiceNum = options.maxServiceNum || 120;
        let stopOnSuccess = options.stopOnSuccess === null || options.stopOnSuccess === undefined || !!options.stopOnSuccess;

        for (let i = 5; i > 0 && maxServiceNum > 0; i--) {
            let cid = new CID(dhtHash);
            // log("Query service", serviceDescriptor, cid);
            try {
                for await (let peerData of this.node.contentRouting.findProviders(cid, options)) {
                    if (!includeSelf && this._peerId.equals(peerData.id)) {
                        // skip my self
                        continue;
                    }

                    // log("Found service", serviceDescriptor, cid, peerData);
                    if (!peerData.multiaddrs || peerData.multiaddrs.length == 0) {
                        peerData.multiaddrs = this.getPeerAddr(peerData.id) || [];
                    }

                    yield peerData;

                    if (--maxServiceNum <= 0) {
                        return;
                    }
                }

                if (stopOnSuccess) {
                    return;
                }

                options.timeout = options.retryTimeout;
            } catch (e) {
                log("Error resolving", serviceDescriptor, cid, i, e);
            }

            dhtHash = await multihashing(dhtHash, 'sha2-256')
        }
    }

    async addEgo(egoId) {
        if (this.egos.has(egoId)) {
            return;
        }

        this.egos.add(egoId);
        return this.provideService(`/helios/ego/peer/${egoId}`).then((rv) => {
            this.events.emit("ego:announce", egoId);
            return rv;
        });
    }

    async createNetworkId(egoid, type) {
        return this.peerId;
    }

    async connectTo(peer, serviceDescriptor) {
        if (peer instanceof PeerId || peer instanceof Multiaddr || typeof peer === "string") {
            peer = peer;
        } else if (peer != null && peer.id && peer.multiaddrs && peer.multiaddrs.length > 0) {
            // verify that the peerstore has all the addresses we want to dial
            let id = typeof peer.id === "string" ? PeerId.createFromB58String(peer.id) : peer.id;
            this.node.peerStore.addressBook.add(id, peer.multiaddrs);
            peer = id;
        } else if (peer != null && peer.id) {
            peer = peer.id
        } else {
            throw new Error("Expect argument t be string, Multiaddr or PeerId");
        }

        let conn = this.node.dialProtocol(peer, serviceDescriptor);

        return conn;
    }

    async connectToEgo(egoid, serviceDescriptor) {
        for await (let peerData of this.findEgo(egoid)) {
            try {
                return await this.connectTo(peerData, serviceDescriptor);
            } catch (e) {
                console.info("Error connecting:", e);
            }
        }
    }

    async sendTo(ma, proto, message) {
        let { stream } = await this.node.dialProtocol(ma, proto);

        let chunkSize = 100 * 1024;
        let messageIt = async function*(source) {
            for (let i = 0; i < message.length; i += chunkSize) {
                yield message.slice(i, i + chunkSize);

                // Rudimentary flow control
                await source.next();
            }
        };

        // closes the stream after input
        let rv = await pipe(stream.source, messageIt, stream.sink);
        try {
            await stream.close();
        } catch (e) {
            // Ignore error
        }
        return rv;
    }

    async sendToEgo(egoid) {}

    // Status and monitoring
    async currentPeers() {}
    async currentNetworkId() {}

    _handlePeerStatus(peer) {
    }
}

module.exports.HeP2pClient = HeP2pClient;
