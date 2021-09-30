"use strict";

const NodeFdPass = process._linkedBinding("node_fd_pass");
const fs = require("fs");
const net = require("net");

let ioMap = NodeFdPass.getIoMap();
let input, output;

console.info("Node io map:", ioMap);

let fd = Number.parseInt(ioMap.inout.substr("fd://".length), 10)
let sock = new net.Socket({ fd });
let callId = 1;

let lastPingId = null;

sock.on("data", (data) => {
    console.info("Received in node: ", data)
    try {
        let obj = JSON.parse(data.toString());
        let reply = null;

        console.info("Node data parsed: ", obj);

        if (obj && obj.type === 'call' && obj.name === 'ping-me') {
            lastPingId = obj.id;
            // This "reply" is actually a call to ping on the other side
            reply = {
                type: 'call',
                id: 'call-' + String(callId++),
                name: 'ping',
                data: [
                    String(obj.data),
                ],
            };
        }

        if (obj && obj.type === 'return' && obj.name === 'ping') {
            // Reply to the original ping-me with a pong from the other side
            reply = {
                type: 'return',
                id: lastPingId,
                name: 'ping-me',
                data: obj.data,
            };
        }

        if (obj && obj.type === 'call' && obj.name === 'ping') {
            reply = {
                type: 'return',
                id: obj.id,
                name: obj.name,
                data: String(obj.data) + "-pong",
            };
        }

        if (reply) {
            console.info("Node send obj reply (plus \\n): ", JSON.stringify(reply));
            let buf = Buffer.from(JSON.stringify(reply) + "\n");

            sock.write(buf, (err) => {
                if (err) {
                    console.error("Error writing reply data: ", err);
                }
            });
        }
    } catch (e) {
        console.error(e);
    }
});